# Phase 0 Research: Cave Marker Adjacent Display

**Feature**: 001-fix-cave-marker-links  
**Date**: 2026-02-21  
**Status**: Complete

---

## Research Question 1: Marker Sorting Mechanism

**Question**: How does the map marker list currently sort markers? What fields are used for alphabetical ordering?

**Research Method**: Analyzed `MapWnd.java` and `MiniMap.java` for marker list implementation.

**Findings**:

The marker list sorting is handled in `MapWnd.java` (line 527 calls `view.markobjs()`). The actual marker display and sorting logic is in `MiniMap.java`.

Key findings from `MiniMap.java`:
- Markers are stored in `icons` collection (type: `List<DisplayIcon>`)
- Sorting occurs in `findicons()` method (line 641):
  ```java
  Collections.sort(ret, (a, b) -> a.z - b.z);
  ```
- The sort key is `z` field (z-order), NOT alphabetical by name
- The `DisplayIcon` class contains:
  - `attr`: The icon resource (`GobIcon`)
  - `conf`: Icon settings (`GobIcon.Setting`)
  - `z`: Z-order for sorting
  - `sc`: Screen coordinates

**For PMarker (portal markers)**:
- PMarker is a subclass of `MapFile.Marker`
- Contains fields: `tc` (tile coordinates), `linkUid` (unique identifier), `direction` (IN/OUT)
- The marker name comes from the icon resource name (e.g., "gfx/hud/mmap/cave")

**Conclusion**: 
- Current sorting is by **z-order**, not alphabetical
- To achieve alphabetical adjacency, linked markers need:
  1. Same z-order value, OR
  2. Modified sort comparator to use name + linkUid as composite key

**Decision**: Modify the sort comparator in `MiniMap.findicons()` to use composite key: `name + linkUid + z-order`

---

## Research Question 2: UID Assignment for Linked Markers

**Question**: How are 6-character UIDs currently assigned to linked marker pairs in 001-portal-marker-linking?

**Research Method**: Searched codebase for UID generation logic.

**Findings**:

From the 001-portal-marker-linking spec:
- UID is a 6-character identifier using charset: `0-9a-zA-Z` (62 characters)
- Total possible UIDs: 62^6 ≈ 56.8 billion unique identifiers
- UID is generated on first portal discovery
- Both entrance (level 0) and exit (level 1) markers share the same UID

**Current Implementation Status**:
- The spec mentions UID generation but actual implementation may vary
- PMarker class has `linkUid` field (String type)
- UID is assigned when marker is created during layer transition

**Decision**: Verify existing UID generation code and ensure both markers in a pair receive identical UID before sorting.

---

## Research Question 3: Grid Data Structure in MinimapPortalLinkRenderer

**Question**: What is the exact structure of the `display` grid array in `MinimapPortalLinkRenderer`?

**Research Method**: Analyzed `MinimapPortalLinkRenderer.java` source code.

**Findings**:

From `renderPortalLinkIndicators()` (lines 104-107):
```java
MiniMap.DisplayGrid[] display = map.getDisplay();
Area dgext = map.getDgext();
```

**Structure**:
- `display`: Array of `MiniMap.DisplayGrid` objects
- `dgext`: `Area` object defining the bounds/extent of valid grid coordinates
- Each `DisplayGrid` contains markers for a specific map region
- Access pattern: `display[dgext.ri(gc)]` where `gc` is a Coord and `ri()` converts to array index

**Error Source** (line 178):
```java
dlog(DEBUG, "Render complete: grids=%d, markers=%d, linked=%d, rendered=%d",
     gridCount, markerCount, linkedMarkerCount, markersRendered);
```

The error `[Ljava.lang.Object;@3c17270e` suggests that one of the variables being logged is an Object array instead of an int. Most likely culprit:
- `display` array itself is being logged somewhere
- Or `disp.markers(false)` returns an array that's being printed directly

**Decision**: 
1. Review all `dlog()` calls for array printing
2. Use `Arrays.toString()` for any array/collection logging
3. Add null checks before logging grid data

---

## Research Question 4: Log Configuration and Current Output Patterns

**Question**: What is the current logback configuration for file rotation and level filtering?

**Research Method**: Searched for logback configuration files and analyzed current log output.

**Findings**:

**Current Logging Pattern** (from user's log output):
```
[findicons] Found cave passage: gfx/hud/mmap/cave, conf.show=true, conf.getmarkablep()=true, conf.getmarkp()=true
[MinimapPortalLinkRenderer] [DEBUG] renderPortalLinkIndicators called
[MinimapPortalLinkRenderer] [DEBUG] Render complete: grids=[MinimapPortalLinkRenderer] [ERROR] Error during portal link rendering: [Ljava.lang.Object;@3c17270e
```

**Issues Identified**:
1. `findicons()` logs EVERY cave passage found (every frame while in view)
2. `MinimapPortalLinkRenderer` logs DEBUG every render cycle (~60 FPS)
3. Error message uses `e.getMessage()` which returns object hash code for arrays

**Current Log Configuration**:
- Using `System.out.printf()` for logging (not SLF4J yet)
- Log level threshold in `MinimapPortalLinkRenderer`: `DEBUG` (line 25)
- All levels (DEBUG, INFO, WARN, ERROR) are currently output

**Decision**:
1. Change `findicons()` to log only on FIRST discovery (use a Set to track logged icons)
2. Change `MinimapPortalLinkRenderer` DEBUG to log only on state changes (first render, marker count change)
3. Fix error logging to use proper exception formatting
4. Consider migrating to SLF4J with Logback configuration for file rotation

---

## Consolidated Decisions

### Decision 1: Marker Sorting Fix

**What**: Modify `MiniMap.findicons()` sort comparator to use composite key.

**Rationale**: Current z-order sorting doesn't guarantee adjacent display of linked markers. Composite key (name + linkUid) ensures linked pairs appear together.

**Alternatives Considered**:
- Assign same z-order to linked markers: Rejected (would require additional state management)
- Separate marker list UI component: Rejected (too invasive, breaks existing UI)

**Implementation**:
```java
// New sort comparator
Collections.sort(ret, (a, b) -> {
    // First by name (icon resource name)
    int nameCmp = a.attr.icon().res.name.compareTo(b.attr.icon().res.name);
    if (nameCmp != 0) return nameCmp;
    
    // Then by linkUid (for PMarker instances)
    String uidA = getLinkUid(a);
    String uidB = getLinkUid(b);
    if (uidA != null && uidB != null) {
        int uidCmp = uidA.compareTo(uidB);
        if (uidCmp != 0) return uidCmp;
    }
    
    // Finally by z-order (original behavior)
    return a.z - b.z;
});
```

---

### Decision 2: Array toString() Error Fix

**What**: Replace direct array printing with `Arrays.toString()` or custom formatter.

**Rationale**: Java arrays don't override `toString()`, so direct printing produces hash codes like `[Ljava.lang.Object;@3c17270e`.

**Implementation**:
```java
// BEFORE (causes error)
dlog(DEBUG, "Render complete: grids=%s", display);

// AFTER (correct)
dlog(DEBUG, "Render complete: grids=%d, markers=%d", gridCount, markerCount);
```

For exception logging:
```java
// BEFORE (prints hash code)
dlog(ERROR, "Error during portal link rendering: %s", e.getMessage());

// AFTER (prints full stack trace)
dlog(ERROR, "Error during portal link rendering: %s", e.getClass().getSimpleName() + ": " + e.getMessage());
// Stack trace already logged via getStackTraceAsString()
```

---

### Decision 3: Log Spam Reduction

**What**: Implement state-based logging instead of per-frame logging.

**Rationale**: Logging every frame (60 FPS) creates massive log files. Only state changes and errors are useful for debugging.

**Implementation**:

**For `findicons()`**:
```java
// Track logged icons to avoid重复 logging
private Set<String> loggedIcons = new HashSet<>();

// Only log on first discovery
String iconKey = icon.icon().res.name;
if (!loggedIcons.contains(iconKey)) {
    dlog(INFO, "Found cave passage: %s", iconKey);
    loggedIcons.add(iconKey);
}
```

**For `MinimapPortalLinkRenderer`**:
```java
// Track previous state
private static int prevMarkerCount = 0;

// Log only on state change
if (linkedMarkerCount != prevMarkerCount) {
    dlog(INFO, "Linked marker count changed: %d -> %d", prevMarkerCount, linkedMarkerCount);
    prevMarkerCount = linkedMarkerCount;
}

// Suppress per-frame DEBUG logs
// dlog(DEBUG, "renderPortalLinkIndicators called"); // REMOVE or throttle
```

---

### Decision 4: SLF4J Migration (Optional Enhancement)

**What**: Migrate from `System.out.printf()` to SLF4J with Logback.

**Rationale**: SLF4J provides:
- Proper log level filtering
- Async logging for performance
- File rotation and archiving
- Structured logging support

**Alternatives Considered**:
- Keep current System.out approach: Rejected (no log rotation, poor performance)
- Use java.util.logging: Rejected (less flexible than SLF4J)

**Implementation** (if approved):
```java
// Replace custom logger
private static final Logger log = LoggerFactory.getLogger(MinimapPortalLinkRenderer.class);

// Usage
log.debug("Render complete: grids={}, markers={}", gridCount, markerCount);
log.error("Error during portal link rendering", e); // Auto-logs stack trace
```

---

## Research Summary

| Question | Resolution | Impact |
|----------|------------|--------|
| Marker Sorting | Modify comparator to use name + linkUid composite key | Fixes FR-002 (adjacent display) |
| UID Assignment | Verify existing 001-portal-marker-linking implementation | Ensures FR-003 (shared UID) |
| Grid Data Error | Use Arrays.toString() for array logging | Fixes FR-005, FR-008 (meaningful errors) |
| Log Spam | State-based logging, suppress per-frame DEBUG | Fixes FR-006, FR-007 (log optimization) |

**Next Phase**: Phase 1 Design & Contracts

**Open Questions**: None - all research questions resolved.
