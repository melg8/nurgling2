# Data Model: Cave Marker Linking

**Feature**: 001-fix-cave-marker-links  
**Date**: 2026-02-21  
**Related**: 001-portal-marker-linking

---

## Core Entities

### 1. Cave Marker (PMarker)

**Description**: A map marker representing a cave entrance (level 0) or cave exit (level 1).

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `tc` | Coord | Tile coordinates on the map |
| `linkUid` | String (6 chars) | Unique identifier shared by linked marker pair |
| `direction` | Direction enum | IN (arriving) or OUT (departing) |
| `icon` | GobIcon | Icon resource (e.g., "gfx/hud/mmap/cave") |
| `name` | String | Display name ("Cave") - derived from icon |
| `z` | int | Z-order for sorting |

**Relationships**:
- Each Cave Marker has exactly ONE linked partner (entrance ↔ exit pair)
- Linked pair shares identical `linkUid` and `name`
- Markers exist on different map layers (level 0 ↔ level 1)

**Validation Rules**:
- `linkUid` MUST be exactly 6 characters from charset `0-9a-zA-Z`
- `direction` MUST be either IN or OUT
- `name` MUST be "Cave" for cave portal markers

---

### 2. Linked Marker Pair

**Description**: Two Cave Markers (entrance + exit) connected by shared UID.

**Composition**:
- **Entrance Marker**: Level 0 (outdoor), direction = OUT
- **Exit Marker**: Level 1 (indoor), direction = IN

**Properties**:
| Property | Value |
|----------|-------|
| Shared `linkUid` | 6-character identifier |
| Shared `name` | "Cave" |
| Marker count | Exactly 2 |
| Layer relationship | Level 0 ↔ Level 1 |

**Validation Rules**:
- Both markers MUST have identical `linkUid`
- Both markers MUST have identical `name`
- Markers MUST be on different layers
- Directions MUST be opposite (IN ↔ OUT)

---

### 3. Map Marker List

**Description**: UI component displaying all active map markers in sorted order.

**Sort Order** (composite key):
1. **Primary**: `name` (alphabetical)
2. **Secondary**: `linkUid` (alphabetical)
3. **Tertiary**: `z` (z-order, ascending)

**Sorting Logic**:
```java
Comparator<DisplayIcon> comparator = (a, b) -> {
    // Compare by name first
    int nameCmp = a.attr.icon().res.name.compareTo(b.attr.icon().res.name);
    if (nameCmp != 0) return nameCmp;
    
    // Then by linkUid (for PMarker instances)
    String uidA = getLinkUid(a);
    String uidB = getLinkUid(b);
    if (uidA != null && uidB != null) {
        int uidCmp = uidA.compareTo(uidB);
        if (uidCmp != 0) return uidCmp;
    }
    
    // Finally by z-order
    return a.z - b.z;
};
```

**Expected Behavior**:
- All "Cave" markers appear together (grouped by name)
- Within "Cave" group, linked pairs appear adjacent (grouped by linkUid)
- Within each pair, order determined by z-order

---

### 4. DisplayIcon

**Description**: Wrapper class for displayable map icons.

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `attr` | GobIcon | The icon resource |
| `conf` | GobIcon.Setting | Icon configuration (show, getmarkablep, getmarkp) |
| `z` | int | Z-order for sorting |
| `sc` | Coord | Screen coordinates |

**Relationships**:
- Wraps a `GobIcon` (game object icon)
- Contains configuration from `GobIcon.Setting`
- Used in `MiniMap.icons` collection

---

### 5. Grid Data (DisplayGrid)

**Description**: Spatial grid containing markers for a map region.

**Structure**:
```java
class DisplayGrid {
    Collection<DisplayMarker> markers(boolean includeHidden);
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `markers` | Collection<DisplayMarker> | Markers in this grid cell |

**Usage in Rendering**:
```java
MiniMap.DisplayGrid[] display = map.getDisplay();
Area dgext = map.getDgext();

for (Coord gc : dgext) {
    DisplayGrid disp = display[dgext.ri(gc)];
    for (DisplayMarker mark : disp.markers(false)) {
        // Process marker
    }
}
```

---

## State Transitions

### Marker Lifecycle

```
[Not Discovered] 
       ↓ (player enters portal visibility range)
[Discovered - No UID]
       ↓ (player transitions through portal)
[Linked - UID Assigned]
       ↓ (player deletes marker)
[Deleted]
       ↓ (player revisits portal)
[Linked - UID Recreated] (same UID preserved)
```

### Link Creation Flow

```
1. Player at cave entrance (level 0)
       ↓
2. System detects portal, creates OUT marker (no UID yet)
       ↓
3. Player enters portal, transitions to level 1
       ↓
4. System detects layer transition, generates 6-char UID
       ↓
5. System creates IN marker on level 1 with UID
       ↓
6. System assigns same UID to OUT marker on level 0
       ↓
7. Linked pair complete (both markers share UID)
```

---

## Validation Rules

### Rule 1: UID Format
```
linkUid MUST match regex: ^[0-9a-zA-Z]{6}$
```

### Rule 2: Marker Pairing
```
For every PMarker with linkUid:
  - Exactly ONE other PMarker MUST exist with same linkUid
  - The other marker MUST have opposite direction
  - The other marker MUST be on different map layer
```

### Rule 3: Adjacency
```
When marker list is sorted:
  - Markers with same name appear consecutively
  - Markers with same name+linkUid appear consecutively
  - Linked pairs are visually adjacent in UI
```

### Rule 4: Log Output
```
Log messages MUST NOT contain:
  - Object hash codes (pattern: [Ljava.lang.Object;@...)
  - Raw array toString() output
  
Log messages MUST:
  - Use Arrays.toString() for arrays
  - Include contextual data (marker count, UID, direction)
  - Be human-readable at ERROR level
```

---

## Data Persistence

### Cross-Session Persistence
- Markers are persisted in game save file
- `linkUid` is saved with marker data
- On reload, markers restore with original UIDs intact

### Marker Recreation
- If player deletes a linked marker, the UID is preserved
- On portal revisit, system recreates marker with original UID
- Link is automatically restored

---

## Relationships to 001-portal-marker-linking

This data model extends the original portal marker linking feature:

| Entity | Original (001-portal-marker-linking) | Current (001-fix-cave-marker-links) |
|--------|-------------------------------------|-------------------------------------|
| PMarker | Created with UID on layer transition | Same + adjacency in sorted list |
| linkUid | 6-char identifier for linking | Same + used as sort key |
| direction | IN/OUT indicator | Same + used for rendering |
| Marker List | Not specified | **NEW**: Composite sort key (name+UID) |
| Logging | DEBUG-heavy | **NEW**: State-based, reduced spam |

---

## Entity Diagram

```
┌─────────────────┐         ┌─────────────────┐
│  Cave Marker    │         │  Cave Marker    │
│  (Entrance)     │◄───────►│  (Exit)         │
├─────────────────┤  linkUid├─────────────────┤
│ tc: Coord       │         │ tc: Coord       │
│ linkUid: String │         │ linkUid: String │
│ direction: OUT  │         │ direction: IN   │
│ name: "Cave"    │         │ name: "Cave"    │
│ layer: 0        │         │ layer: 1        │
└─────────────────┘         └─────────────────┘
         │                           │
         │                           │
         ▼                           ▼
┌─────────────────────────────────────────────────┐
│           Map Marker List (Sorted)              │
├─────────────────────────────────────────────────┤
│  1. Cave [UID=abc123] OUT  ← Adjacent Pair     │
│  2. Cave [UID=abc123] IN   ←                   │
│  3. Cave [UID=xyz789] OUT  ← Adjacent Pair     │
│  4. Cave [UID=xyz789] IN   ←                   │
│  5. Cave [UID=def456] OUT  ← Adjacent Pair     │
│  6. Cave [UID=def456] IN   ←                   │
│  7. Other Marker...                            │
└─────────────────────────────────────────────────┘
```
