# Tasks: Fix Cave Marker Adjacent Display in Map Marker List

**Input**: Design documents from `/specs/001-fix-cave-marker-links/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are OPTIONAL - this feature includes test tasks for verification of marker adjacency, rendering errors, and log optimization.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## AGENT WORKFLOW REQUIREMENT

**CRITICAL**: Each task in this file MUST be executed following the agent-workflow.md cycle:

1. **Delegate to `incremental-developer`** agent with the task description
2. **Wait** for completion with status `STATUS: ATOMIC_STEP_COMPLETE`
3. **Delegate to `qa-verifier`** agent for verification
4. **If RESULT: FAILED** → Return to step 1 with error details
5. **If RESULT: PASSED** → Mark task as [X] and proceed to next task

**After EACH code change within a task:**
- Run build command: `ant bin`
- Run test command: `ant test`
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

**Purpose**: Project initialization and build verification

- [ ] T001 Verify Java 21 (LTS) is installed: run `java -version` in project root (no file changes)
- [ ] T002 Verify Ant build system works: run `ant -version` and `ant bin` in project root (no file changes)
- [ ] T003 [P] Review existing logging configuration in build/ directory and src/resources/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T004 [P] Review current MiniMap.java structure (lines 600-900) for findicons() and markobjs() methods in src/haven/MiniMap.java
- [ ] T005 [P] Review MinimapPortalLinkRenderer.java for current logging implementation in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java
- [ ] T006 [P] Identify all System.out.printf logging locations in src/haven/MiniMap.java and src/nurgling/overlays/map/MinimapPortalLinkRenderer.java
- [ ] T007 Create helper utility class for UID extraction from DisplayIcon in src/nurgling/util/MarkerUidHelper.java
- [ ] T008 [P] Setup state tracking variables for log throttling: add to src/haven/MiniMap.java and src/nurgling/overlays/map/MinimapPortalLinkRenderer.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Linked Cave Markers Appear Adjacent in Map Marker List (Priority: P1) 🎯 MVP

**Goal**: Modify marker sorting to ensure linked cave markers (entrance + exit pairs) appear adjacent in the marker list when sorted

**Independent Test**: Player opens the map marker list with 6 active cave markers (3 pairs). When sorted, each entrance marker appears directly next to its corresponding exit marker.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T009 [P] [US1] Create unit test for marker sort comparator in tests/nurgling/markers/MarkerSortComparatorTest.java
- [ ] T010 [P] [US1] Create integration test for marker adjacency in tests/nurgling/integration/MarkerAdjacencyTest.java

### Implementation for User Story 1

- [ ] T011 [P] [US1] Implement getLinkUid() helper method in src/nurgling/util/MarkerUidHelper.java
- [ ] T012 [P] [US1] Create MarkerSortComparator class implementing Comparator<DisplayIcon> in src/nurgling/markers/MarkerSortComparator.java
- [ ] T013 [US1] Update MiniMap.findicons() to use MarkerSortComparator instead of simple z-order sort (src/haven/MiniMap.java, line ~641)
- [ ] T014 [US1] Add validation logging for marker pair integrity in src/nurgling/util/MarkerUidHelper.java (warn if UID mismatch detected)
- [ ] T015 [US1] Test marker adjacency with 3+ cave pairs manually per specs/001-fix-cave-marker-links/quickstart.md

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Error-Free Portal Link Rendering (Priority: P2)

**Goal**: Fix array-to-string conversion errors in portal link rendering so logs contain human-readable error messages

**Independent Test**: Monitor game logs while viewing the map with cave markers active. Portal link rendering should complete without ERROR level messages containing object hash codes (pattern: `[Ljava.lang.Object;@`).

### Tests for User Story 2 ⚠️

- [ ] T016 [P] [US2] Create unit test for array formatting in tests/nurgling/overlays/map/MinimapPortalLinkRendererTest.java
- [ ] T017 [P] [US2] Create log output verification test in tests/nurgling/integration/LogOutputVerificationTest.java

### Implementation for User Story 2

- [ ] T018 [P] [US2] Fix dlog() calls in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java to use Arrays.toString() for any array/collection logging
- [ ] T019 [US2] Update error logging in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java to use e.getClass().getSimpleName() + ": " + e.getMessage() instead of e.getMessage() alone
- [ ] T020 [US2] Add null/empty checks for display grid array before logging in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java renderPortalLinkIndicators()
- [ ] T021 [US2] Verify grid data logging in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java uses proper formatting (not direct object printing)
- [ ] T022 [US2] Run 30-minute gameplay test and verify zero `[Ljava.lang.Object;@` patterns in nurgling.log

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Reduced Log Spam with Meaningful Messages (Priority: P3)

**Goal**: Suppress per-frame DEBUG logging and log only state changes and infrequent events

**Independent Test**: Play the game with cave markers active for 10 minutes. Log file should contain only essential events without repetitive DEBUG messages appearing every frame.

### Tests for User Story 3 ⚠️

- [ ] T023 [P] [US3] Create log spam measurement test in tests/nurgling/integration/LogSpamMeasurementTest.java
- [ ] T024 [P] [US3] Create baseline log count (before fix) by counting lines in nurgling_markobjs.log

### Implementation for User Story 3

- [ ] T025 [P] [US3] Add loggedIcons Set to src/haven/MiniMap.java for tracking first-time cave discoveries
- [ ] T026 [US3] Modify src/haven/MiniMap.java findicons() method to log cave passage discovery only ONCE (first discovery)
- [ ] T027 [US3] Add prevMarkerCount state variable to src/nurgling/overlays/map/MinimapPortalLinkRenderer.java
- [ ] T028 [US3] Modify src/nurgling/overlays/map/MinimapPortalLinkRenderer.java renderPortalLinkIndicators() to log only on state change (marker count changed)
- [ ] T029 [US3] Remove or throttle per-frame DEBUG logs in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java (renderPortalLinkIndicators called, render complete every frame)
- [ ] T030 [US3] Modify src/haven/MiniMap.java markobjs() method to log only on state changes (not every call)
- [ ] T031 [US3] Run 5-minute gameplay test and measure log line count reduction in nurgling_markobjs.log (target: 90% DEBUG reduction)
- [ ] T032 [US3] Verify log file growth rate reduced by ≥75% in nurgling.log (compare file sizes before/after)

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T033 [P] Update specs/001-fix-cave-marker-links/quickstart.md with actual test results and measurements
- [ ] T034 [P] Create test report in specs/001-fix-cave-marker-links/test-report.md
- [ ] T035 Code cleanup - remove temporary debug logging added during development in src/haven/MiniMap.java and src/nurgling/overlays/map/MinimapPortalLinkRenderer.java
- [ ] T036 [P] Run full build: `ant clean build` in project root (no file changes)
- [ ] T037 [P] Run all tests: `ant test` in project root (no file changes)
- [ ] T038 [P] Update commit message with feature summary per git workflow (no file changes)
- [ ] T039 Run speckit requirements-manager to verify all spec requirements met in specs/001-fix-cave-marker-links/spec.md (no file changes)
- [ ] T040 Create commit with all changes per git workflow in project root (no file changes)

---

## Additional Coverage Tasks (FR-009, FR-010, SC-004, SC-006)

**Purpose**: Cover missing functional requirements and success criteria

- [ ] T041 [P] [US1] Test marker adjacency preservation across 50 map reload/layer transition cycles in src/haven/MiniMap.java (covers FR-009, SC-006)
- [ ] T042 [P] [US2] Verify backward compatibility with 001-portal-marker-linking functionality by testing existing marker persistence in src/haven/MiniMap.java and src/nurgling/overlays/map/MinimapPortalLinkRenderer.java (covers FR-010)
- [ ] T043 [P] [US2] Measure portal link render success rate over 1000 render cycles in src/nurgling/overlays/map/MinimapPortalLinkRenderer.java (target: 99%) (covers SC-004)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - **BLOCKS all user stories**
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Independent of US1
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Independent of US1/US2

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Helper utilities before main implementation
- Core implementation before integration testing
- Story complete before moving to next priority

### Parallel Opportunities

**Phase 1 (Setup)**:
- T001, T002, T003 can all run in parallel (different verification tasks)

**Phase 2 (Foundational)**:
- T004, T005, T006 can run in parallel (code review tasks)
- T007 (helper utility) should complete before T011 (US1 implementation)

**Phase 3 (US1)**:
- T009, T010 (tests) can run in parallel
- T011, T012 (helper + comparator) can run in parallel
- T013 depends on T011, T012
- T014, T015 depend on T013

**Phase 4 (US2)**:
- T016, T017 (tests) can run in parallel
- T018, T019, T020, T021 can run in parallel (different logging fixes)
- T022 depends on T018-T021

**Phase 5 (US3)**:
- T023, T024 (tests) can run in parallel
- T025, T027 (state tracking setup) can run in parallel
- T026 depends on T025
- T028, T029, T030 can run in parallel (log optimization in different methods)
- T031, T032 depend on T026, T028-T030

**Phase 6 (Polish)**:
- T033, T034, T036, T037, T038 can all run in parallel
- T039, T040 are sequential (requirements-manager before commit)

**Additional Coverage**:
- T041, T042, T043 can all run in parallel (independent verification tasks)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task T009: "Create unit test for marker sort comparator in tests/nurgling/markers/MarkerSortComparatorTest.java"
Task T010: "Create integration test for marker adjacency in tests/nurgling/integration/MarkerAdjacencyTest.java"

# Launch all models/utilities for User Story 1 together:
Task T011: "Implement getLinkUid() helper method in src/nurgling/util/MarkerUidHelper.java"
Task T012: "Create MarkerSortComparator class in src/nurgling/markers/MarkerSortComparator.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test marker adjacency independently per quickstart.md
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 (marker adjacency) → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 (error-free rendering) → Test independently → Deploy/Demo
4. Add User Story 3 (log optimization) → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (marker adjacency)
   - Developer B: User Story 2 (rendering errors)
   - Developer C: User Story 3 (log optimization)
3. Stories complete and integrate independently

---

## Task Summary

| Phase | Description | Task Count |
|-------|-------------|------------|
| Phase 1 | Setup | 3 |
| Phase 2 | Foundational | 5 |
| Phase 3 | User Story 1 (P1) | 7 |
| Phase 4 | User Story 2 (P2) | 7 |
| Phase 5 | User Story 3 (P3) | 10 |
| Phase 6 | Polish | 8 |
| Additional Coverage | FR-009, FR-010, SC-004, SC-006 | 3 |
| **Total** | **All phases** | **43** |

---

## Notes

- [P] tasks = different files, no dependencies - can run in parallel
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Requirements Coverage Matrix

### Functional Requirements Coverage

| FR | Description | Covered By Tasks |
|----|-------------|------------------|
| FR-001 | Display all 6 cave markers | T015 (test marker adjacency with 3+ pairs) |
| FR-002 | Linked markers appear adjacent | T009, T010, T012, T013, T014, T015 |
| FR-003 | Identical names and shared UID | T007, T011 (MarkerUidHelper), T012 (comparator) |
| FR-004 | Portal link rendering without errors | T016, T018, T020, T022 |
| FR-005 | Meaningful error messages | T017, T019, T021 |
| FR-006 | Suppress DEBUG level messages | T023, T026, T028, T029, T030, T031 |
| FR-007 | Log only infrequent events | T025, T026, T027, T028, T030 |
| FR-008 | Handle array/grid data properly | T016, T018, T020, T021 |
| FR-009 | Preserve adjacency across reloads | T041 |
| FR-010 | Maintain compatibility | T042 |

### Success Criteria Coverage

| SC | Description | Covered By Tasks |
|----|-------------|------------------|
| SC-001 | 100% adjacency rate | T010, T015, T041 |
| SC-002 | Zero `[Ljava.lang.Object;@` in 30 min | T017, T022 |
| SC-003 | 90% DEBUG reduction | T023, T031 |
| SC-004 | 99% render success rate | T043 |
| SC-005 | Human-readable error messages | T017, T019 |
| SC-006 | Adjacency preserved across 100% reloads | T041 |
| SC-007 | 75% log file growth reduction | T032 |
