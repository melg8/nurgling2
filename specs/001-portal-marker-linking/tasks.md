# Tasks: Portal Marker Linking Between Map Layers

**Input**: Design documents from `/specs/001-portal-marker-linking/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are REQUIRED for this feature to ensure teleportation detection and coordinate transformation work correctly.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## AGENT WORKFLOW REQUIREMENT

**CRITICAL**: Each task in this file MUST be executed following the agent-workflow.md cycle:

1. **Delegate to `incremental-developer`** agent with the task description
2. **Wait** for completion with status `STATUS: ATOMIC_STEP_COMPLETE`
3. **Delegate to `qa-verifier`** agent for verification
4. **If RESULT: FAILED** → Return to step 1 with error details
5. **If RESULT: PASSED** → Mark task as [X] and proceed to next task

**After EACH code change within a task:**
- Run build command (e.g., `ant bin`)
- Run test command (e.g., `ant test`)
- Fix ALL errors before proceeding

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- Paths shown below assume single project structure per plan.md

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure for portal linking feature

- [X] T001 Create package structure: `src/nurgling/teleportation/`, `src/nurgling/markers/`, `src/nurgling/utils/`, `src/nurgling/overlays/map/`
- [X] T002 [P] Create test package structure: `tests/unit/nurgling/teleportation/`, `tests/unit/nurgling/markers/`, `tests/unit/nurgling/utils/`, `tests/integration/nurgling/`
- [X] T003 [P] Verify JUnit 5 and Mockito dependencies in build configuration

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 [P] Create `TeleportationType` enum in `src/nurgling/teleportation/TeleportationType.java` with values: PORTAL_LAYER_TRANSITION, PORTAL_SAME_LAYER, HEARTHFIRE, VILLAGE_TOTEM, SIGNPOST, UNKNOWN
- [X] T005 [P] Create `TeleportationEvent` class in `src/nurgling/teleportation/TeleportationEvent.java` with fields: type, sourceGridId, sourceCoord, targetGridId, targetCoord, timestamp, portalGobName
- [X] T006 [P] Create `TeleportationDetector` interface in `src/nurgling/teleportation/TeleportationDetector.java` with methods: onTeleportation(), getLastEvent(), clearEvent()
- [X] T007 [P] Create `LayerTransitionDetector` interface in `src/nurgling/teleportation/LayerTransitionDetector.java` with methods: isLayerTransition(), getTransitionDirection(), getPortalType()
- [X] T008 [P] Create `CoordinateTransformer` interface in `src/nurgling/utils/CoordinateTransformer.java` with methods: transformCoordinate(), calculateOffset(), applyOffset()
- [X] T009 [P] Create `PortalType` enum in `src/nurgling/markers/PortalType.java` with values: CAVE, MINEHOLE, LADDER (extend existing ChunkPortal.PortalType if needed)
- [X] T010 [P] Create `Direction` enum in `src/nurgling/markers/Direction.java` with values: IN, OUT

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Automatic Creation of Linked Portal Markers on Layer Transition (Priority: P1) 🎯 MVP

**Goal**: Automatically detect layer transitions via portal and create linked marker pairs with shared 6-character UID on both source and destination layers

**Independent Test**: Player can enter a cave on surface level (0), teleport to underground level (1), and observe two linked markers with matching UID in the map marker list - one on each layer

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T011 [P] [US1] Unit test for `CoordinateTransformer.calculateOffset()` in `tests/unit/nurgling/utils/CoordinateTransformerTest.java`
- [X] T012 [P] [US1] Unit test for `CoordinateTransformer.transformCoordinate()` in `tests/unit/nurgling/utils/CoordinateTransformerTest.java`
- [X] T013 [P] [US1] Unit test for `CoordinateTransformer.applyOffset()` in `tests/unit/nurgling/utils/CoordinateTransformerTest.java`
- [X] T014 [P] [US1] Unit test for UID generation (6-char alphanumeric) in `tests/unit/nurgling/markers/PortalLinkManagerTest.java`
- [X] T015 [US1] Integration test for layer transition detection (cave surface→underground) in `tests/integration/nurgling/PortalLinkingIntegrationTest.java`

### Implementation for User Story 1

- [X] T016 [P] [US1] Implement `CoordinateTransformer` class in `src/nurgling/utils/CoordinateTransformer.java` with transformation formula: offset = markerCoord - portalCoord, target = targetPortal + offset
- [X] T017 [P] [US1] Create `PortalMarker` class in `src/nurgling/markers/PortalMarker.java` with fields: name, type, gridId, coord, linkUid, direction, layer, createdTimestamp, icon
- [X] T018 [P] [US1] Create `PortalLink` class in `src/nurgling/markers/PortalLink.java` with fields: linkUid, sourceMarker, targetMarker, sourceLayer, targetLayer, createdTimestamp, lastAccessedTimestamp
- [X] T019 [P] [US1] Create `PortalLinkSaveData` class in `src/nurgling/markers/PortalLinkSaveData.java` for JSON serialization with fields matching data-model.md schema
- [X] T020 [US1] Implement `TeleportationDetector` class in `src/nurgling/teleportation/TeleportationDetector.java` to detect discontinuous position changes (>10 tiles) and classify teleportation type
- [X] T021 [US1] Implement `LayerTransitionDetector` class in `src/nurgling/teleportation/LayerTransitionDetector.java` with decision logic: isLayerTransition() checks PORTAL_LAYER_TRANSITION type, different gridId, and portal type (CAVE/MINEHOLE/LADDER)
- [X] T022 [US1] Implement `PortalLinkManager` class in `src/nurgling/markers/PortalLinkManager.java` with methods: createLink(), getLinkedMarkers(), getLinkByUid(), processTeleportation()
- [X] T023 [US1] Implement UID generation method in `PortalLinkManager` (6-character alphanumeric 0-9a-zA-Z with collision check)
- [X] T024 [US1] Implement `createLink()` method in `PortalLinkManager` with validation: markers on adjacent layers, assign linkUid, set direction (OUT for source, IN for target)
- [X] T025 [US1] Implement `processTeleportation()` method in `PortalLinkManager` to handle layer transition events and create linked markers
- [X] T026 [US1] Implement coordinate transformation for markers in `PortalLinkManager.processTeleportation()` using `CoordinateTransformer`
- [X] T027 [US1] Add SLF4J logging to all User Story 1 operations (teleportation detection, link creation, coordinate transformation)
- [X] T028 [US1] Implement marker creation on target layer when marker doesn't exist at transformed coordinate

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently - player can transition through cave and see linked markers created

---

## Phase 4: User Story 2 - Quick Switching Between Linked Markers via Map Interface (Priority: P2)

**Goal**: Enable player to use map marker list to quickly toggle between linked portals on different layers by clicking markers

**Independent Test**: Player can click on a level 0 portal marker in the list, then click the linked level 1 marker, and see map center on each marker's location

### Tests for User Story 2 ⚠️

- [X] T029 [P] [US2] Unit test for marker list sorting (linked markers grouped together) in `tests/unit/nurgling/markers/PortalLinkManagerTest.java`
- [X] T030 [US2] Integration test for marker click → map center action in `tests/integration/nurgling/PortalLinkingIntegrationTest.java`

### Implementation for User Story 2

- [X] T031 [P] [US2] Extend `MapWnd.getSortedMarkers()` in `src/haven/MapWnd.java` with comparator: primary by name, secondary by linkUid, tertiary by direction
- [X] T032 [US2] Implement marker click handler in `MapWnd` to center map on clicked `PortalMarker` location
- [X] T033 [US2] Add visual indicator for linked markers (IN/OUT icon overlay) in `src/nurgling/overlays/map/MinimapPortalLinkRenderer.java`
- [X] T034 [US2] Implement `getLinkedMarkers()` method in `PortalLinkManager` with O(1) HashMap lookup
- [X] T035 [US2] Add SLF4J logging for User Story 2 operations (marker sorting, map centering)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently - player can create linked markers and switch between them via UI

---

## Phase 5: User Story 3 - Automatic Discovery and Linking of All Portals During Exploration (Priority: P3)

**Goal**: Automatically track all portals visited during exploration and create cross-layer links as player explores multiple portals

**Independent Test**: Player can visit multiple surface portals and their corresponding underground portals, and the system automatically creates links for all visited pairs

### Tests for User Story 3 ⚠️

- [X] T036 [P] [US3] Unit test for multiple link creation with unique UIDs in `tests/unit/nurgling/markers/PortalLinkManagerTest.java`
- [X] T037 [P] [US3] Unit test for marker recreation after deletion in `tests/unit/nurgling/markers/PortalLinkManagerTest.java`
- [X] T038 [US3] Integration test for dungeon level handling (level 2+) in `tests/integration/nurgling/PortalLinkingIntegrationTest.java`

### Implementation for User Story 3

- [X] T039 [P] [US3] Implement `saveLinks()` method in `PortalLinkManager` to persist links to JSON array in save file with key "portalLinks"
- [X] T040 [P] [US3] Implement `loadLinks()` method in `PortalLinkManager` to load links from save file with backward compatibility (initialize empty array if key absent)
- [X] T041 [US3] Implement `recreateMarker()` method in `PortalLinkManager` to recreate deleted markers with preserved UID on portal revisit
- [X] T042 [US3] Extend `LayerTransitionDetector` to handle dungeon levels 2+ (minehole/ladder between underground levels)
- [X] T043 [US3] Implement validation in `PortalLink` that sourceLayer and targetLayer differ by exactly 1 (adjacent levels only)
- [X] T044 [US3] Add SLF4J logging for User Story 3 operations (save, load, marker recreation, multi-level handling)

**Checkpoint**: All user stories should now be independently functional - player can explore multiple portals with automatic linking and persistence

---

## Phase 6: User Story 4 - Distinguishing Layer Transitions from Other Teleportation Types (Priority: P4)

**Goal**: Automatically determine whether teleportation is a layer transition (via portal) or another type of movement (hearthfire bone, village totem, signpost fast travel)

**Independent Test**: Player can use hearthfire or totem teleportation, and the system will not create linked portal markers

### Tests for User Story 4 ⚠️

- [X] T045 [P] [US4] Unit test for hearthfire teleportation non-detection in `tests/unit/nurgling/teleportation/LayerTransitionDetectorTest.java`
- [X] T046 [P] [US4] Unit test for village totem teleportation non-detection in `tests/unit/nurgling/teleportation/LayerTransitionDetectorTest.java`
- [X] T047 [P] [US4] Unit test for signpost fast travel non-detection in `tests/unit/nurgling/teleportation/LayerTransitionDetectorTest.java`
- [X] T048 [US4] Integration test for portal layer transition detection (cave surface→underground) in `tests/integration/nurgling/PortalLinkingIntegrationTest.java`

### Implementation for User Story 4

- [X] T049 [US4] Extend `TeleportationDetector` to classify hearthfire teleportation (check for hearthfire UI interaction)
- [X] T050 [US4] Extend `TeleportationDetector` to classify village totem teleportation (check for totem UI interaction)
- [X] T051 [US4] Extend `TeleportationDetector` to classify signpost fast travel (check for signpost UI interaction)
- [X] T052 [US4] Extend `LayerTransitionDetector.isLayerTransition()` to return false for HEARTHFIRE, VILLAGE_TOTEM, SIGNPOST types
- [X] T053 [US4] Add SLF4J logging for User Story 4 operations (teleportation classification, false positive prevention)

**Checkpoint**: All user stories complete - system correctly distinguishes layer transitions from other teleportation types

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T054 [P] Update `quickstart.md` with build instructions, testing workflow, and debugging guide
- [X] T055 [P] Add Javadoc comments to all public classes and methods in `nurgling.teleportation`, `nurgling.markers`, `nurgling.utils` packages
- [X] T056 [P] Run code cleanup and refactoring (remove duplicates, improve naming)
- [X] T057 [P] Run full test suite: `ant test` and fix any failures
- [X] T058 [P] Verify backward compatibility: load existing save files without "portalLinks" key
- [X] T059 [P] Performance test: verify marker creation within 2 seconds of layer transition
- [X] T060 [P] Verify all logging uses appropriate levels (INFO for normal operations, WARN for recoverable issues, ERROR for unrecoverable)
- [X] T061 [P] Run `ant bin` and verify no compile errors or warnings

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3 → P4)
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 marker creation for full functionality
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Depends on US1 link creation
- **User Story 4 (P4)**: Can start after Foundational (Phase 2) - Independent but enhances US1-3

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

**Phase 1 (Setup)**:
- T001, T002, T003 can all run in parallel

**Phase 2 (Foundational)**:
- T004, T005, T006, T007, T008, T009, T010 can all run in parallel (different files)

**Phase 3 (User Story 1)**:
- Tests T011, T012, T013, T014, T015 can run in parallel
- Models T017, T018, T019 can run in parallel
- T016 (CoordinateTransformer) can run parallel with T020 (TeleportationDetector)
- T020, T021 (detectors) can run in parallel
- T022-T028 must follow sequence: PortalLinkManager creation → methods → integration

**Phase 4 (User Story 2)**:
- T029 (test) and T031 (sorting) can run in parallel
- T032, T033, T034 can run in parallel (different files)

**Phase 5 (User Story 3)**:
- T036, T037, T038 (tests) can run in parallel
- T039 (save) and T040 (load) can run in parallel
- T041, T042, T043 can run in parallel

**Phase 6 (User Story 4)**:
- T045, T046, T047, T048 (tests) can run in parallel
- T049, T050, T051 (teleportation classification) can run in parallel

**Phase 7 (Polish)**:
- All tasks can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test for CoordinateTransformer.calculateOffset() in tests/unit/nurgling/utils/CoordinateTransformerTest.java"
Task: "Unit test for CoordinateTransformer.transformCoordinate() in tests/unit/nurgling/utils/CoordinateTransformerTest.java"
Task: "Unit test for CoordinateTransformer.applyOffset() in tests/unit/nurgling/utils/CoordinateTransformerTest.java"
Task: "Unit test for UID generation (6-char alphanumeric) in tests/unit/nurgling/markers/PortalLinkManagerTest.java"

# Launch all models for User Story 1 together:
Task: "Create PortalMarker class in src/nurgling/markers/PortalMarker.java"
Task: "Create PortalLink class in src/nurgling/markers/PortalLink.java"
Task: "Create PortalLinkSaveData class in src/nurgling/markers/PortalLinkSaveData.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
   - Player enters cave from surface
   - Verify two linked markers created with matching UID
   - Run integration test: `tests/integration/nurgling/PortalLinkingIntegrationTest.java`
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo (UI switching)
4. Add User Story 3 → Test independently → Deploy/Demo (persistence, multi-portal)
5. Add User Story 4 → Test independently → Deploy/Demo (teleportation discrimination)
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (core linking)
   - Developer B: User Story 2 (UI integration)
   - Developer C: User Story 3 (persistence)
   - Developer D: User Story 4 (teleportation classification)
3. Stories complete and integrate independently
4. Team reconvenes for Phase 7 (Polish)

---

## Task Summary

| Phase | Description | Task Count |
|-------|-------------|------------|
| Phase 1 | Setup | 3 |
| Phase 2 | Foundational | 7 |
| Phase 3 | User Story 1 (P1 - MVP) | 18 (5 tests + 13 implementation) |
| Phase 4 | User Story 2 (P2) | 7 (2 tests + 5 implementation) |
| Phase 5 | User Story 3 (P3) | 9 (3 tests + 6 implementation) |
| Phase 6 | User Story 4 (P4) | 9 (4 tests + 5 implementation) |
| Phase 7 | Polish | 8 |
| **Total** | | **61 tasks** |

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All file paths are absolute from repository root: `E:\work\nurgling2\`
- Build command: `ant bin`
- Test command: `ant test`

---

## Format Validation Checklist

✅ All tasks follow format: `- [ ] [TaskID] [P?] [Story?] Description with file path`
✅ Task IDs sequential (T001-T061) in execution order
✅ [P] marker included only for parallelizable tasks
✅ [Story] label (US1-US4) included for user story phase tasks only
✅ Setup phase (T001-T003): NO story label
✅ Foundational phase (T004-T010): NO story label
✅ User Story phases (T011-T053): HAVE story labels
✅ Polish phase (T054-T061): NO story label
✅ All tasks include exact file paths
