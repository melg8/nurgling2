# API Contract: Marker Sorting

**Feature**: 001-fix-cave-marker-links  
**Date**: 2026-02-21  
**Version**: 1.0

---

## Overview

This contract defines the sorting behavior for the map marker list to ensure linked cave markers appear adjacent when sorted.

---

## Sort Comparator Contract

### Interface

```java
/**
 * Comparator for sorting map markers in the marker list.
 * 
 * <p>Sort Order (composite key):</p>
 * <ol>
 *   <li>Primary: Icon resource name (alphabetical)</li>
 *   <li>Secondary: Link UID (alphabetical) - for PMarker instances</li>
 *   <li>Tertiary: Z-order (ascending)</li>
 * </ol>
 * 
 * <p>This ensures that linked markers (same name + same UID) appear adjacent.</p>
 */
public class MarkerSortComparator implements Comparator<DisplayIcon> {
    
    @Override
    public int compare(DisplayIcon a, DisplayIcon b);
}
```

### Method: `compare(DisplayIcon a, DisplayIcon b)`

**Parameters**:
- `a` - First DisplayIcon to compare
- `b` - Second DisplayIcon to compare

**Returns**:
- `int` - Negative if `a < b`, zero if equal, positive if `a > b`

**Sort Logic**:

```java
public int compare(DisplayIcon a, DisplayIcon b) {
    // Null safety
    if (a == null) return (b == null) ? 0 : -1;
    if (b == null) return 1;
    
    // Primary sort: by icon resource name (alphabetical)
    String nameA = a.attr.icon().res.name;
    String nameB = b.attr.icon().res.name;
    int nameCmp = nameA.compareTo(nameB);
    if (nameCmp != 0) {
        return nameCmp;
    }
    
    // Secondary sort: by linkUid (for PMarker instances)
    String uidA = getLinkUid(a);
    String uidB = getLinkUid(b);
    
    // If both have UIDs, compare by UID
    if (uidA != null && uidB != null) {
        int uidCmp = uidA.compareTo(uidB);
        if (uidCmp != 0) {
            return uidCmp;
        }
    }
    
    // If only one has UID, the one with UID comes first
    if (uidA != null) return -1;
    if (uidB != null) return 1;
    
    // Tertiary sort: by z-order (ascending)
    return Integer.compare(a.z, b.z);
}

/**
 * Helper method to extract linkUid from DisplayIcon.
 * Returns null if marker is not a PMarker or has no linkUid.
 */
private String getLinkUid(DisplayIcon icon) {
    if (icon.attr instanceof PMarker) {
        PMarker pMarker = (PMarker) icon.attr;
        return pMarker.linkUid;
    }
    return null;
}
```

---

## Expected Behavior

### Input: 6 Cave Markers (3 Linked Pairs)

**Before Sorting**:
```
Index | Name  | UID      | Direction | Z-order
------|-------|----------|-----------|--------
0     | Cave  | xyz789   | OUT       | 5
1     | Cave  | abc123   | IN        | 2
2     | Cave  | def456   | OUT       | 7
3     | Cave  | abc123   | OUT       | 1
4     | Cave  | xyz789   | IN        | 6
5     | Cave  | def456   | IN        | 8
```

**After Sorting** (by name → uid → z):
```
Index | Name  | UID      | Direction | Z-order
------|-------|----------|-----------|--------
0     | Cave  | abc123   | OUT       | 1     ← Pair 1
1     | Cave  | abc123   | IN        | 2     ←
2     | Cave  | def456   | OUT       | 7     ← Pair 2
3     | Cave  | def456   | IN        | 8     ←
4     | Cave  | xyz789   | OUT       | 5     ← Pair 3
5     | Cave  | xyz789   | IN        | 6     ←
```

**Result**: ✅ All linked pairs are adjacent

---

## Edge Cases

### Case 1: Mixed Marker Types

**Input**: Cave markers + other marker types

**Behavior**:
- Different marker types sorted by name first
- All "Cave" markers grouped together
- Within "Cave" group, linked pairs adjacent

```
Marker List:
  1. Cave [UID=abc123] OUT
  2. Cave [UID=abc123] IN
  3. Chest [UID=null]
  4. Chest [UID=null]
  5. Grave [UID=null]
```

### Case 2: One Marker Missing UID

**Input**: One marker in pair has null UID

**Behavior**:
- Marker with UID sorts before marker without UID
- Adjacency may be broken (indicates data integrity issue)

```
Marker List:
  1. Cave [UID=abc123] OUT  ← Has UID
  2. Cave [UID=null] IN     ← Missing UID (data error)
```

**Action**: Log WARN level message about UID mismatch

### Case 3: Same UID, Different Names

**Input**: Markers with same UID but different names (should not happen)

**Behavior**:
- Sorted by name first, so may not be adjacent
- Indicates data corruption

```
Marker List:
  1. Cave [UID=abc123] OUT
  2. Portal [UID=abc123] IN  ← Different name, same UID (error)
```

**Action**: Log ERROR level message about UID/name mismatch

---

## Validation Rules

### Rule 1: UID Format
```
linkUid MUST match: ^[0-9a-zA-Z]{6}$
```

**Validation Code**:
```java
private static final Pattern UID_PATTERN = Pattern.compile("^[0-9a-zA-Z]{6}$");

public boolean isValidUid(String uid) {
    return uid != null && UID_PATTERN.matcher(uid).matches();
}
```

### Rule 2: Pair Integrity
```
For every PMarker with linkUid:
  - Exactly ONE other PMarker MUST exist with same linkUid
  - Both MUST have same name
  - Both MUST have opposite direction
```

**Validation Code**:
```java
public void validateMarkerPairs(Collection<DisplayIcon> markers) {
    Map<String, List<DisplayIcon>> byUid = new HashMap<>();
    
    for (DisplayIcon icon : markers) {
        String uid = getLinkUid(icon);
        if (uid != null) {
            byUid.computeIfAbsent(uid, k -> new ArrayList<>()).add(icon);
        }
    }
    
    for (Map.Entry<String, List<DisplayIcon>> entry : byUid.entrySet()) {
        List<DisplayIcon> pair = entry.getValue();
        if (pair.size() != 2) {
            log.warn("UID {} has {} markers (expected 2)", entry.getKey(), pair.size());
        }
    }
}
```

---

## Performance Requirements

### Requirement 1: Sort Time

**Metric**: Sort 100 markers in <50ms

**Benchmark**:
```java
@Test
public void testSortPerformance() {
    List<DisplayIcon> markers = generateTestMarkers(100);
    Comparator<DisplayIcon> comparator = new MarkerSortComparator();
    
    long start = System.nanoTime();
    Collections.sort(markers, comparator);
    long end = System.nanoTime();
    
    long durationMs = (end - start) / 1_000_000;
    assertTrue("Sort took " + durationMs + "ms (expected <50ms)", durationMs < 50);
}
```

### Requirement 2: Stability

**Metric**: Same input produces same output across 1000 iterations

**Benchmark**:
```java
@Test
public void testSortStability() {
    List<DisplayIcon> markers = generateTestMarkers(6);
    Comparator<DisplayIcon> comparator = new MarkerSortComparator();
    
    List<Integer> results = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
        Collections.sort(markers, comparator);
        results.add(markers.hashCode());
    }
    
    // All results should be identical
    long unique = results.stream().distinct().count();
    assertEquals("Sort was not stable", 1, unique);
}
```

---

## Testing

### Unit Test: Adjacency

```java
@Test
public void testLinkedMarkersAdjacent() {
    // Arrange
    List<DisplayIcon> markers = Arrays.asList(
        createCaveMarker("Cave", "xyz789", "OUT", 5),
        createCaveMarker("Cave", "abc123", "IN", 2),
        createCaveMarker("Cave", "def456", "OUT", 7),
        createCaveMarker("Cave", "abc123", "OUT", 1),
        createCaveMarker("Cave", "xyz789", "IN", 6),
        createCaveMarker("Cave", "def456", "IN", 8)
    );
    
    // Act
    Collections.sort(markers, new MarkerSortComparator());
    
    // Assert
    assertEquals("abc123", getLinkUid(markers.get(0)));
    assertEquals("abc123", getLinkUid(markers.get(1)));
    assertEquals("def456", getLinkUid(markers.get(2)));
    assertEquals("def456", getLinkUid(markers.get(3)));
    assertEquals("xyz789", getLinkUid(markers.get(4)));
    assertEquals("xyz789", getLinkUid(markers.get(5)));
}
```

### Integration Test: UI Display

```java
@Test
public void testMarkerListDisplay() {
    // Setup: Create 3 linked cave pairs
    MapFile mapFile = createTestMapFile();
    createLinkedCavePair(mapFile, "abc123", level0, level1);
    createLinkedCavePair(mapFile, "def456", level0, level1);
    createLinkedCavePair(mapFile, "xyz789", level0, level1);
    
    // Open marker list UI
    MapWnd mapWnd = new MapWnd(mapFile);
    List<DisplayIcon> displayed = mapWnd.getDisplayedMarkers();
    
    // Verify adjacency
    assertAdjacentPairs(displayed);
}
```

---

## Backward Compatibility

### Compatibility with 001-portal-marker-linking

This contract maintains compatibility with the original portal marker linking feature:

| Aspect | Original | Current | Compatible |
|--------|----------|---------|------------|
| UID format | 6-char alphanumeric | Same | ✅ |
| Marker type | PMarker | Same | ✅ |
| Direction enum | IN/OUT | Same | ✅ |
| Name field | "Cave" | Same | ✅ |
| Sort order | Z-order only | Name+UID+Z | ⚠️ (enhancement) |

**Note**: Sort order change is intentional and required for adjacency feature.

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-21 | Initial contract for marker sorting |
