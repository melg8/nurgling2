# Feature Specification: Cave Marker Adjacent Display in Map Marker List

**Feature Branch**: `001-fix-cave-marker-links`
**Created**: 2026-02-21
**Status**: Draft
**Related Feature**: 001-portal-marker-linking (Portal Marker Linking Between Map Layers)
**Input**: User description: "в данный момент не происходит визуально связывания отображаются все 6 маркеров пещер (3 снаружи 3 внутри) но без связей, лог выдает: [findicons] Found cave passage: gfx/hud/mmap/cave, conf.show=true, conf.getmarkablep()=true, conf.getmarkp()=true [MinimapPortalLinkRenderer] [DEBUG] renderPortalLinkIndicators called [MinimapPortalLinkRenderer] [DEBUG] Render complete: grids=[MinimapPortalLinkRenderer] [ERROR] Error during portal link rendering: [Ljava.lang.Object;@3c17270e [MinimapPortalLinkRenderer] [DEBUG] Stack trace: [Ljava.lang.Object;@1285011b [markobjs] Checking 3 icons нужно найти в чем конкретно заключается проблема и исправить ее добившись полной работоспособности, так же нужно уменьшить количество спама логов и выводить только редко происходящие или интересующие нас события"

## Context

This specification is a **refinement and bug fix** for the existing Portal Marker Linking feature (001-portal-marker-linking). The original feature established linked portal markers between map layers. This task addresses:

1. **Marker list display**: Linked cave markers (3 outside + 3 inside = 6 total) should appear **adjacent** in the map marker list when sorted
2. **Rendering errors**: Fix array-to-string conversion errors in portal link rendering code
3. **Log optimization**: Reduce DEBUG log spam to show only meaningful events

## AGENT WORKFLOW REQUIREMENT

**CRITICAL**: This specification MUST be implemented by delegating ALL work to specialized subagents:
- Use `incremental-developer` agent for ALL code implementation tasks
- Use `qa-verifier` agent for ALL verification and testing tasks
- Use `requirements-manager` agent for completion verification
- After EACH code change, run the speckit cycle: **COMPILE → VERIFY → TEST → FIX**

## User Scenarios & Testing

### User Story 1 - Linked Cave Markers Appear Adjacent in Map Marker List (Priority: P1)

As a player viewing the map marker list, I want linked cave markers (entrance on surface level 0 and exit on underground level 1) to appear next to each other when the list is sorted, so that I can easily identify which cave entrance connects to which exit.

**Why this priority**: This is the core functionality from 001-portal-marker-linking that is currently broken. The original spec (FR-011) requires that "linked markers appear adjacent in alphabetical order due to shared name and UID." Currently all 6 markers (3 outside, 3 inside) display but without proper adjacency, making it difficult to understand cave connections.

**Independent Test**: Player opens the map marker list with 6 active cave markers (3 pairs). When sorted alphabetically, each entrance marker should appear directly next to its corresponding exit marker.

**Acceptance Scenarios**:

1. **Given** player has 3 linked cave portal pairs (6 markers total: 3 on level 0, 3 on level 1), **When** player opens the map marker list and sorts alphabetically, **Then** each entrance marker appears adjacent to its corresponding exit marker due to shared name and UID
2. **Given** linked markers share the same name (Cave) and 6-character UID, **When** the marker list is sorted, **Then** markers with identical name+UID appear consecutively in the list
3. **Given** player clicks on an entrance marker in the list, **When** player scrolls to find the linked exit marker, **Then** the exit marker is immediately visible next to or near the entrance marker

---

### User Story 2 - Error-Free Portal Link Rendering (Priority: P2)

As a player using the map marker system, I want the portal link rendering to complete without errors, so that linked markers function reliably without log pollution.

**Why this priority**: The current error in portal link rendering (`[Ljava.lang.Object;@3c17270e` - array object hash code instead of readable data) indicates a bug that may interfere with proper marker list sorting and link functionality.

**Independent Test**: Monitor game logs while viewing the map with cave markers active. Portal link rendering should complete without ERROR level messages containing object hash codes.

**Acceptance Scenarios**:

1. **Given** the portal link renderer processes grid data for cave marker links, **When** rendering occurs, **Then** no array-to-string conversion errors appear in logs (no patterns like `[Ljava.lang.Object;@`)
2. **Given** an error occurs during portal link rendering, **When** the system logs the error, **Then** the message contains human-readable information about what failed (not object memory references)
3. **Given** the renderPortalLinkIndicators function completes, **When** logging render status, **Then** grid data displays as readable content (not object hash codes)

---

### User Story 3 - Reduced Log Spam with Meaningful Messages (Priority: P3)

As a developer or advanced user reviewing game logs, I want to see only important and infrequently occurring events, so that I can quickly identify actual issues without sifting through repetitive debug messages.

**Why this priority**: Current log output shows repetitive DEBUG messages every render cycle (findicons, MinimapPortalLinkRenderer, markobjs), making it difficult to identify genuine problems. This is a refinement of the logging behavior from 001-portal-marker-linking.

**Independent Test**: Play the game with cave markers active for 10 minutes. Log file should contain only essential events (errors, warnings, rare state changes) without repetitive DEBUG messages appearing every frame.

**Acceptance Scenarios**:

1. **Given** the map is displaying cave markers with links, **When** the game runs for 5 minutes, **Then** logs do not contain repetitive DEBUG messages from findicons, MinimapPortalLinkRenderer, or markobjs appearing more than once per second
2. **Given** a cave marker is found or processed, **When** this is a routine event, **Then** no log entry is created OR a single summary entry is created at INFO level (not DEBUG)
3. **Given** an error occurs during portal link rendering, **When** the error is logged, **Then** the message is meaningful (not object hash codes like `[Ljava.lang.Object;@3c17270e`)
4. **Given** the render complete event fires every frame, **When** this is routine operation, **Then** no DEBUG log entry is created (only log on first render or state change)

---

### Edge Cases

- **Partial marker visibility**: What happens when only some cave markers are visible (e.g., 2 outside, 1 inside)? The system should display available markers with correct adjacency for linked pairs.
- **Marker deletion**: What happens when player deletes one marker from a linked pair? The remaining marker should still display correctly, and the link should be recreated on portal revisit (per 001-portal-marker-linking FR-010).
- **Multiple cave systems**: How does the system behave when 3+ cave systems are active? Each linked pair should maintain adjacency without cross-linking between different caves.
- **Empty or null grid data**: What happens when grid data array is empty? The system should handle gracefully with meaningful error message, not object hash code.
- **Rapid layer transitions**: How does the system handle quick transitions between indoor/outdoor? Marker list should update smoothly without log spam or rendering errors.

## Requirements

### Functional Requirements

- **FR-001**: System MUST display all 6 cave markers (3 outdoor entrance markers on level 0, 3 indoor exit markers on level 1) in the map marker list
- **FR-002**: System MUST ensure linked cave markers (entrance + exit pair sharing UID) appear adjacent in the marker list when sorted alphabetically
- **FR-003**: System MUST assign identical names ("Cave") and shared 6-character UID to linked marker pairs (as per 001-portal-marker-linking FR-008, FR-002)
- **FR-004**: System MUST complete portal link rendering without errors during normal operation when cave markers are present
- **FR-005**: System MUST display meaningful error messages in logs when rendering failures occur (not object memory references or hash codes)
- **FR-006**: System MUST suppress routine DEBUG level log messages that occur every frame or render cycle
- **FR-007**: System MUST log only infrequent events (state changes, first-time occurrences) and error conditions at appropriate log levels
- **FR-008**: System MUST handle array and grid data properly without toString() conversion errors that produce object hash codes
- **FR-009**: System MUST preserve the linked marker adjacency behavior across map reloads and layer transitions
- **FR-010**: System MUST maintain compatibility with existing 001-portal-marker-linking functionality (automatic marker creation, UID persistence, marker recreation)

### Key Entities

- **Cave Marker**: A map marker representing a cave entrance (level 0, outdoor) or cave exit (level 1, indoor) location
- **Linked Marker Pair**: Two cave markers (entrance + exit) connected by a shared unique identifier (6-character UID) and identical name
- **Map Marker List**: The UI component displaying all active map markers in sorted order (alphabetical by default)
- **Portal Link**: The logical association between paired cave markers on different map layers (from 001-portal-marker-linking)
- **Grid Data**: Spatial information used by the rendering system to determine marker positions and link relationships

## Success Criteria

### Measurable Outcomes

- **SC-001**: All 6 cave markers (3 outdoor, 3 indoor) display in the marker list with linked pairs appearing adjacent 100% of the time during gameplay testing
- **SC-002**: Zero ERROR level log entries containing object hash codes (pattern: `[Ljava.lang.Object;@`) during 30 minutes of gameplay with cave markers active
- **SC-003**: DEBUG level log messages from MinimapPortalLinkRenderer, findicons, and markobjs reduced by at least 90% compared to current behavior (measured by log line count over 10 minute test period)
- **SC-004**: Portal link rendering completes successfully in 99% of render cycles when cave markers are present (measured over 1000 render cycles)
- **SC-005**: All error messages in logs contain human-readable descriptions of the failure condition (verified by manual review of 20 test runs with induced errors)
- **SC-006**: Linked marker adjacency is preserved across 100% of map reload and layer transition events (measured over 50 test cycles)
- **SC-007**: Log file size growth rate reduced by at least 75% during cave marker usage scenarios (comparing 10 minute gameplay sessions before and after fix)
