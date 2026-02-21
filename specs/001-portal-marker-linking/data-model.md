# Data Model: Portal Marker Linking

**Date**: 2026-02-21
**Feature**: Portal Marker Linking Between Map Layers
**Branch**: 001-portal-marker-linking

---

## Core Entities

### 1. PortalMarker

**Purpose**: Represents a portal marker on the map with linking metadata.

**Fields**:
- `name: String` - Display name (e.g., "Cave", "Hole")
- `type: PortalType` - Type of portal (CAVE, MINEHOLE, LADDER)
- `gridId: long` - Grid/chunk ID where marker is located
- `coord: Coord` - Marker coordinate in local chunk space (0-99)
- `linkUid: String` - 6-character unique identifier linking related markers (nullable for unlinked markers)
- `direction: Direction` - IN or OUT marker (nullable for unlinked markers)
- `layer: int` - Map layer number (0 = surface, 1+ = underground)
- `createdTimestamp: long` - Unix timestamp when marker was created
- `icon: Resource.Image` - Visual icon for the marker

**Validation Rules**:
- `linkUid` MUST be 6 characters, alphanumeric (0-9a-zA-Z)
- `direction` MUST be IN or OUT when `linkUid` is present
- `coord` MUST be within chunk bounds (0-99 for both x and y)
- `name` MUST be non-empty

**State Transitions**:
```
Unlinked → Linked (on layer transition detection)
Linked → Unlinked (on player deletion)
Linked → Linked (on marker recreation with same UID)
```

---

### 2. PortalLink

**Purpose**: Represents a link between two portal markers on different layers.

**Fields**:
- `linkUid: String` - Unique 6-character identifier (primary key)
- `sourceMarker: PortalMarker` - Marker on source layer (OUT direction)
- `targetMarker: PortalMarker` - Marker on target layer (IN direction)
- `sourceLayer: int` - Source layer number
- `targetLayer: int` - Target layer number
- `createdTimestamp: long` - When link was created
- `lastAccessedTimestamp: long` - When link was last used for switching

**Validation Rules**:
- `sourceMarker.layer` MUST equal `sourceLayer`
- `targetMarker.layer` MUST equal `targetLayer`
- `sourceLayer` and `targetLayer` MUST differ by exactly 1 (adjacent levels)
- Both markers MUST share the same `linkUid`
- `sourceMarker.direction` MUST be OUT
- `targetMarker.direction` MUST be IN

**Relationships**:
- One `PortalLink` connects exactly two `PortalMarker` instances
- `PortalMarker` can belong to at most one `PortalLink`

---

### 3. PortalLinkManager

**Purpose**: Manages creation, persistence, and retrieval of portal links.

**Responsibilities**:
- Detect layer transitions via portal traversal
- Create new `PortalLink` instances
- Persist links to save file
- Recreate markers on portal revisit after deletion
- Provide API for querying linked markers

**Key Methods**:
```java
// Create a new link between source and target portals
PortalLink createLink(PortalMarker sourceMarker, PortalMarker targetMarker)

// Get all markers linked to a specific marker
List<PortalMarker> getLinkedMarkers(PortalMarker marker)

// Get link by UID
PortalLink getLinkByUid(String linkUid)

// Save all links to file
void saveLinks(File saveFile)

// Load all links from file
void loadLinks(File saveFile)

// Recreate deleted marker with preserved UID
void recreateMarker(PortalMarker originalMarker)
```

---

### 4. PortalLinkSaveData

**Purpose**: DTO for persisting portal links to save file.

**Fields**:
```json
{
  "linkUid": "abc123",
  "sourceLayer": 0,
  "sourceGridId": 12345,
  "sourceCoordX": 50,
  "sourceCoordY": 60,
  "sourceDirection": "OUT",
  "targetLayer": 1,
  "targetGridId": 67890,
  "targetCoordX": 45,
  "targetCoordY": 55,
  "targetDirection": "IN",
  "createdTimestamp": 1708500000000,
  "lastAccessedTimestamp": 1708500100000
}
```

**Serialization**:
- Format: JSON array in map save file
- Key: `"portalLinks"`
- Backward compatible: if key absent → initialize empty array

---

### 5. TeleportationEvent

**Purpose**: Represents a teleportation event detected by the system.

**Fields**:
- `type: TeleportationType` - Type of teleportation
- `sourceGridId: long` - Grid ID before teleportation
- `sourceCoord: Coord` - Coordinate before teleportation
- `targetGridId: long` - Grid ID after teleportation
- `targetCoord: Coord` - Coordinate after teleportation
- `timestamp: long` - When teleportation occurred
- `portalGobName: String` - Gob name if portal teleportation (nullable)

**TeleportationType Enum**:
- `PORTAL_LAYER_TRANSITION` - Portal connecting different layers (cave, minehole, ladder)
- `PORTAL_SAME_LAYER` - Portal within same layer (door, stairs within building)
- `HEARTHFIRE` - Teleportation to hearthfire bone
- `VILLAGE_TOTEM` - Teleportation to village totem
- `SIGNPOST` - Fast travel via signpost
- `UNKNOWN` - Unrecognized teleportation

---

### 6. CoordinateTransformer

**Purpose**: Transforms coordinates between layers using portal as reference point.

**Key Methods**:
```java
// Transform marker coordinate from source layer to target layer
Coord transformCoordinate(
    Coord sourceMarkerCoord,
    Coord sourcePortalCoord,
    Coord targetPortalCoord
)

// Calculate offset vector
Coord calculateOffset(Coord markerCoord, Coord portalCoord)

// Apply offset to target portal
Coord applyOffset(Coord targetPortalCoord, Coord offset)
```

**Transformation Formula**:
```
offset = sourceMarkerCoord - sourcePortalCoord
targetCoord = targetPortalCoord + offset
```

**Example**:
```
Surface (Layer 0):
  Cave1 Portal: (50, 60)
  Cave2 Portal: (50, 80)
  Offset Cave2 from Cave1: (0, 20)

Underground (Layer 1):
  Cave1 Portal: (45, 55)  // Different absolute coords
  Cave2 Portal: (45, 75)  // Same relative offset (0, 20) ✓
```

---

## Entity Relationship Diagram

```
┌─────────────────┐         ┌─────────────────┐
│  PortalMarker   │         │   PortalLink    │
├─────────────────┤         ├─────────────────┤
│ linkUid (FK)    │◄────────┤ linkUid (PK)    │
│ direction       │         │ sourceMarker    │
│ name            │         │ targetMarker    │
│ type            │         │ sourceLayer     │
│ gridId          │         │ targetLayer     │
│ coord           │         └─────────────────┘
│ layer           │                  ▲
└─────────────────┘                  │
        ▲                            │
        │                            │
        │         ┌──────────────────┘
        │         │
        │  ┌──────┴──────┐
        └──┤PortalLink   │
           │Manager      │
           └─────────────┘
```

---

## Validation Rules Summary

### Link UID Generation
- MUST be exactly 6 characters
- Character set: 0-9, a-z, A-Z (alphanumeric)
- MUST be unique across all links in save file
- Generation algorithm: Random with collision check

### Direction Markers
- OUT marker: Created on source layer (where player enters portal)
- IN marker: Created on target layer (where player exits portal)
- Cave portals: Same icon for both IN and OUT
- Minehole/Ladder: Different icons (minehole for IN, ladder for OUT)

### Layer Adjacency
- Cave portals: Connect only layers 0 ↔ 1
- Minehole/Ladder portals: Connect layers 1 ↔ 2, 2 ↔ 3, etc.
- MUST validate that `|sourceLayer - targetLayer| == 1`

### Marker Naming
- Cave portals: Name = "Cave"
- Minehole/Ladder portals: Name = "Hole"
- Linked markers MUST share the same name within a link

---

## State Machine: PortalLink Lifecycle

```
[Initial State]
      │
      ▼
[Portal Detected] ──(player enters portal)──► [Teleportation Detected]
                                                    │
                                                    ▼
                                            [Layer Transition?]
                                               │        │
                                          Yes  │        │ No
                                               │        │
                                               ▼        └──► [Ignore]
                                        [Create Link]
                                               │
                                               ▼
                                        [Persist to Save]
                                               │
                                               ▼
                                        [Linked State] ◄──┐
                                               │           │
                                               │           │ (revisit)
                                    (player deletes marker)│
                                               │           │
                                               ▼           │
                                        [Marker Missing] ──┘
```

---

## Indexes and Query Optimization

### Required Indexes

1. **By Link UID**: `HashMap<String, PortalLink>` for O(1) lookup
2. **By Grid ID + Coord**: `HashMap<Long, HashMap<Coord, PortalMarker>>` for spatial queries
3. **By Layer**: `HashMap<Integer, List<PortalMarker>>` for layer-filtered queries

### Query Patterns

```java
// Find marker by location
PortalMarker getMarker(long gridId, Coord coord)

// Find all linked markers for a given marker
List<PortalMarker> getLinkedMarkers(PortalMarker marker)

// Find all markers on a specific layer
List<PortalMarker> getMarkersByLayer(int layer)

// Find nearest portal to player
PortalMarker getNearestPortal(Coord playerCoord, int layer)
```

---

## Migration Plan

### Version 1.0 (Initial Release)

**Schema**:
```json
{
  "version": "1.0",
  "portalLinks": []
}
```

**Migration Steps**:
1. On save file load:
   - Check for `"portalLinks"` key
   - If absent → initialize empty array
   - If present → load existing links

2. On marker load:
   - Check for `linkUid` field
   - If absent → treat as unlinked marker
   - If present → associate with existing link

### Backward Compatibility

- Existing markers without link data continue to work
- New fields are optional (nullable)
- Save file format is additive (no breaking changes)
