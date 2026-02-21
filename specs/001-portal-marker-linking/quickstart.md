# Quickstart: Portal Marker Linking Development

**Date**: 2026-02-21
**Feature**: Portal Marker Linking Between Map Layers
**Branch**: 001-portal-marker-linking

---

## Overview

This feature automatically links portal markers (cave, minehole, ladder) between map layers when the player transitions through portals. Linked markers share a unique 6-character UID and appear adjacent in the marker list for quick switching.

---

## Building the Feature

### Prerequisites

- Java 21 (LTS)
- Ant build system
- Existing Nurgling2 codebase

### Build Commands

```bash
# Compile the project
ant build

# Run with the feature
ant run

# Run tests
ant test
```

---

## Testing Locally

### Manual Testing Workflow

**Step 1: Surface Exploration**

1. Start game and navigate to surface (layer 0)
2. Locate a cave entrance
3. Verify cave marker appears on map automatically

**Step 2: Layer Transition**

1. Enter the cave
2. Teleport to underground (layer 1)
3. Verify two linked markers created:
   - Surface marker: "Cave abc123 OUT"
   - Underground marker: "Cave abc123 IN"

**Step 3: Marker Switching**

1. Open map (default: M key)
2. Click on "Cave abc123 OUT" in marker list
3. Verify map centers on surface cave
4. Click on "Cave abc123 IN" in marker list
5. Verify map switches to underground layer and centers on corresponding marker

**Step 4: Persistence Testing**

1. Save and exit game
2. Reload game
3. Open map
4. Verify linked markers still present with same UID

### Expected Behavior

| Scenario | Expected Result |
|----------|-----------------|
| Enter cave from surface | Two linked markers created (OUT on surface, IN underground) |
| Exit cave to surface | Map updates with IN marker on surface |
| Use hearthfire teleport | No linked markers created |
| Delete linked marker | Marker recreated on next portal visit with same UID |
| Multiple caves explored | Each cave pair has unique UID |

---

## Debugging Common Issues

### Issue 1: Markers Not Created on Transition

**Symptoms**: Player transitions through cave but no linked markers appear.

**Debug Steps**:

1. Check teleportation detection:
   ```java
   // In TeleportationDetector
   log.debug("Teleportation detected: {} -> {}", event.sourceCoord, event.targetCoord);
   ```

2. Verify layer transition classification:
   ```java
   // In LayerTransitionDetector
   log.debug("Is layer transition: {}", isLayerTransition(event));
   ```

3. Check portal type classification:
   ```java
   // Verify cave gob name pattern matching
   log.debug("Portal type: {}", ChunkPortal.classifyPortal(gobName));
   ```

**Common Causes**:
- Teleportation not detected (distance threshold too high)
- Portal type misclassified (gob name pattern not matching)
- Layer transition detection returning false

---

### Issue 2: Markers Created but Not Linked

**Symptoms**: Markers appear on both layers but have different UIDs or no UID.

**Debug Steps**:

1. Check link creation:
   ```java
   // In PortalLinkManager.processTeleportation()
   log.debug("Creating link for source={} target={}", sourceMarker, targetMarker);
   ```

2. Verify UID generation:
   ```java
   // Check UID is 6 characters alphanumeric
   log.debug("Generated UID: {}", linkUid);
   ```

3. Check marker assignment:
   ```java
   log.debug("Marker directions: source={} target={}", 
             sourceMarker.direction, targetMarker.direction);
   ```

**Common Causes**:
- Link creation failed (markers on same layer)
- UID collision (rare, should regenerate)
- Marker assignment logic error

---

### Issue 3: Markers at Wrong Coordinates

**Symptoms**: Linked markers appear at incorrect locations on target layer.

**Debug Steps**:

1. Check coordinate transformation:
   ```java
   // In CoordinateTransformer
   log.debug("Transform: marker={} portal_src={} portal_tgt={}", 
             sourceMarkerCoord, sourcePortalCoord, targetPortalCoord);
   ```

2. Verify offset calculation:
   ```java
   Coord offset = calculateOffset(sourceMarkerCoord, sourcePortalCoord);
   log.debug("Offset: {}", offset);
   ```

3. Check target coordinate:
   ```java
   Coord targetCoord = applyOffset(targetPortalCoord, offset);
   log.debug("Target coordinate: {}", targetCoord);
   ```

**Common Causes**:
- Offset calculation sign error (subtraction vs addition)
- Using wrong portal as reference point
- Coordinate system mismatch (local vs world)

---

### Issue 4: Markers Not Persisted After Reload

**Symptoms**: Linked markers disappear after saving and reloading game.

**Debug Steps**:

1. Check save operation:
   ```java
   // In PortalLinkManager.saveLinks()
   log.debug("Saving {} links to {}", links.size(), saveFile.getPath());
   ```

2. Verify JSON format:
   ```java
   // Check JSON structure
   log.debug("Save JSON: {}", jsonArray.toString(2));
   ```

3. Check load operation:
   ```java
   // In PortalLinkManager.loadLinks()
   log.debug("Loaded {} links from {}", links.size(), saveFile.getPath());
   ```

**Common Causes**:
- Save file path incorrect
- JSON serialization error
- Load operation not called on game start
- Save file schema version mismatch

---

## Log Configuration

### Enable Debug Logging

Add to `logback.xml`:

```xml
<configuration>
    <!-- Portal linking debug logging -->
    <logger name="nurgling.teleportation" level="DEBUG"/>
    <logger name="nurgling.markers" level="DEBUG"/>
    <logger name="nurgling.utils.CoordinateTransformer" level="DEBUG"/>
    
    <!-- Info level for production -->
    <logger name="nurgling" level="INFO"/>
</configuration>
```

### Key Log Messages

**Normal Operation**:
```
INFO  nurgling.markers.PortalLinkManager - Created portal link abc123 between layers 0→1
INFO  nurgling.markers.PortalLinkManager - Saved 5 portal links to save.dat
```

**Debug Information**:
```
DEBUG nurgling.teleportation.TeleportationDetector - Teleportation detected: (50,60) -> (45,55)
DEBUG nurgling.teleportation.LayerTransitionDetector - Is layer transition: true
DEBUG nurgling.utils.CoordinateTransformer - Transform: marker=(50,80) portal_src=(50,60) portal_tgt=(45,55)
DEBUG nurgling.utils.CoordinateTransformer - Offset: (0,20)
DEBUG nurgling.utils.CoordinateTransformer - Target coordinate: (45,75)
```

---

## Code Navigation

### Key Files

| File | Purpose |
|------|---------|
| `src/nurgling/teleportation/TeleportationDetector.java` | Detect teleportation events |
| `src/nurgling/teleportation/LayerTransitionDetector.java` | Classify layer transitions |
| `src/nurgling/markers/PortalMarker.java` | Portal marker data structure |
| `src/nurgling/markers/PortalLink.java` | Link between two markers |
| `src/nurgling/markers/PortalLinkManager.java` | Manage link lifecycle |
| `src/nurgling/utils/CoordinateTransformer.java` | Coordinate transformation |
| `src/nurgling/overlays/map/MinimapPortalLinkRenderer.java` | Render linked markers |

### Related Existing Files

| File | Purpose |
|------|---------|
| `src/nurgling/navigation/UnifiedTilePathfinder.java` | Existing portal traversal |
| `src/nurgling/navigation/ChunkPortal.java` | Portal data structure |
| `src/haven/MapWnd.java` | Map window base class |
| `src/nurgling/widgets/NMapWnd.java` | Nurgling map window extensions |

---

## Performance Considerations

### Marker Limit

- No hard limit on number of linked markers
- Performance impact: O(1) lookup per marker via HashMap index
- Memory: ~100 bytes per link (negligible for typical <1000 links)

### Save File Size

- Each link: ~200 bytes JSON
- 1000 links: ~200KB (acceptable)
- Compression: Save file already compressed by game

---

## Troubleshooting Checklist

- [ ] Teleportation detected when entering cave
- [ ] Layer transition classified correctly (not hearthfire/totem)
- [ ] Portal type classified as CAVE, MINEHOLE, or LADDER
- [ ] Link created with unique 6-char UID
- [ ] Markers assigned correct direction (IN/OUT)
- [ ] Coordinates transformed correctly using portal as reference
- [ ] Markers appear in marker list sorted alphabetically
- [ ] Linked markers grouped together in list
- [ ] Click on marker centers map correctly
- [ ] Links persisted to save file
- [ ] Links loaded on game restart

---

## Next Steps

After verifying the feature works:

1. **Run automated tests**: `ant test`
2. **Check code quality**: Verify against constitution principles
3. **Create AI feedback**: Document any issues in `docs/ai-feedback/`
4. **Commit changes**: Use conventional commits format

---

## Support

For issues or questions:
- Check `research.md` for design rationale
- Check `data-model.md` for entity definitions
- Check `portal-linking-api.md` for API contracts
- Review existing code in `src/nurgling/navigation/` for patterns
