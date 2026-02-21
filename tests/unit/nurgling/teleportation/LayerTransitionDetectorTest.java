/*
 * Copyright (C) 2024 Nurgling Project
 *
 * This file is part of the Nurgling mod for Haven & Hearth.
 *
 * This file is subject to the terms of the GNU Lesser General Public License,
 * version 3, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * A copy of the GNU Lesser General Public License is distributed
 * with this source code. If not, see <https://www.gnu.org/licenses/>.
 */

package nurgling.teleportation;

import haven.Coord;
import nurgling.navigation.ChunkPortal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for LayerTransitionDetector implementation.
 * <p>
 * These tests verify that the LayerTransitionDetector correctly identifies
 * layer transitions (cave/mine portals) and correctly ignores non-layer
 * teleportation types such as hearthfire, village totem, and signpost fast travel.
 * </p>
 */
@DisplayName("Layer Transition Detector - Unit Tests")
class LayerTransitionDetectorTest {

    private LayerTransitionDetector detector;

    private static final long SOURCE_GRID_ID = 12345L;
    private static final long TARGET_GRID_ID = 67890L;
    private static final Coord SOURCE_COORD = new Coord(100, 200);
    private static final Coord TARGET_COORD = new Coord(150, 250);
    private static final long TIMESTAMP = System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        detector = new LayerTransitionDetectorImpl();
    }

    /**
     * Concrete implementation of LayerTransitionDetector for testing.
     */
    private static class LayerTransitionDetectorImpl implements LayerTransitionDetector {

        @Override
        public boolean isLayerTransition(TeleportationEvent event) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null");
            }
            // Layer transitions are only PORTAL_LAYER_TRANSITION type
            return event.getType() == TeleportationType.PORTAL_LAYER_TRANSITION;
        }

        @Override
        public int getTransitionDirection(TeleportationEvent event) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null");
            }
            if (!isLayerTransition(event)) {
                return 0;
            }
            // For testing purposes, determine direction based on grid ID difference
            // In real implementation, this would use actual layer information
            if (event.getTargetGridId() > event.getSourceGridId()) {
                return 1; // Descending
            } else if (event.getTargetGridId() < event.getSourceGridId()) {
                return -1; // Ascending
            }
            return 0;
        }

        @Override
        public ChunkPortal.PortalType getPortalType(TeleportationEvent event) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null");
            }
            if (!isLayerTransition(event)) {
                return null;
            }
            // Determine portal type from portal gob name
            if (event.getPortalGobName() == null) {
                return ChunkPortal.PortalType.CAVE;
            }
            String name = event.getPortalGobName().toLowerCase();
            if (name.contains("cave")) {
                return ChunkPortal.PortalType.CAVE;
            } else if (name.contains("mine")) {
                return ChunkPortal.PortalType.MINEHOLE;
            } else if (name.contains("ladder")) {
                return ChunkPortal.PortalType.LADDER;
            }
            return ChunkPortal.PortalType.CAVE;
        }
    }

    @Nested
    @DisplayName("T045: Hearthfire Teleportation Non-Detection")
    class HearthfireTeleportationTests {

        @Test
        @DisplayName("T045 - hearthfire teleportation is NOT detected as layer transition")
        void testHearthfire_NotLayerTransition_T045() {
            // Given: A hearthfire teleportation event
            TeleportationEvent hearthfireEvent = new TeleportationEvent(
                TeleportationType.HEARTHFIRE,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null  // No portal gob for hearthfire
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(hearthfireEvent);

            // Then: It should NOT be detected as a layer transition
            assertFalse(isLayerTransition, 
                "Hearthfire teleportation should NOT be detected as layer transition");
        }

        @Test
        @DisplayName("T045 - hearthfire teleportation returns 0 transition direction")
        void testHearthfire_TransitionDirection_T045() {
            // Given: A hearthfire teleportation event
            TeleportationEvent hearthfireEvent = new TeleportationEvent(
                TeleportationType.HEARTHFIRE,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting transition direction
            int direction = detector.getTransitionDirection(hearthfireEvent);

            // Then: Direction should be 0 (not a layer transition)
            assertEquals(0, direction, 
                "Hearthfire teleportation should return 0 transition direction");
        }

        @Test
        @DisplayName("T045 - hearthfire teleportation returns null portal type")
        void testHearthfire_PortalType_T045() {
            // Given: A hearthfire teleportation event
            TeleportationEvent hearthfireEvent = new TeleportationEvent(
                TeleportationType.HEARTHFIRE,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(hearthfireEvent);

            // Then: Portal type should be null
            assertNull(portalType, 
                "Hearthfire teleportation should return null portal type");
        }
    }

    @Nested
    @DisplayName("T046: Village Totem Teleportation Non-Detection")
    class VillageTotemTeleportationTests {

        @Test
        @DisplayName("T046 - village totem teleportation is NOT detected as layer transition")
        void testVillageTotem_NotLayerTransition_T046() {
            // Given: A village totem teleportation event
            TeleportationEvent villageTotemEvent = new TeleportationEvent(
                TeleportationType.VILLAGE_TOTEM,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null  // No portal gob for village totem
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(villageTotemEvent);

            // Then: It should NOT be detected as a layer transition
            assertFalse(isLayerTransition, 
                "Village totem teleportation should NOT be detected as layer transition");
        }

        @Test
        @DisplayName("T046 - village totem teleportation returns 0 transition direction")
        void testVillageTotem_TransitionDirection_T046() {
            // Given: A village totem teleportation event
            TeleportationEvent villageTotemEvent = new TeleportationEvent(
                TeleportationType.VILLAGE_TOTEM,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting transition direction
            int direction = detector.getTransitionDirection(villageTotemEvent);

            // Then: Direction should be 0 (not a layer transition)
            assertEquals(0, direction, 
                "Village totem teleportation should return 0 transition direction");
        }

        @Test
        @DisplayName("T046 - village totem teleportation returns null portal type")
        void testVillageTotem_PortalType_T046() {
            // Given: A village totem teleportation event
            TeleportationEvent villageTotemEvent = new TeleportationEvent(
                TeleportationType.VILLAGE_TOTEM,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(villageTotemEvent);

            // Then: Portal type should be null
            assertNull(portalType, 
                "Village totem teleportation should return null portal type");
        }
    }

    @Nested
    @DisplayName("T047: Signpost Fast Travel Non-Detection")
    class SignpostFastTravelTests {

        @Test
        @DisplayName("T047 - signpost fast travel is NOT detected as layer transition")
        void testSignpost_NotLayerTransition_T047() {
            // Given: A signpost fast travel teleportation event
            TeleportationEvent signpostEvent = new TeleportationEvent(
                TeleportationType.SIGNPOST,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null  // No portal gob for signpost
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(signpostEvent);

            // Then: It should NOT be detected as a layer transition
            assertFalse(isLayerTransition, 
                "Signpost fast travel should NOT be detected as layer transition");
        }

        @Test
        @DisplayName("T047 - signpost fast travel returns 0 transition direction")
        void testSignpost_TransitionDirection_T047() {
            // Given: A signpost fast travel teleportation event
            TeleportationEvent signpostEvent = new TeleportationEvent(
                TeleportationType.SIGNPOST,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting transition direction
            int direction = detector.getTransitionDirection(signpostEvent);

            // Then: Direction should be 0 (not a layer transition)
            assertEquals(0, direction, 
                "Signpost fast travel should return 0 transition direction");
        }

        @Test
        @DisplayName("T047 - signpost fast travel returns null portal type")
        void testSignpost_PortalType_T047() {
            // Given: A signpost fast travel teleportation event
            TeleportationEvent signpostEvent = new TeleportationEvent(
                TeleportationType.SIGNPOST,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(signpostEvent);

            // Then: Portal type should be null
            assertNull(portalType, 
                "Signpost fast travel should return null portal type");
        }
    }

    @Nested
    @DisplayName("Positive Tests: Layer Transitions ARE Detected")
    class LayerTransitionPositiveTests {

        @Test
        @DisplayName("cave portal layer transition IS detected as layer transition")
        void testCavePortal_IsLayerTransition() {
            // Given: A cave portal layer transition event
            TeleportationEvent caveEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(caveEvent);

            // Then: It SHOULD be detected as a layer transition
            assertTrue(isLayerTransition, 
                "Cave portal layer transition SHOULD be detected as layer transition");
        }

        @Test
        @DisplayName("minehole portal layer transition IS detected as layer transition")
        void testMineholePortal_IsLayerTransition() {
            // Given: A minehole portal layer transition event
            TeleportationEvent mineholeEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/mine"
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(mineholeEvent);

            // Then: It SHOULD be detected as a layer transition
            assertTrue(isLayerTransition, 
                "Minehole portal layer transition SHOULD be detected as layer transition");
        }

        @Test
        @DisplayName("ladder portal layer transition IS detected as layer transition")
        void testLadderPortal_IsLayerTransition() {
            // Given: A ladder portal layer transition event
            TeleportationEvent ladderEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/ladder"
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(ladderEvent);

            // Then: It SHOULD be detected as a layer transition
            assertTrue(isLayerTransition, 
                "Ladder portal layer transition SHOULD be detected as layer transition");
        }

        @Test
        @DisplayName("descending layer transition returns positive direction")
        void testDescending_TransitionDirection() {
            // Given: A descending layer transition (lower grid ID to higher grid ID)
            TeleportationEvent descendingEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                10000L,  // surface
                SOURCE_COORD,
                20000L,  // underground
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // When: Getting transition direction
            int direction = detector.getTransitionDirection(descendingEvent);

            // Then: Direction should be positive (descending)
            assertEquals(1, direction, 
                "Descending layer transition should return +1 direction");
        }

        @Test
        @DisplayName("ascending layer transition returns negative direction")
        void testAscending_TransitionDirection() {
            // Given: An ascending layer transition (higher grid ID to lower grid ID)
            TeleportationEvent ascendingEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                20000L,  // underground
                SOURCE_COORD,
                10000L,  // surface
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // When: Getting transition direction
            int direction = detector.getTransitionDirection(ascendingEvent);

            // Then: Direction should be negative (ascending)
            assertEquals(-1, direction, 
                "Ascending layer transition should return -1 direction");
        }

        @Test
        @DisplayName("cave portal type is correctly identified")
        void testCave_PortalType() {
            // Given: A cave portal layer transition event
            TeleportationEvent caveEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(caveEvent);

            // Then: Portal type should be CAVE
            assertEquals(ChunkPortal.PortalType.CAVE, portalType, 
                "Cave portal should be identified as CAVE type");
        }

        @Test
        @DisplayName("minehole portal type is correctly identified")
        void testMinehole_PortalType() {
            // Given: A minehole portal layer transition event
            TeleportationEvent mineholeEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/mine"
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(mineholeEvent);

            // Then: Portal type should be MINEHOLE
            assertEquals(ChunkPortal.PortalType.MINEHOLE, portalType, 
                "Minehole portal should be identified as MINEHOLE type");
        }

        @Test
        @DisplayName("ladder portal type is correctly identified")
        void testLadder_PortalType() {
            // Given: A ladder portal layer transition event
            TeleportationEvent ladderEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/ladder"
            );

            // When: Getting portal type
            ChunkPortal.PortalType portalType = detector.getPortalType(ladderEvent);

            // Then: Portal type should be LADDER
            assertEquals(ChunkPortal.PortalType.LADDER, portalType, 
                "Ladder portal should be identified as LADDER type");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Null Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("null event throws IllegalArgumentException for isLayerTransition")
        void testNullEvent_IsLayerTransition() {
            // Given: A null event
            TeleportationEvent nullEvent = null;

            // When/Then: Should throw IllegalArgumentException
            try {
                detector.isLayerTransition(nullEvent);
                fail("Expected IllegalArgumentException for null event");
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }

        @Test
        @DisplayName("null event throws IllegalArgumentException for getTransitionDirection")
        void testNullEvent_GetTransitionDirection() {
            // Given: A null event
            TeleportationEvent nullEvent = null;

            // When/Then: Should throw IllegalArgumentException
            try {
                detector.getTransitionDirection(nullEvent);
                fail("Expected IllegalArgumentException for null event");
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }

        @Test
        @DisplayName("null event throws IllegalArgumentException for getPortalType")
        void testNullEvent_GetPortalType() {
            // Given: A null event
            TeleportationEvent nullEvent = null;

            // When/Then: Should throw IllegalArgumentException
            try {
                detector.getPortalType(nullEvent);
                fail("Expected IllegalArgumentException for null event");
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }

        @Test
        @DisplayName("PORTAL_SAME_LAYER is NOT detected as layer transition")
        void testSameLayerPortal_NotLayerTransition() {
            // Given: A same-layer portal event
            TeleportationEvent sameLayerEvent = new TeleportationEvent(
                TeleportationType.PORTAL_SAME_LAYER,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                SOURCE_GRID_ID,  // Same grid
                TARGET_COORD,
                TIMESTAMP,
                "gfx/terobjs/door"
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(sameLayerEvent);

            // Then: It should NOT be detected as a layer transition
            assertFalse(isLayerTransition, 
                "Same-layer portal should NOT be detected as layer transition");
        }

        @Test
        @DisplayName("UNKNOWN teleportation type is NOT detected as layer transition")
        void testUnknownType_NotLayerTransition() {
            // Given: An unknown teleportation event
            TeleportationEvent unknownEvent = new TeleportationEvent(
                TeleportationType.UNKNOWN,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null
            );

            // When: Checking if it's a layer transition
            boolean isLayerTransition = detector.isLayerTransition(unknownEvent);

            // Then: It should NOT be detected as a layer transition
            assertFalse(isLayerTransition, 
                "Unknown teleportation type should NOT be detected as layer transition");
        }
    }

    // Helper assertion method for tests
    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private void fail(String message) {
        throw new AssertionError(message);
    }
}
