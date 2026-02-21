# Implementation Plan: Portal Marker Linking Between Map Layers

**Branch**: `001-portal-marker-linking` | **Date**: 2026-02-21 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-portal-marker-linking/spec.md`

## Summary

**Primary Requirement**: Implement automatic linking of portal markers (cave, minehole, ladder) between map layers when player transitions through portals. Linked markers share a unique 6-character UID and appear adjacent in the marker list for quick switching.

**Technical Approach**: 
- Detect layer transitions by monitoring teleportation events and distinguishing portal transitions from other teleportation types (hearthfire, totem, signposts)
- Use transition portal as zero reference point for coordinate transformation between layers
- Preserve relative portal positions between connected layers using offset vectors
- Store linked marker data with UID, direction markers (IN/OUT), and persistence to save file

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Existing Nurgling2 navigation system, map marker system, SLF4J/Logback
**Storage**: Map marker save file (extension with link UID, direction marker fields)
**Testing**: JUnit 5 with Mockito for unit tests; Integration tests for game client teleportation detection
**Target Platform**: Haven & Hearth game client (Java-based MMO)
**Project Type**: Single project (Java client mod)
**Performance Goals**: Marker creation within 2 seconds of layer transition; No FPS degradation during marker operations
**Constraints**: Must not interfere with existing navigation/pathfinding systems; Backward compatible with existing marker save format
**Scale/Scope**: Single-player feature affecting map UI and marker persistence; No server-side changes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance Status | Notes |
|-----------|-------------------|-------|
| I. Code Quality First | ✅ PASS | Will follow all code quality rules (Javadoc, method limits, no warnings) |
| II. Incremental Refactoring | ✅ PASS | Feature adds new functionality without large-scale refactoring |
| III. Comprehensive Logging | ✅ PASS | All portal detection, linking, and coordinate transformation will log at appropriate levels |
| IV. Automatic Testing First | ✅ PASS | Unit tests for coordinate transformation, link creation; Integration tests for teleportation detection |
| V. Modular Architecture | ✅ PASS | New portal linking logic in separate package (nurgling.portal) |
| VI. Documentation & Knowledge Sharing | ✅ PASS | This plan.md, data-model.md, contracts, and quickstart.md will be created |
| VII. AI-Assisted Development | ✅ PASS | AI feedback artifacts will be created for any hallucinations |
| VIII. English Language for Code Artifacts | ✅ PASS | All code comments, commits, documentation in English |

**GATE RESULT**: ✅ PASS - All constitution principles satisfied. Proceeding to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-portal-marker-linking/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── portal-linking-api.md
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/
├── haven/
│   ├── MapWnd.java              # Existing map window - will add linked marker UI
│   └── MiniMap.java             # Existing minimap - may add linked marker indicators
├── nurgling/
│   ├── markers/
│   │   ├── PortalMarker.java           # NEW: Portal marker with link UID
│   │   ├── PortalLink.java             # NEW: Link between two portal markers
│   │   ├── PortalLinkManager.java      # NEW: Manages creation/persistence of links
│   │   └── PortalLinkSaveData.java     # NEW: Save/load link data
│   ├── navigation/
│   │   ├── UnifiedTilePathfinder.java  # EXISTING: Has portal traversal logic
│   │   └── ChunkPortal.java            # EXISTING: Portal data structure
│   ├── overlays/
│   │   └── map/
│   │       └── MinimapPortalLinkRenderer.java  # NEW: Render linked portal indicators
│   ├── teleportation/
│   │   ├── TeleportationDetector.java          # NEW: Detect teleportation events
│   │   ├── LayerTransitionDetector.java        # NEW: Distinguish layer transitions
│   │   └── TeleportationType.java              # NEW: Enum for teleportation types
│   ├── utils/
│   │   └── CoordinateTransformer.java          # NEW: Transform coordinates between layers
│   └── widgets/
│       └── NMapWnd.java              # EXISTING: Nurgling map window extension

tests/
├── unit/
│   └── nurgling/
│       ├── markers/
│       │   ├── PortalLinkTest.java
│       │   └── PortalLinkManagerTest.java
│       ├── teleportation/
│       │   ├── TeleportationDetectorTest.java
│       │   └── LayerTransitionDetectorTest.java
│       └── utils/
│           └── CoordinateTransformerTest.java
└── integration/
    └── nurgling/
        └── PortalLinkingIntegrationTest.java
```

**Structure Decision**: Single project structure with new packages under `src/nurgling/` for portal linking feature. Leverages existing navigation system (`UnifiedTilePathfinder`, `ChunkPortal`) for portal detection.

## Complexity Tracking

No constitution violations. Complexity tracking not required.

---

## Phase 0: Research & Discovery

### Research Tasks

1. **Research existing teleportation detection mechanisms**
   - Analyze `UnifiedTilePathfinder.java` for portal traversal logic
   - Identify how game client handles teleportation events
   - Document existing APIs for detecting hearthfire, totem, signpost teleportation

2. **Research map marker system architecture**
   - Analyze existing marker classes in `nurgling/overlays/`
   - Understand marker save/load mechanism
   - Identify extension points for link UID and direction markers

3. **Research coordinate system and layer management**
   - Analyze how map layers are managed (surface vs underground)
   - Understand coordinate transformation between chunks
   - Document existing `ChunkPortal` data structure

4. **Find best practices for marker persistence**
   - Review existing save file format
   - Identify backward-compatible extension patterns
   - Document migration strategy for existing saves

5. **Research UI integration for linked markers**
   - Analyze `MapWnd.java` and `NMapWnd.java` for marker list UI
   - Identify hooks for marker sorting and grouping
   - Document UI update patterns

### Phase 0 Output

All research findings will be documented in `research.md` with:
- Decision: What approach was chosen
- Rationale: Why chosen over alternatives
- Alternatives considered: What else was evaluated

---

## Phase 1: Design & Contracts

**Prerequisites:** `research.md` complete

### Design Artifacts

1. **data-model.md**: Entity definitions for:
   - `PortalMarker`: Fields (name, type, coordinates, link UID, direction marker)
   - `PortalLink`: Fields (UID, source marker ref, target marker ref, creation timestamp)
   - `PortalLinkSaveData`: Persistence format
   - Validation rules from requirements (6-char UID, IN/OUT markers)

2. **contracts/portal-linking-api.md**: API contracts for:
   - `TeleportationDetector.detectTeleportation()`: Event detection
   - `LayerTransitionDetector.isLayerTransition()`: Type discrimination
   - `PortalLinkManager.createLink()`: Link creation API
   - `CoordinateTransformer.transform()`: Coordinate conversion API

3. **quickstart.md**: Developer guide for:
   - Building the feature
   - Testing portal linking locally
   - Debugging common issues

### Agent Context Update

After Phase 1 design complete, run:
```powershell
.specify/scripts/powershell/update-agent-context.ps1 -AgentType qwen
```

This will add new technologies/patterns from this plan to the Qwen agent context.

---

## Phase 2: Task Breakdown

**Output**: `tasks.md` created by `/speckit.tasks` command

Tasks will be organized by user story (P1-P4) with:
- Phase 1: Setup (project structure, dependencies)
- Phase 2: Foundational (teleportation detection, marker system extension)
- Phase 3+: User Story implementation (US1-US4)
- Final Phase: Polish & cross-cutting concerns

---

## Constitution Check (Post-Design)

*Re-evaluate after Phase 1 design artifacts are complete.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality First | ⏳ PENDING | Will verify after implementation |
| II. Incremental Refactoring | ⏳ PENDING | Will verify refactoring is incremental |
| III. Comprehensive Logging | ⏳ PENDING | Will verify logging in all operations |
| IV. Automatic Testing First | ⏳ PENDING | Will verify test coverage |
| V. Modular Architecture | ⏳ PENDING | Will verify package structure |
| VI. Documentation & Knowledge Sharing | ⏳ PENDING | This file + research.md + data-model.md |
| VII. AI-Assisted Development | ⏳ PENDING | Will create feedback artifacts |
| VIII. English Language for Code Artifacts | ⏳ PENDING | Will verify all artifacts in English |

**Next Action**: Proceed to Phase 2 task breakdown via `/speckit.tasks` command.

---

## Phase 0 & 1 Completion Status

### Phase 0: Research ✅ COMPLETE

All research findings documented in `research.md`:

- ✅ Teleportation detection mechanisms analyzed
- ✅ Map marker system architecture documented
- ✅ Coordinate system and layer management understood
- ✅ Marker persistence best practices identified
- ✅ UI integration points identified

**Key Decisions**:
1. Hook into existing `UnifiedTilePathfinder` for portal detection
2. Create `PortalMarker` wrapper with link metadata
3. Use transition portal as zero reference point for coordinate transformation
4. JSON array format for persistence in save file

---

### Phase 1: Design ✅ COMPLETE

**Artifacts Created**:

| File | Purpose |
|------|---------|
| `data-model.md` | Entity definitions (PortalMarker, PortalLink, PortalLinkManager) |
| `contracts/portal-linking-api.md` | API contracts for all interfaces |
| `quickstart.md` | Developer guide for testing and debugging |
| `research.md` | Research findings and design decisions |

**Agent Context**: ✅ Updated Qwen agent context with new technologies

---

## Constitution Check (Post-Design)

*Re-evaluated after Phase 1 design artifacts complete.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality First | ✅ PASS | Implementation will follow all code quality rules |
| II. Incremental Refactoring | ✅ PASS | Feature adds new packages without large-scale refactoring |
| III. Comprehensive Logging | ✅ PASS | All operations will log at appropriate levels (documented in contracts) |
| IV. Automatic Testing First | ✅ PASS | Unit tests + integration tests planned (documented in contracts) |
| V. Modular Architecture | ✅ PASS | New packages: `nurgling.teleportation`, `nurgling.markers`, `nurgling.utils` |
| VI. Documentation & Knowledge Sharing | ✅ PASS | research.md, data-model.md, contracts, quickstart.md created |
| VII. AI-Assisted Development | ✅ PASS | AI feedback artifacts will be created during implementation |
| VIII. English Language for Code Artifacts | ✅ PASS | All documentation, code comments, commits in English |

**GATE RESULT**: ✅ PASS - All constitution principles satisfied. Ready for Phase 2 task breakdown.

---

## Generated Artifacts Summary

**Documentation**:
- `specs/001-portal-marker-linking/plan.md` (this file)
- `specs/001-portal-marker-linking/research.md`
- `specs/001-portal-marker-linking/data-model.md`
- `specs/001-portal-marker-linking/quickstart.md`
- `specs/001-portal-marker-linking/contracts/portal-linking-api.md`
- `specs/001-portal-marker-linking/checklists/requirements.md`

**Source Code Structure** (to be implemented):
- `src/nurgling/teleportation/` - Teleportation detection
- `src/nurgling/markers/` - Portal marker and link management
- `src/nurgling/utils/` - Coordinate transformation
- `src/nurgling/overlays/map/` - UI rendering

**Tests** (to be implemented):
- `tests/unit/nurgling/teleportation/` - Unit tests for detection
- `tests/unit/nurgling/markers/` - Unit tests for link management
- `tests/unit/nurgling/utils/` - Unit tests for coordinate transformation
- `tests/integration/nurgling/` - End-to-end integration tests
