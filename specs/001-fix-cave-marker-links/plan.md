# Implementation Plan: Fix Cave Marker Adjacent Display in Map Marker List

**Branch**: `001-fix-cave-marker-links` | **Date**: 2026-02-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-fix-cave-marker-links/spec.md`

## Summary

**Primary Requirement**: Fix linked cave marker adjacency in the map marker list (3 outdoor + 3 indoor = 6 markers should appear as 3 adjacent pairs when sorted alphabetically), fix array-to-string conversion errors in portal link rendering, and reduce DEBUG log spam.

**Technical Approach**: 
1. Ensure linked markers share identical name ("Cave") and 6-character UID for proper alphabetical adjacency
2. Fix `Arrays.toString()` or custom formatting for grid data logging instead of direct object array printing
3. Adjust log level thresholds and suppress per-frame DEBUG messages in `MinimapPortalLinkRenderer` and `MiniMap.findicons/markobjs`

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: SLF4J/Logback (logging), Haven client framework
**Storage**: N/A (in-memory marker data, persisted via game save files)
**Testing**: JUnit 5 with Mockito
**Target Platform**: Haven client (desktop Java application)
**Project Type**: Single project (existing Nurgling2 mod codebase)
**Performance Goals**: 
- Marker list sorting: <50ms for 100 markers
- Render cycle: <16ms (60 FPS) for portal link indicators
- Log file growth: <1MB per hour during normal gameplay

**Constraints**:
- Must maintain backward compatibility with 001-portal-marker-linking feature
- Cannot break existing marker persistence across sessions
- Log changes must preserve ERROR/WARN level messages for debugging

**Scale/Scope**:
- 6 cave markers (3 pairs) typical scenario
- Up to 20+ marker pairs in extended gameplay
- 1000+ render cycles per hour

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Initial | Post-Design | Notes |
|-----------|---------|-------------|-------|
| **I. Code Quality First** | ✅ PASS | ✅ PASS | Design maintains code quality - methods <50 lines, proper logging |
| **II. Incremental Refactoring** | ✅ PASS | ✅ PASS | Small changes: sort comparator, log formatting, state tracking |
| **III. Comprehensive Logging** | ✅ PASS | ✅ PASS | Design improves logging: state-based, proper error formatting |
| **IV. Automatic Testing First** | ✅ PASS | ✅ PASS | Test strategy defined in contracts (unit + integration tests) |
| **V. Modular Architecture** | ✅ PASS | ✅ PASS | Changes confined to MiniMap.java, MinimapPortalLinkRenderer.java |
| **VI. Documentation & Knowledge Sharing** | ✅ PASS | ✅ PASS | research.md, data-model.md, quickstart.md, contracts created |
| **VII. AI-Assisted Development** | ✅ PASS | ✅ PASS | Using spec-kit workflow, feedback artifacts will be created |
| **VIII. English Language for Code Artifacts** | ✅ PASS | ✅ PASS | All documentation and code in English |

**GATE RESULT**: ✅ PASSED - All constitution principles satisfied (pre and post-design)

## Project Structure

### Documentation (this feature)

```text
specs/001-fix-cave-marker-links/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (API contracts for marker sorting)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/
├── haven/
│   └── MiniMap.java                    # findicons(), markobjs() - log spam fix
├── nurgling/
│   ├── overlays/map/
│   │   └── MinimapPortalLinkRenderer.java  # renderPortalLinkIndicators() - error fix, log optimization
│   └── widgets/
│       └── NMiniMap.java               # Integration point for portal link rendering
└── tests/
    └── nurgling/
        └── overlays/map/
            └── MinimapPortalLinkRendererTest.java  # New test class
```

**Structure Decision**: Single project structure (existing Nurgling2 codebase). Changes confined to:
- `src/haven/MiniMap.java` - Reduce log spam in `findicons()` and `markobjs()`
- `src/nurgling/overlays/map/MinimapPortalLinkRenderer.java` - Fix array toString() error, optimize logging
- `src/tests/` - New test classes for marker adjacency and rendering

## Complexity Tracking

No constitution violations requiring justification.

---

## Phase 0: Outline & Research

### Unknowns Identified

1. **Marker Sorting Mechanism**: How does the map marker list currently sort markers? What fields are used for alphabetical ordering?
2. **UID Assignment**: How are 6-character UIDs currently assigned to linked marker pairs in 001-portal-marker-linking?
3. **Grid Data Structure**: What is the exact structure of the `display` grid array in `MinimapPortalLinkRenderer`?
4. **Log Configuration**: What is the current logback configuration for file rotation and level filtering?

### Research Tasks

- [ ] **Task 0.1**: Research marker sorting in MapWnd/MiniMap - identify sort key fields
- [ ] **Task 0.2**: Research UID generation and assignment in 001-portal-marker-linking implementation
- [ ] **Task 0.3**: Analyze `MinimapPortalLinkRenderer.renderPortalLinkIndicators()` error source
- [ ] **Task 0.4**: Review logback configuration and current log output patterns

---

## Phase 1: Design & Contracts

### Design Deliverables

1. **data-model.md**: Document marker data structure, UID field, sorting key composition
2. **contracts/**: Define marker sorting contract (name + UID as composite key)
3. **quickstart.md**: Setup guide for testing marker adjacency
4. **Agent Context Update**: Add Java 21 + SLF4J best practices to agent memory

### Implementation Contracts

**Contract 1: Marker Adjacency**
- Linked markers MUST share identical `name` field ("Cave")
- Linked markers MUST share identical `linkUid` field (6 characters: 0-9a-zA-Z)
- Marker list sort MUST use composite key: `name + linkUid` for alphabetical ordering

**Contract 2: Log Output**
- ERROR level: Always logged with full stack trace
- WARN level: Always logged
- INFO level: Logged once per state change (not per frame)
- DEBUG level: Suppressed during routine operation (configurable)

**Contract 3: Error Handling**
- Array/grid data MUST use `Arrays.toString()` or custom formatter
- Object hash codes MUST NOT appear in log output
- Rendering errors MUST NOT disrupt minimap display

---

## Phase 2: Implementation Tasks

*Note: Tasks will be created by `/speckit.tasks` command*

### Expected Task Categories

1. **Marker Adjacency Fix**
   - Verify UID assignment in marker creation
   - Update marker sort comparator if needed
   - Test adjacency with 3+ marker pairs

2. **Rendering Error Fix**
   - Fix array toString() in `MinimapPortalLinkRenderer`
   - Add null/empty checks for grid data
   - Improve error message formatting

3. **Log Optimization**
   - Reduce DEBUG logging in `findicons()` and `markobjs()`
   - Add state-change logging instead of per-frame
   - Configure log level threshold

4. **Testing**
   - Unit tests for marker adjacency
   - Integration tests for rendering
   - Log output verification tests

---

## Success Metrics

- ✅ All 6 cave markers display with linked pairs adjacent (100% adjacency rate)
- ✅ Zero `[Ljava.lang.Object;@` hash codes in logs (0 occurrences in 30 min test)
- ✅ 90% reduction in DEBUG log messages (measured by line count)
- ✅ All tests pass (JUnit 5 + integration tests)
- ✅ Code compiles without warnings (Java 21, strict flags)
