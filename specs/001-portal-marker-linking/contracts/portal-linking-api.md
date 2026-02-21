# API Contracts: Portal Marker Linking

**Date**: 2026-02-21
**Feature**: Portal Marker Linking Between Map Layers
**Branch**: 001-portal-marker-linking

---

## 1. TeleportationDetector

**Purpose**: Detect and classify teleportation events.

### Interface

```java
package nurgling.teleportation;

import haven.Coord;

public interface TeleportationDetector {
    
    /**
     * Register a teleportation event callback.
     * Called when player position changes discontinuously.
     */
    void onTeleportation(TeleportationCallback callback);
    
    /**
     * Get the last detected teleportation event.
     * @return Last teleportation event or null if none detected
     */
    TeleportationEvent getLastEvent();
    
    /**
     * Clear the last event (call after processing).
     */
    void clearEvent();
}

@FunctionalInterface
interface TeleportationCallback {
    void onTeleport(TeleportationEvent event);
}
```

### Implementation Contract

**Input**: Player position updates from game client

**Processing**:
1. Detect discontinuous position change (> 10 tiles)
2. Classify teleportation type based on context:
   - If near portal gob → `PORTAL_LAYER_TRANSITION` or `PORTAL_SAME_LAYER`
   - If hearthfire UI interaction → `HEARTHFIRE`
   - If village totem UI → `VILLAGE_TOTEM`
   - If signpost UI → `SIGNPOST`
3. Create `TeleportationEvent` with source/target coordinates

**Output**: `TeleportationEvent` passed to callback

**Error Handling**:
- If classification ambiguous → `UNKNOWN` type
- If event processing fails → log at WARN level, do not block game

---

## 2. LayerTransitionDetector

**Purpose**: Distinguish layer transitions from other teleportation.

### Interface

```java
package nurgling.teleportation;

public interface LayerTransitionDetector {
    
    /**
     * Determine if a teleportation event is a layer transition.
     * @param event The teleportation event
     * @return true if event represents layer transition
     */
    boolean isLayerTransition(TeleportationEvent event);
    
    /**
     * Get the direction of layer transition.
     * @param event The teleportation event
     * @return +1 for descent (surface→underground), -1 for ascent
     */
    int getTransitionDirection(TeleportationEvent event);
    
    /**
     * Get the portal type used for transition.
     * @param event The teleportation event
     * @return Portal type (CAVE, MINEHOLE, LADDER) or null
     */
    ChunkPortal.PortalType getPortalType(TeleportationEvent event);
}
```

### Implementation Contract

**Decision Logic**:

```java
boolean isLayerTransition(TeleportationEvent event) {
    // Rule 1: Must be portal teleportation
    if (event.type != PORTAL_LAYER_TRANSITION) {
        return false;
    }
    
    // Rule 2: Portal must connect to different grid
    if (event.sourceGridId == event.targetGridId) {
        return false;
    }
    
    // Rule 3: Portal type must be layer-connecting type
    PortalType type = classifyPortal(event.portalGobName);
    return type == CAVE || type == MINEHOLE || type == LADDER;
}
```

**Transition Direction**:
```java
int getTransitionDirection(TeleportationEvent event) {
    PortalType type = getPortalType(event);
    switch (type) {
        case CAVE:
        case MINEHOLE:
            return +1;  // Descent (entering underground)
        case LADDER:
            return -1;  // Ascent (exiting to surface/higher level)
        default:
            return 0;   // Unknown
    }
}
```

---

## 3. PortalLinkManager

**Purpose**: Manage lifecycle of portal links.

### Interface

```java
package nurgling.markers;

import haven.Coord;
import java.util.List;
import java.util.Optional;

public interface PortalLinkManager {
    
    /**
     * Create a new link between two portal markers.
     * @param sourceMarker Marker on source layer (will be OUT)
     * @param targetMarker Marker on target layer (will be IN)
     * @return Created PortalLink
     * @throws IllegalArgumentException if markers on same layer
     */
    PortalLink createLink(PortalMarker sourceMarker, PortalMarker targetMarker);
    
    /**
     * Get all markers linked to a specific marker.
     * @param marker The marker to find links for
     * @return List of linked markers (empty if marker not linked)
     */
    List<PortalMarker> getLinkedMarkers(PortalMarker marker);
    
    /**
     * Get link by unique identifier.
     * @param linkUid 6-character unique ID
     * @return Optional containing link if found
     */
    Optional<PortalLink> getLinkByUid(String linkUid);
    
    /**
     * Save all links to file.
     * @param saveFile Path to save file
     */
    void saveLinks(java.io.File saveFile) throws IOException;
    
    /**
     * Load all links from file.
     * @param saveFile Path to save file
     */
    void loadLinks(java.io.File saveFile) throws IOException;
    
    /**
     * Recreate a deleted marker with preserved UID.
     * @param originalMarker The marker to recreate
     * @return Recreated marker with same UID
     */
    PortalMarker recreateMarker(PortalMarker originalMarker);
    
    /**
     * Process a teleportation event and create/update links.
     * @param event The teleportation event to process
     */
    void processTeleportation(TeleportationEvent event);
}
```

### Method Contracts

#### createLink

**Preconditions**:
- `sourceMarker` and `targetMarker` MUST NOT be null
- `sourceMarker.layer` and `targetMarker.layer` MUST differ by exactly 1
- Neither marker MAY already have a `linkUid` (or if they do, they must match)

**Postconditions**:
- New `PortalLink` created with unique 6-char UID
- Both markers assigned the same `linkUid`
- `sourceMarker.direction` = OUT
- `targetMarker.direction` = IN
- Link persisted to save file

**Error Cases**:
- Markers on same layer → `IllegalArgumentException`
- Markers already linked to different UIDs → `IllegalStateException`

---

#### getLinkedMarkers

**Preconditions**:
- `marker` MUST NOT be null

**Postconditions**:
- Returns list of markers sharing the same `linkUid`
- Returns empty list if marker not linked
- Does NOT include the input marker itself

**Performance**:
- MUST be O(1) lookup via HashMap index

---

#### processTeleportation

**Workflow**:
```
1. Check if event is layer transition
   └─ No → return immediately
   └─ Yes → continue

2. Get source and target portal coordinates
   └─ sourcePortal = event.sourceCoord (nearest portal on source layer)
   └─ targetPortal = event.targetCoord (nearest portal on target layer)

3. Find all markers on source layer for linking
   └─ Filter: markers with name "Cave" or "Hole"
   └─ Filter: markers without linkUid OR with matching linkUid
   └─ Exclude: markers already linked with different UID

4. For each marker on source layer:
   a. Calculate offset from source portal
   b. Transform to target layer coordinate
   c. Check if marker exists at target coordinate
      └─ Yes → link existing markers
      └─ No → create new marker on target layer

5. Persist all created/updated links
```

---

## 4. CoordinateTransformer

**Purpose**: Transform coordinates between layers.

### Interface

```java
package nurgling.utils;

import haven.Coord;

public interface CoordinateTransformer {
    
    /**
     * Transform a marker coordinate from source layer to target layer.
     * @param sourceMarkerCoord Marker coordinate on source layer
     * @param sourcePortalCoord Portal coordinate on source layer
     * @param targetPortalCoord Portal coordinate on target layer
     * @return Transformed coordinate on target layer
     */
    Coord transformCoordinate(
        Coord sourceMarkerCoord,
        Coord sourcePortalCoord,
        Coord targetPortalCoord
    );
    
    /**
     * Calculate offset vector from portal to marker.
     * @param markerCoord Marker coordinate
     * @param portalCoord Portal coordinate (reference point)
     * @return Offset vector
     */
    Coord calculateOffset(Coord markerCoord, Coord portalCoord);
    
    /**
     * Apply offset vector to a coordinate.
     * @param baseCoord Base coordinate
     * @param offset Offset vector to apply
     * @return Resulting coordinate
     */
    Coord applyOffset(Coord baseCoord, Coord offset);
}
```

### Implementation Contract

**Transformation Formula**:

```java
Coord transformCoordinate(
    Coord sourceMarkerCoord,
    Coord sourcePortalCoord,
    Coord targetPortalCoord
) {
    Coord offset = calculateOffset(sourceMarkerCoord, sourcePortalCoord);
    return applyOffset(targetPortalCoord, offset);
}

Coord calculateOffset(Coord markerCoord, Coord portalCoord) {
    return markerCoord.sub(portalCoord);  // Vector subtraction
}

Coord applyOffset(Coord baseCoord, Coord offset) {
    return baseCoord.add(offset);  // Vector addition
}
```

**Example**:

```
Surface (Layer 0):
  sourcePortalCoord = (50, 60)
  sourceMarkerCoord = (50, 80)
  offset = (50-50, 80-60) = (0, 20)

Underground (Layer 1):
  targetPortalCoord = (45, 55)
  targetMarkerCoord = (45+0, 55+20) = (45, 75) ✓
```

---

## 5. PortalMarker UI Integration

**Purpose**: Integrate linked markers with map UI.

### Interface with MapWnd

```java
package haven;

import nurgling.markers.PortalMarker;
import java.util.Comparator;

public class MapWnd {
    
    /**
     * Get sorted list of all markers.
     * Linked markers are grouped together.
     */
    public List<PortalMarker> getSortedMarkers() {
        return markers.stream()
            .sorted(
                Comparator
                    // Primary: alphabetically by name
                    .comparing(m -> m.name)
                    // Secondary: by linkUid (groups linked markers)
                    .thenComparing(m -> m.linkUid, Comparator.nullsLast(Comparator.naturalOrder()))
                    // Tertiary: by direction (OUT before IN)
                    .thenComparing(m -> m.direction)
            )
            .collect(Collectors.toList());
    }
    
    /**
     * Center map on a marker.
     */
    public void centerOnMarker(PortalMarker marker);
}
```

### Marker List Sorting

**Sort Order**:
1. **Primary**: Alphabetically by `name` ("Cave" before "Hole")
2. **Secondary**: By `linkUid` (groups linked markers together)
3. **Tertiary**: By `direction` (OUT before IN)

**Example Sorted List**:
```
Cave           (unlinked)
Cave abc123 OUT  ← linked pair grouped together
Cave abc123 IN   ←
Cave def456 OUT  ← another linked pair
Cave def456 IN   ←
Hole ghi789 OUT  ← linked pair
Hole ghi789 IN   ←
Hole             (unlinked)
```

---

## Error Handling

### Error Categories

**Recoverable Errors** (log at WARN, continue):
- Marker not found at expected coordinate → create new marker
- Link UID collision → generate new UID
- Save file not found → initialize empty links

**Unrecoverable Errors** (log at ERROR, abort):
- Invalid coordinate transformation (null inputs)
- Corrupted save file (JSON parse error)
- Database constraint violation (duplicate UID)

### Logging Requirements

```java
// Info: Normal operations
log.info("Created portal link {} between layers {}→{}", linkUid, sourceLayer, targetLayer);

// Warn: Recoverable issues
log.warn("Marker not found at target coordinate {}, creating new marker", targetCoord);

// Error: Unrecoverable issues
log.error("Failed to save portal links: {}", errorMessage, exception);
```

---

## Testing Contracts

### Unit Test Requirements

**TeleportationDetector**:
- Test detection of discontinuous position changes
- Test classification of different teleportation types
- Test non-detection of continuous movement

**LayerTransitionDetector**:
- Test layer transition detection for cave, minehole, ladder
- Test non-detection for hearthfire, totem, signpost
- Test transition direction calculation

**CoordinateTransformer**:
- Test coordinate transformation with known offsets
- Test edge cases (negative coordinates, chunk boundaries)
- Test round-trip transformation (A→B→A)

**PortalLinkManager**:
- Test link creation with valid markers
- Test link creation rejection (same layer)
- Test marker recreation after deletion
- Test save/load round-trip

### Integration Test Requirements

**End-to-End Flow**:
1. Player enters cave on surface
2. System detects layer transition
3. System creates linked markers
4. Player clicks marker in list → map centers
5. Player clicks linked marker → map switches layer
6. Save/reload → links preserved

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-21 | Initial API contracts |
