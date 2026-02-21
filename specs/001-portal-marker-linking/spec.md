# Feature Specification: Portal Marker Linking Between Map Layers

**Feature Branch**: `001-portal-marker-linking`
**Created**: 2026-02-21
**Status**: Draft
**Input**: User description: "Связывание маркеров порталов между слоями карты при переходе через портал"

## User Scenarios & Testing

### User Story 1 - Automatic Creation of Linked Portal Markers on Layer Transition (Priority: P1)

Player travels through a portal (cave, minehole, or ladder) from one map layer to another. The system automatically detects the layer transition and creates linked marker pairs with a shared unique identifier on both the source and destination layers.

**Why this priority**: This is the foundational capability without which the entire linking system cannot function. It establishes the core mechanism for all other user stories.

**Independent Test**: Player can enter a cave on surface level (0), teleport to underground level (1), and observe two linked markers with matching UID in the map marker list - one on each layer.

**Acceptance Scenarios**:

1. **Given** player is adjacent to a cave portal on level 0, **When** player interacts with the portal and teleports to level 1, **Then** system creates two linked markers with shared 6-character UID (one on level 0 with OUT marker, one on level 1 with IN marker)
2. **Given** player has transitioned through a portal between layers, **When** system detects the layer transition, **Then** portal markers receive a unique 6-character identifier (0-9a-zA-Z)
3. **Given** linked markers are created, **When** player opens the map marker list, **Then** linked markers appear adjacent in alphabetical order due to shared name and UID

---

### User Story 2 - Quick Switching Between Linked Markers via Map Interface (Priority: P2)

Player uses the map marker list to quickly toggle between linked portals on different layers. Clicking a marker in the list centers the map on that marker's location.

**Why this priority**: This delivers the primary user value - the ability to rapidly analyze map layer overlays. Enables effective planning and coordination between layers.

**Independent Test**: Player can click on a level 0 portal marker in the list, then click the linked level 1 marker, and see what lies beneath the surface at that location.

**Acceptance Scenarios**:

1. **Given** player has two linked portal markers (level 0 and level 1), **When** player clicks the first marker in the list, **Then** map centers on the level 0 marker
2. **Given** map is centered on level 0 marker, **When** player clicks the linked level 1 marker in the list, **Then** map switches to layer 1 and centers on the corresponding marker
3. **Given** linked markers in the list, **When** list is sorted alphabetically, **Then** linked markers appear adjacent due to shared name and UID

---

### User Story 3 - Automatic Discovery and Linking of All Portals During Exploration (Priority: P3)

Player explores the world, visiting various portals on surface and underground layers. The system automatically tracks all portals and creates cross-layer links as the player explores.

**Why this priority**: Extends basic functionality to multiple portals. Allows gradual construction of a complete linked transition map without manual intervention.

**Independent Test**: Player can visit multiple surface portals and their corresponding underground portals, and the system automatically creates links for all visited pairs.

**Acceptance Scenarios**:

1. **Given** player has visited 3 cave portals on level 0, **When** player transitions through each to level 1, **Then** system creates 3 pairs of linked markers with unique UIDs for each pair
2. **Given** player deletes a linked marker, **When** player revisits that portal, **Then** system automatically recreates the marker preserving the original UID link
3. **Given** player is on level 2 or below, **When** player transitions between levels, **Then** system correctly identifies the dungeon level and creates links only for single-level portals (cave/minehole connect only adjacent levels)

---

### User Story 4 - Distinguishing Layer Transitions from Other Teleportation Types (Priority: P4)

System automatically determines whether teleportation is a layer transition (via portal) or another type of movement (hearthfire bone, village totem, signpost fast travel).

**Why this priority**: Critical for preventing false positives in the linking system. Without this, the function would create incorrect links during ordinary teleportation.

**Independent Test**: Player can use hearthfire or totem teleportation, and the system will not create linked portal markers.

**Acceptance Scenarios**:

1. **Given** player uses hearthfire bone teleportation, **When** teleportation occurs, **Then** system does not create linked portal markers
2. **Given** player uses signpost fast travel within the same layer, **When** teleportation occurs, **Then** system does not detect a layer transition
3. **Given** player enters a cave portal, **When** teleportation occurs from level 0 to level 1, **Then** system correctly identifies this as a layer transition

---

### Edge Cases

- **What happens when loading game in underground (level 2+)**: System must determine current level and correctly link markers during transitions from that level
- **How system handles player deletion of linked markers**: Automatically recreate on portal revisit while preserving UID
- **What happens with multi-level portals (mineshaft)**: Requires separate logic for portals connecting more than 2 levels
- **How minehole/ladder portals are handled**: Entry marker (minehole IN) and exit marker (ladder OUT) may have different icons
- **Relative positioning preservation**: If two caves on surface form a triangle, their underground counterparts must form the same triangle (relative positions preserved even if absolute coordinates differ)
- **Direction indicators for different portal types**: Cave uses same icon for IN/OUT, minehole/ladder may use different icons

## Requirements

### Functional Requirements

- **FR-001**: System MUST automatically detect portals (cave, minehole, ladder) when they enter player's visibility square
- **FR-002**: System MUST create portal markers with unique 6-character identifiers (0-9a-zA-Z) on first discovery
- **FR-003**: System MUST detect layer transition events via portal (distinguish from other teleportation: hearthfire, village totem, signposts)
- **FR-004**: System MUST determine current dungeon level using transition direction logic (entering cave from surface = descent level 0→1, exiting cave = ascent level 1→0, descending ladder = lower level, climbing ladder = higher level)
- **FR-005**: System MUST use the transition portal as the zero reference point for coordinate transformation between layers
- **FR-006**: System MUST preserve relative portal positions between connected layers (offset vectors remain constant)
- **FR-007**: System MUST create linked markers with direction indicators (IN/OUT) to denote transition direction between levels
- **FR-008**: System MUST assign identical names (Cave for cave, Hole for minehole/ladder) to all linked markers in a pair
- **FR-009**: System MUST persist created links to save file for cross-session persistence
- **FR-010**: System MUST automatically recreate player-deleted linked markers on portal revisit
- **FR-011**: System MUST support marker list sorting such that linked markers appear adjacent (alphabetical order)
- **FR-012**: System MUST correctly handle single-level portals (cave, minehole - connect only 2 adjacent levels)
- **FR-013**: System MUST support extension for multi-level portals (mineshaft - connect multiple levels贯通)
- **FR-014**: System MUST identify nearest portal to player and track nearest portal changes during movement
- **FR-015**: System MUST transform marker coordinates from world coordinates to local coordinates relative to transition portal
- **FR-016**: System MUST check all markers on the map for linking without distance restrictions

### Key Entities

- **Portal**: Special map location where player teleports to another map layer. Types: cave (natural cave entrance), minehole (player-dug shaft), ladder (climbing exit), mineshaft (multi-level shaft).
- **Map Layer**: Vertical game world level. Level 0 = surface, Levels 1+ = underground. Layers are stacked vertically.
- **Portal Marker**: Visual marker on player's map indicating portal location. Contains: name, type, coordinates, link UID, direction marker (IN/OUT).
- **Portal Link**: Association between two portal markers on different layers sharing a unique identifier (UID).
- **Offset Vector**: Coordinate difference between transition portal and another marker, preserved between connected layers.
- **Direction Marker**: Indicator of transition direction (IN = entering layer, OUT = exiting layer).

## Success Criteria

### Measurable Outcomes

- **SC-001**: Player can switch between linked markers on different layers in 2 clicks (one per marker in list)
- **SC-002**: System automatically creates linked markers within 2 seconds of completing layer transition
- **SC-003**: 100% of portal transitions are correctly detected as inter-layer (no false positives on other teleportation types)
- **SC-004**: System preserves 100% of created links between sessions (all links restored after reload)
- **SC-005**: Relative positioning of linked portals is preserved with accuracy of 1 tile between layers
- **SC-006**: Player can explore and automatically link unlimited portals without manual intervention
- **SC-007**: Marker deletion and recreation occurs automatically on portal visit (no player action required)

## Assumptions

- **A-001**: Game provides API for detecting teleportation events and determining teleportation type (portal vs hearthfire vs totem vs signpost)
- **A-002**: Dungeon level is determined by transition direction logic: entering cave/minehole = descent, exiting/climbing ladder = ascent
- **A-003**: Each map has its own coordinate system, but relative portal positions are preserved between maps stacked vertically
- **A-004**: Map marker system supports custom attributes (link UID, direction marker, portal type)
- **A-005**: Map marker save file exists and supports extension with new fields
- **A-006**: Cave portals exist only between levels 0 and 1 (surface and first underground level)
- **A-007**: Minehole/ladder portals exist between levels 1+ (underground levels)
- **A-008**: Coordinate transformation uses transition portal as zero reference point; all other markers transform to local coordinates relative to this portal
- **A-009**: Marker linking checks all markers on map without distance restrictions

## Resolved Clarifications

- **RC-001** (Dungeon Level Determination): Game does not provide direct API for level retrieval. Level is determined by transition direction: entering cave/shaft = descend one level, exiting/climbing ladder = ascend one level.
- **RC-002** (Coordinate Transformation): Each map has its own coordinate system. Relative portal positions are identical on vertically stacked maps. Transition portal serves as zero reference point for coordinate transformation.
- **RC-003** (Distance Restriction): No distance restriction. All markers on map are checked for linking.
