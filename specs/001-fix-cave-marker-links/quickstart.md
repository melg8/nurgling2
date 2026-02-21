# Quickstart: Testing Cave Marker Adjacency

**Feature**: 001-fix-cave-marker-links  
**Date**: 2026-02-21  
**Purpose**: Setup guide for testing marker adjacency and log optimization

---

## Prerequisites

- Java 21 (LTS) installed
- Nurgling2 client built and configured
- Access to test world with at least 3 cave portals
- Text editor for reviewing logs

---

## Build and Run

### 1. Build the Client

```bash
# From project root
ant clean build
```

**Expected Output**:
```
BUILD SUCCESSFUL
Total time: X seconds
```

### 2. Configure Log Level (Optional)

Edit `src/resources/logback.xml` (if using SLF4J):

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>nurgling.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Set root level to INFO to suppress DEBUG -->
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
    
    <!-- Or keep DEBUG for specific classes during testing -->
    <logger name="nurgling.overlays.map.MinimapPortalLinkRenderer" level="DEBUG"/>
</configuration>
```

### 3. Run the Client

```bash
# Using provided script
play.bat

# Or directly
java -jar build/nurgling2.jar
```

---

## Test Scenario 1: Marker Adjacency

### Setup

1. Start the client and load into test world
2. Ensure you're on surface level (level 0)
3. Open map (`M` key by default)

### Steps

1. **Locate 3 cave entrances** on the surface
2. **Enter each cave** and mark the exit on level 1
3. **Return to surface** after marking each exit
4. **Open map marker list** (click marker icon or press shortcut)

### Expected Behavior

**Before Fix**:
```
Marker List (sorted by z-order):
  1. Cave [UID=abc123] OUT
  2. Cave [UID=xyz789] OUT
  3. Cave [UID=abc123] IN
  4. Cave [UID=def456] OUT
  5. Cave [UID=xyz789] IN
  6. Cave [UID=def456] IN
```
❌ Linked pairs are NOT adjacent

**After Fix**:
```
Marker List (sorted by name + linkUid):
  1. Cave [UID=abc123] OUT  ← Pair 1
  2. Cave [UID=abc123] IN   ←
  3. Cave [UID=def456] OUT  ← Pair 2
  4. Cave [UID=def456] IN   ←
  5. Cave [UID=xyz789] OUT  ← Pair 3
  6. Cave [UID=xyz789] IN   ←
```
✅ Linked pairs ARE adjacent

### Verification

- [ ] All 6 markers visible in list
- [ ] Each entrance marker is directly above/below its corresponding exit marker
- [ ] Pairs are grouped together (no interleaving)
- [ ] Sorting is stable across map reloads

---

## Test Scenario 2: Error-Free Rendering

### Setup

1. Enable console/terminal to view log output
2. Clear existing logs:
   ```bash
   del nurgling.log
   del nurgling_markobjs.log
   ```

### Steps

1. Open map with cave markers active
2. View minimap for 30 seconds
3. Monitor console/log output

### Expected Behavior

**Before Fix**:
```
[MinimapPortalLinkRenderer] [ERROR] Error during portal link rendering: [Ljava.lang.Object;@3c17270e
[MinimapPortalLinkRenderer] [DEBUG] Stack trace: [Ljava.lang.Object;@1285011b
```
❌ Object hash codes in error messages

**After Fix**:
```
[MinimapPortalLinkRenderer] [ERROR] Error during portal link rendering: NullPointerException: null
[MinimapPortalLinkRenderer] [DEBUG] Stack trace:
  at nurgling.overlays.map.MinimapPortalLinkRenderer.renderPortalLinkIndicators(MinimapPortalLinkRenderer.java:123)
  at nurgling.widgets.NMiniMap.drawparts(NMiniMap.java:203)
  ...
```
✅ Human-readable error messages

### Verification

- [ ] Zero occurrences of pattern `[Ljava.lang.Object;@` in logs
- [ ] Error messages include exception class name and message
- [ ] Stack traces are properly formatted (one element per line)
- [ ] No rendering errors during normal operation

---

## Test Scenario 3: Log Spam Reduction

### Setup

1. Clear logs:
   ```bash
   del nurgling.log
   del nurgling_markobjs.log
   ```
2. Prepare timer/stopwatch

### Steps

1. Stand near cave entrance (markers visible)
2. Open minimap and leave it open for **5 minutes**
3. After 5 minutes, check log file sizes and line counts

### Expected Behavior

**Before Fix**:
```
# Log output (every frame, ~60 FPS):
[findicons] Found cave passage: gfx/hud/mmap/cave, conf.show=true, ...
[findicons] Found cave passage: gfx/hud/mmap/cave, conf.show=true, ...
[findicons] Found cave passage: gfx/hud/mmap/cave, conf.show=true, ...
[MinimapPortalLinkRenderer] [DEBUG] renderPortalLinkIndicators called
[MinimapPortalLinkRenderer] [DEBUG] renderPortalLinkIndicators called
[MinimapPortalLinkRenderer] [DEBUG] renderPortalLinkIndicators called
[markobjs] Checking 3 icons
[markobjs] Checking 3 icons
[markobjs] Checking 3 icons
```

Log file size after 5 minutes: **~50 MB**  
Log lines: **~500,000 lines**

**After Fix**:
```
# Log output (state changes only):
[findicons] Found cave passage: gfx/hud/mmap/cave (logged ONCE on first discovery)
[MinimapPortalLinkRenderer] [INFO] Linked marker count changed: 0 -> 6 (logged ONCE when markers appear)
[markobjs] Checking icons (logged ONCE per session or state change)
```

Log file size after 5 minutes: **<5 MB**  
Log lines: **<50,000 lines** (90% reduction)

### Verification

- [ ] `findicons()` logs cave discovery only ONCE (not every frame)
- [ ] `MinimapPortalLinkRenderer` DEBUG logs suppressed during routine operation
- [ ] `markobjs()` logs only on state changes
- [ ] Log file growth rate reduced by ≥75%
- [ ] DEBUG line count reduced by ≥90%

### Measurement Commands

```bash
# Count lines in log file
wc -l nurgling_markobjs.log

# Count DEBUG messages
findstr /C:"[DEBUG]" nurgling.log | find /C /V ""

# Count ERROR messages with hash codes
findstr /C:"[Ljava.lang.Object;@" nurgling.log | find /C /V ""
```

---

## Test Scenario 4: Multiple Cave Systems

### Setup

1. Load test world with 5+ cave portals
2. Visit each cave and create linked pairs

### Steps

1. Open marker list
2. Verify all 10+ markers (5 pairs) display correctly
3. Check adjacency for each pair

### Expected Behavior

```
Marker List:
  1. Cave [UID=aaa111] OUT  ← Pair 1
  2. Cave [UID=aaa111] IN   ←
  3. Cave [UID=bbb222] OUT  ← Pair 2
  4. Cave [UID=bbb222] IN   ←
  5. Cave [UID=ccc333] OUT  ← Pair 3
  6. Cave [UID=ccc333] IN   ←
  7. Cave [UID=ddd444] OUT  ← Pair 4
  8. Cave [UID=ddd444] IN   ←
  9. Cave [UID=eee555] OUT  ← Pair 5
 10. Cave [UID=eee555] IN   ←
```

### Verification

- [ ] All pairs adjacent (no cross-linking)
- [ ] No performance degradation with many markers
- [ ] Sorting remains stable

---

## Test Scenario 5: Layer Transition

### Setup

1. Start on surface level (level 0)
2. Have 3 linked cave pairs active

### Steps

1. Open marker list, verify adjacency
2. Enter cave → transition to level 1
3. Open marker list again
4. Return to surface
5. Open marker list third time

### Expected Behavior

- Marker adjacency preserved across all transitions
- No duplicate markers created
- No log spam during transitions
- Smooth UI updates (no flickering)

### Verification

- [ ] Adjacency maintained on level 0 → level 1 transition
- [ ] Adjacency maintained on level 1 → level 0 transition
- [ ] No ERROR messages during transitions
- [ ] Marker count stable (6 markers throughout)

---

## Troubleshooting

### Issue: Markers Not Adjacent

**Symptoms**: Linked pairs still separated in list

**Possible Causes**:
1. Sort comparator not updated
2. UID not assigned correctly
3. Name mismatch between pairs

**Debug Steps**:
```java
// Add temporary debug logging in MiniMap.findicons()
for (DisplayIcon icon : ret) {
    String uid = getLinkUid(icon);
    System.out.printf("Marker: name=%s, uid=%s, z=%d%n", 
                      icon.attr.icon().res.name, uid, icon.z);
}
```

### Issue: Hash Codes Still in Logs

**Symptoms**: `[Ljava.lang.Object;@` pattern still appears

**Possible Causes**:
1. Array logging not fixed in all locations
2. Exception message extraction incorrect

**Debug Steps**:
```bash
# Search for array logging
findstr /S /N "\[Ljava.lang" *.log

# Check all dlog() calls in MinimapPortalLinkRenderer.java
```

### Issue: Log Spam Continues

**Symptoms**: DEBUG messages still appearing every frame

**Possible Causes**:
1. State tracking variables not initialized
2. Log level threshold not set correctly
3. Per-frame logging not converted to state-based

**Debug Steps**:
```java
// Check LOG_LEVEL constant
System.out.println("Current LOG_LEVEL: " + LOG_LEVEL);

// Verify state tracking
System.out.println("prevMarkerCount: " + prevMarkerCount);
```

---

## Success Criteria Checklist

After completing all test scenarios, verify:

- [ ] **SC-001**: 100% marker adjacency (6/6 markers in correct pairs)
- [ ] **SC-002**: Zero hash code errors in 30-minute test
- [ ] **SC-003**: 90% DEBUG message reduction (count lines before/after)
- [ ] **SC-004**: 99% render cycle success (no errors in 1000 cycles)
- [ ] **SC-005**: All errors human-readable (manual review)
- [ ] **SC-006**: Adjacency preserved across 50 reload/transition cycles
- [ ] **SC-007**: 75% log file growth reduction (compare file sizes)

---

## Next Steps

After successful testing:

1. Run full test suite: `ant test`
2. Create test report in `specs/001-fix-cave-marker-links/test-report.md`
3. Submit for code review
4. Merge to main branch
