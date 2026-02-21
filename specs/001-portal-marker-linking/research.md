# Research: Portal Marker Linking Implementation

**Date**: 2026-02-21
**Feature**: Portal Marker Linking Between Map Layers
**Branch**: 001-portal-marker-linking

---

## 1. Existing Teleportation Detection Mechanisms

### Current Implementation

**File**: `src/nurgling/navigation/UnifiedTilePathfinder.java`

The existing pathfinder already handles portal traversal:

```java
// Portal connections in pathfinding
for (ChunkPortal portal : chunk.portals) {
    if (portal.localCoord != null && portal.connectsToGridId != -1) {
        // Validate portal type vs target layer
        if (!isPortalTargetLayerValid(portal.type, destChunk.layer)) {
            // Skip invalid portal transitions
        }
        ChunkNavData destChunk = graph.getChunk(portal.connectsToGridId);
        Coord exitCoord = findPortalExitCoord(destChunk, portal, tile.chunkId);
    }
}
```

**Key Findings**:

1. **Portal Detection**: Portals are already classified by type (`ChunkPortal.PortalType`):
   - `DOOR`, `GATE`, `STAIRS_UP`, `STAIRS_DOWN`, `CELLAR`
   - `MINE_ENTRANCE` (legacy), `MINEHOLE`, `LADDER`

2. **Portal Classification** (`ChunkPortal.classifyPortal()`):
   - Uses gob name patterns (e.g., "minehole" → `MINEHOLE`, "ladder" → `LADDER`)
   - Building exteriors treated as doors
   - Gates explicitly NOT treated as portals (walkability handles them)

3. **Layer Validation**: `isPortalTargetLayerValid()` already exists to validate portal transitions

4. **Exit Coordinate Recording**: `exitLocalCoord` is already recorded on portal traversal

### Teleportation Type Discrimination

**Current State**: The pathfinder does NOT currently distinguish between:
- Portal-based layer transitions
- Hearthfire teleportation
- Village totem teleportation
- Signpost fast travel

**Research Decision**: Need to implement teleportation event detection at game client level.

**Recommended Approach**:
- Hook into `GameUI` or `MapView` teleportation callbacks
- Check if teleportation source is a `ChunkPortal` with `connectsToGridId != -1`
- If yes → layer transition; If no → other teleportation

---

## 2. Map Marker System Architecture

### Current Marker Implementation

**Files Analyzed**:
- `src/haven/MapWnd.java` - Base map window
- `src/nurgling/widgets/NMapWnd.java` - Nurgling extensions
- `src/nurgling/overlays/NCropMarker.java` - Example marker implementation
- `src/haven/MiniMap.java` - Minimap widget

### Marker Structure

Current markers in Nurgling are render overlays:

```java
public class NCropMarker extends Sprite implements RenderTree.Node, PView.Render2D {
    // Renders marker sprite at coordinate
    // No persistent data structure for marker metadata
}
```

**Key Findings**:

1. **Marker Storage**: Markers are stored in `MapWnd` as overlay objects
2. **Marker Search**: `NMapWnd` has `markerSearchField` for filtering markers
3. **Marker List**: Markers appear in a list that can be sorted alphabetically
4. **No Link Metadata**: Current markers do NOT have:
   - Link UID field
   - Direction marker (IN/OUT)
   - Cross-layer association

### Extension Points

**Recommended Approach**:

1. Create `PortalMarker` wrapper class that extends existing marker pattern
2. Add link metadata as fields:
   ```java
   public class PortalMarker {
       public String linkUid;        // 6-char unique ID
       public Direction direction;   // IN or OUT
       public int sourceLayer;       // Layer where marker was created
       public int targetLayer;       // Connected layer
   }
   ```
3. Modify marker list sorting to group by `linkUid`

---

## 3. Coordinate System and Layer Management

### Current Coordinate Handling

**File**: `src/nurgling/navigation/ChunkPortal.java`

```java
public class ChunkPortal {
    public Coord localCoord;       // Position within chunk (tile coordinates)
    public long connectsToGridId;  // Grid ID on the other side
    public Coord exitLocalCoord;   // Where you appear in destination chunk
}
```

**Key Findings**:

1. **Local Coordinates**: Each chunk has 0-99 tile coordinate system
2. **Grid ID**: Each chunk has unique `gridId` for identification
3. **Exit Recording**: `exitLocalCoord` already records where player appears after traversal
4. **Layer Info**: `ChunkNavData` has `layer` field (0 = surface, 1+ = underground)

### Coordinate Transformation

**Current State**: No explicit coordinate transformation between layers exists.

**Research Finding**: User description states:
> "Each map has its own coordinate system. Relative portal positions are identical on vertically stacked maps."

**Recommended Approach**:

1. Use transition portal as zero reference point:
   ```java
   // Source layer offset
   Coord sourceOffset = markerCoord.sub(transitionPortalCoord);
   
   // Target layer coordinate
   Coord targetCoord = targetPortalCoord.add(sourceOffset);
   ```

2. Preserve relative positions using vector arithmetic

---

## 4. Marker Persistence Best Practices

### Current Save Format

**File**: `src/haven/MapFile.java` (assumed based on `MapWnd` constructor)

Map data is persisted via `MapFile` which stores:
- Chunk data (walkability, portals)
- Player-discovered information
- Custom markers (if any)

### Backward-Compatible Extension Pattern

**Research Decision**: Use JSON-based extension for link data:

```java
public class PortalLinkSaveData {
    public String linkUid;
    public long sourceGridId;
    public Coord sourcePortalCoord;
    public long targetGridId;
    public Coord targetPortalCoord;
    public String direction;  // "IN" or "OUT"
    public long createdTimestamp;
}
```

**Migration Strategy**:

1. On save file load:
   - Check for existing `portalLinks` array
   - If absent → initialize empty array (backward compatible)
   
2. On marker load:
   - Check if marker has `linkUid` field
   - If absent → treat as unlinked marker (legacy support)

---

## 5. UI Integration for Linked Markers

### Current Marker List UI

**File**: `src/haven/MapWnd.java` line 272+

```java
private class View extends NMiniMap implements CursorQuery.Handler {
    // Marker list rendering
    // Marker search and filtering
}
```

**Current Features**:
- Alphabetical sorting of markers
- Search field (`markerSearchField`) for filtering
- Click to center map on marker

### Required Extensions

**For Linked Markers**:

1. **Sorting Enhancement**:
   - Primary sort: alphabetically by name
   - Secondary sort: by `linkUid` (groups linked markers)

2. **Visual Indicators**:
   - Add IN/OUT icon overlay on marker
   - Optional: connecting line between linked markers in list

3. **Double-Click Action** (future enhancement):
   - Double-click linked marker → auto-switch to connected layer

---

## Phase 0 Decisions Summary

### Decision 1: Teleportation Detection

**Decision**: Hook into existing `UnifiedTilePathfinder` portal traversal logic

**Rationale**: 
- Portal classification already exists
- `exitLocalCoord` already recorded
- Minimal new code required

**Alternatives Considered**:
- Global teleportation event listener → More invasive, requires game core changes
- Player position monitoring → Unreliable, race conditions

---

### Decision 2: Marker Data Structure

**Decision**: Create `PortalMarker` wrapper with link metadata

**Rationale**:
- Non-breaking extension of existing marker system
- Clear separation between linked and unlinked markers
- Easy to serialize/deserialize

**Alternatives Considered**:
- Modify base `MapWnd` marker class → Too invasive
- Use marker name encoding (e.g., "Cave_abc123_OUT") → Fragile, hard to maintain

---

### Decision 3: Coordinate Transformation

**Decision**: Use transition portal as zero reference point

**Rationale**:
- Matches user description exactly
- Simple vector arithmetic
- Preserves relative positions automatically

**Formula**:
```
targetCoord = targetPortalCoord + (sourceMarkerCoord - sourcePortalCoord)
```

---

### Decision 4: Persistence Format

**Decision**: JSON array in map save file

**Rationale**:
- Consistent with existing `ChunkPortal.toJson()` pattern
- Human-readable for debugging
- Easy to extend

**Schema**:
```json
{
  "portalLinks": [
    {
      "linkUid": "abc123",
      "markers": [
        {"gridId": 12345, "coord": [50, 60], "direction": "OUT", "layer": 0},
        {"gridId": 67890, "coord": [45, 55], "direction": "IN", "layer": 1}
      ]
    }
  ]
}
```

---

## Next Steps: Phase 1 Design

1. **data-model.md**: Define `PortalMarker`, `PortalLink`, `PortalLinkManager` classes
2. **contracts/portal-linking-api.md**: Define APIs for link creation, detection, persistence
3. **quickstart.md**: Developer guide for testing and debugging

---

## Open Questions for Phase 1

1. **Cave Detection**: How to distinguish natural cave portals from other portal types?
   - Current `ChunkPortal` does not have `CAVE` type
   - May need to add cave classification based on gob name patterns

2. **Layer Determination**: Game does not provide direct "current layer" API
   - Will use transition direction logic:
     - Entering cave/minehole = descent (layer + 1)
     - Exiting/ladder = ascent (layer - 1)

3. **Multi-level Portals**: `mineshaft` type mentioned but not in current codebase
   - Will design for extensibility but implement only single-level portals first
