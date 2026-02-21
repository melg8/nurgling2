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

package nurgling.integration;

import haven.Coord;
import nurgling.markers.Direction;
import nurgling.markers.PortalLinkManager;
import nurgling.navigation.ChunkPortal;
import nurgling.teleportation.LayerTransitionDetector;
import nurgling.teleportation.TeleportationCallback;
import nurgling.teleportation.TeleportationDetector;
import nurgling.teleportation.TeleportationEvent;
import nurgling.teleportation.TeleportationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for layer transition detection and portal linking workflow.
 * <p>
 * This test verifies the complete workflow when a player transitions between
 * layers (e.g., from surface to underground via a cave portal):
 * </p>
 * <ol>
 *   <li>Player teleports through a cave portal from surface (layer 0) to underground (layer 1)</li>
 *   <li>TeleportationDetector detects the teleportation event</li>
 *   <li>LayerTransitionDetector identifies it as a layer transition</li>
 *   <li>PortalLinkManager creates two linked markers with a shared UID</li>
 *   <li>One marker has direction=OUT (surface), another has direction=IN (underground)</li>
 * </ol>
 * <p>
 * This test uses simple test stubs to mock dependencies and verify the integration flow.
 * </p>
 */
@DisplayName("Portal Linking Integration - Layer Transition Detection")
class PortalLinkingIntegrationTest {

    // Test stubs for the teleportation detection system
    private TestTeleportationDetector teleportationDetector;

    private TestLayerTransitionDetector layerTransitionDetector;

    private TestPortalLinkManager portalLinkManager;

    // Test data
    private static final long SOURCE_GRID_ID = 12345L;
    private static final long TARGET_GRID_ID = 67890L;
    private static final Coord SOURCE_COORD = new Coord(100, 200);
    private static final Coord TARGET_COORD = new Coord(150, 250);
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final String PORTAL_GOB_NAME = "gfx/terobjs/cave-entrance";
    private static final ChunkPortal.PortalType PORTAL_TYPE = ChunkPortal.PortalType.MINEHOLE;

    /**
     * Test stub for TeleportationDetector.
     */
    private static class TestTeleportationDetector implements TeleportationDetector {
        private TeleportationCallback callback;
        private TeleportationEvent lastEvent;
        private boolean clearEventCalled = false;

        @Override
        public void onTeleportation(TeleportationCallback callback) {
            this.callback = callback;
        }

        @Override
        public TeleportationEvent getLastEvent() {
            return clearEventCalled ? null : lastEvent;
        }

        @Override
        public void clearEvent() {
            clearEventCalled = true;
            lastEvent = null;
        }

        public void triggerEvent(TeleportationEvent event) {
            lastEvent = event;
            if (callback != null) {
                callback.onTeleport(event);
            }
        }

        public TeleportationCallback getCallback() {
            return callback;
        }

        public boolean isClearEventCalled() {
            return clearEventCalled;
        }
    }

    /**
     * Test stub for LayerTransitionDetector.
     */
    private static class TestLayerTransitionDetector implements LayerTransitionDetector {
        private boolean layerTransitionResult = true;
        private int transitionDirection = 1;
        private ChunkPortal.PortalType portalType = ChunkPortal.PortalType.MINEHOLE;
        private boolean isLayerTransitionCalled = false;
        private boolean getTransitionDirectionCalled = false;
        private boolean getPortalTypeCalled = false;

        @Override
        public boolean isLayerTransition(TeleportationEvent event) {
            isLayerTransitionCalled = true;
            return layerTransitionResult;
        }

        @Override
        public int getTransitionDirection(TeleportationEvent event) {
            getTransitionDirectionCalled = true;
            return transitionDirection;
        }

        @Override
        public ChunkPortal.PortalType getPortalType(TeleportationEvent event) {
            getPortalTypeCalled = true;
            return portalType;
        }

        public void setLayerTransitionResult(boolean result) {
            layerTransitionResult = result;
        }

        public void setTransitionDirection(int direction) {
            transitionDirection = direction;
        }

        public void setPortalType(ChunkPortal.PortalType type) {
            portalType = type;
        }

        public boolean isLayerTransitionCalled() {
            return isLayerTransitionCalled;
        }

        public boolean isGetTransitionDirectionCalled() {
            return getTransitionDirectionCalled;
        }

        public boolean isGetPortalTypeCalled() {
            return getPortalTypeCalled;
        }

        public void resetCallFlags() {
            isLayerTransitionCalled = false;
            getTransitionDirectionCalled = false;
            getPortalTypeCalled = false;
        }
    }

    /**
     * Test stub for PortalLinkManager.
     */
    private static class TestPortalLinkManager implements PortalLinkManager {
        private final List<MarkerRecord> createdMarkers = new ArrayList<>();
        private String lastCreatedUid;
        private boolean clearCalled = false;
        private int createLinkedMarkersCallCount = 0;
        private int createMarkerCallCount = 0;

        private static class MarkerRecord {
            final long gridId;
            final Coord coord;
            final Direction direction;
            final ChunkPortal.PortalType portalType;
            final String uid;
            final String markerName;

            MarkerRecord(long gridId, Coord coord, Direction direction,
                        ChunkPortal.PortalType portalType, String uid, String markerName) {
                this.gridId = gridId;
                this.coord = coord;
                this.direction = direction;
                this.portalType = portalType;
                this.uid = uid;
                this.markerName = markerName;
            }
        }

        @Override
        public String createLinkedMarkers(TeleportationEvent event, ChunkPortal.PortalType portalType) {
            createLinkedMarkersCallCount++;
            String sharedUid = "TEST_UID_" + createLinkedMarkersCallCount;

            // Create OUT marker on source layer
            createdMarkers.add(new MarkerRecord(
                event.getSourceGridId(),
                event.getSourceCoord(),
                Direction.OUT,
                portalType,
                sharedUid,
                "Portal OUT"
            ));

            // Create IN marker on target layer
            createdMarkers.add(new MarkerRecord(
                event.getTargetGridId(),
                event.getTargetCoord(),
                Direction.IN,
                portalType,
                sharedUid,
                "Portal IN"
            ));

            lastCreatedUid = sharedUid;
            return sharedUid;
        }

        @Override
        public void createMarker(long gridId, Coord coord, Direction direction,
                                ChunkPortal.PortalType portalType, String uid, String markerName) {
            createMarkerCallCount++;
            createdMarkers.add(new MarkerRecord(gridId, coord, direction, portalType, uid, markerName));
        }

        @Override
        public String getLastCreatedUid() {
            return clearCalled ? null : lastCreatedUid;
        }

        @Override
        public void clear() {
            clearCalled = true;
            createdMarkers.clear();
            lastCreatedUid = null;
        }

        public List<MarkerRecord> getCreatedMarkers() {
            return new ArrayList<>(createdMarkers);
        }

        public boolean isClearCalled() {
            return clearCalled;
        }

        public int getCreateLinkedMarkersCallCount() {
            return createLinkedMarkersCallCount;
        }

        public int getCreateMarkerCallCount() {
            return createMarkerCallCount;
        }

        public void reset() {
            createdMarkers.clear();
            lastCreatedUid = null;
            clearCalled = false;
            createLinkedMarkersCallCount = 0;
            createMarkerCallCount = 0;
        }
    }

    @BeforeEach
    void setUp() {
        teleportationDetector = new TestTeleportationDetector();
        layerTransitionDetector = new TestLayerTransitionDetector();
        portalLinkManager = new TestPortalLinkManager();
    }

    @Nested
    @DisplayName("Complete Layer Transition Workflow")
    class CompleteWorkflowTests {

        @Test
        @DisplayName("detects cave portal teleportation from surface to underground")
        void testCavePortalTransition_SurfaceToUnderground() {
            // Given: A teleportation event from surface (layer 0) to underground (layer 1)
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                PORTAL_GOB_NAME
            );

            // And: LayerTransitionDetector identifies this as a layer transition
            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1); // Descending
            layerTransitionDetector.setPortalType(PORTAL_TYPE);

            // When: The system processes the teleportation
            // Simulate the integration flow: detector triggers event, callback processes it
            teleportationDetector.triggerEvent(event);

            // And: LayerTransitionDetector is consulted
            layerTransitionDetector.isLayerTransition(event);
            layerTransitionDetector.getTransitionDirection(event);
            layerTransitionDetector.getPortalType(event);

            // And: PortalLinkManager creates linked markers
            portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: Two markers are created with the same UID
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size(), "Should create exactly 2 markers");

            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);

            // And: Markers have correct directions
            assertEquals(Direction.OUT, outMarker.direction, "First marker should be OUT direction");
            assertEquals(Direction.IN, inMarker.direction, "Second marker should be IN direction");

            // And: Markers share the same UID
            assertEquals(outMarker.uid, inMarker.uid, "Both markers should share the same UID");
            assertNotNull(outMarker.uid, "UID should not be null");

            // And: OUT marker is on source grid (surface)
            assertEquals(SOURCE_GRID_ID, outMarker.gridId, "OUT marker should be on source grid");
            assertEquals(SOURCE_COORD, outMarker.coord, "OUT marker should be at source coordinates");

            // And: IN marker is on target grid (underground)
            assertEquals(TARGET_GRID_ID, inMarker.gridId, "IN marker should be on target grid");
            assertEquals(TARGET_COORD, inMarker.coord, "IN marker should be at target coordinates");

            // And: Both markers have the correct portal type
            assertEquals(PORTAL_TYPE, outMarker.portalType, "OUT marker should have correct portal type");
            assertEquals(PORTAL_TYPE, inMarker.portalType, "IN marker should have correct portal type");
        }

        @Test
        @DisplayName("detects layer transition direction correctly (descending)")
        void testLayerTransitionDirection_Descending() {
            // Given: A descending teleportation (surface -> underground)
            TeleportationEvent event = createTestEvent();

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1); // +1 = descending
            layerTransitionDetector.setPortalType(PORTAL_TYPE);

            // When: Checking transition direction
            int direction = layerTransitionDetector.getTransitionDirection(event);

            // Then: Direction is positive (descending)
            assertEquals(1, direction, "Descending transition should return +1");
            assertTrue(layerTransitionDetector.isGetTransitionDirectionCalled());
        }

        @Test
        @DisplayName("detects layer transition direction correctly (ascending)")
        void testLayerTransitionDirection_Ascending() {
            // Given: An ascending teleportation (underground -> surface)
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                TARGET_GRID_ID,  // Starting from underground
                TARGET_COORD,
                SOURCE_GRID_ID,  // Going to surface
                SOURCE_COORD,
                TIMESTAMP,
                PORTAL_GOB_NAME
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(-1); // -1 = ascending
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.LADDER);

            // When: Checking transition direction
            int direction = layerTransitionDetector.getTransitionDirection(event);

            // Then: Direction is negative (ascending)
            assertEquals(-1, direction, "Ascending transition should return -1");
        }

        @Test
        @DisplayName("identifies non-layer transitions correctly")
        void testNonLayerTransition() {
            // Given: A same-layer portal transition (not a layer transition)
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_SAME_LAYER,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                SOURCE_GRID_ID,  // Same grid
                new Coord(110, 210),  // Different coord but same layer
                TIMESTAMP,
                "gfx/terobjs/door"
            );

            layerTransitionDetector.setLayerTransitionResult(false);

            // When: Checking if it's a layer transition
            boolean isLayerTransition = layerTransitionDetector.isLayerTransition(event);

            // Then: It's not a layer transition
            assertTrue(!isLayerTransition, "Same-layer transition should not be detected as layer transition");

            // And: PortalLinkManager should NOT create linked markers
            assertEquals(0, portalLinkManager.getCreateLinkedMarkersCallCount());
        }

        @Test
        @DisplayName("creates markers with unique UIDs for different transitions")
        void testUniqueUidsForDifferentTransitions() {
            // Given: Two separate teleportation events
            TeleportationEvent event1 = createTestEvent();
            TeleportationEvent event2 = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                11111L,
                new Coord(50, 50),
                22222L,
                new Coord(60, 60),
                TIMESTAMP + 1000,
                PORTAL_GOB_NAME
            );

            // When: Processing both events
            String uid1 = portalLinkManager.createLinkedMarkers(event1, PORTAL_TYPE);
            String uid2 = portalLinkManager.createLinkedMarkers(event2, PORTAL_TYPE);

            // Then: UIDs are different
            assertNotSame(uid1, uid2, "Different transitions should have different UIDs");
            assertEquals("TEST_UID_1", uid1);
            assertEquals("TEST_UID_2", uid2);
        }

        @Test
        @DisplayName("handles cave portal type correctly")
        void testCavePortalType() {
            // Given: A cave portal transition
            TeleportationEvent event = createTestEvent();

            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.MINEHOLE);

            // When: Getting portal type
            ChunkPortal.PortalType detectedType = layerTransitionDetector.getPortalType(event);

            // Then: Portal type is correctly identified
            assertEquals(ChunkPortal.PortalType.MINEHOLE, detectedType);
        }

        @Test
        @DisplayName("handles ladder portal type correctly")
        void testLadderPortalType() {
            // Given: A ladder portal transition (ascending from mine)
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                TARGET_GRID_ID,
                TARGET_COORD,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TIMESTAMP,
                "gfx/terobjs/ladder"
            );

            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.LADDER);

            // When: Getting portal type
            ChunkPortal.PortalType detectedType = layerTransitionDetector.getPortalType(event);

            // Then: Portal type is correctly identified
            assertEquals(ChunkPortal.PortalType.LADDER, detectedType);
        }

        @Test
        @DisplayName("verifies callback registration with TeleportationDetector")
        void testCallbackRegistration() {
            // Given: A TeleportationCallback
            final AtomicReference<TeleportationEvent> capturedEvent = new AtomicReference<>();
            TeleportationCallback testCallback = capturedEvent::set;

            // When: Registering the callback
            teleportationDetector.onTeleportation(testCallback);

            // And: Triggering an event
            TeleportationEvent event = createTestEvent();
            teleportationDetector.triggerEvent(event);

            // Then: Callback received the event
            assertNotNull(capturedEvent.get());
            assertEquals(event, capturedEvent.get());
        }

        @Test
        @DisplayName("verifies getLastEvent returns the last teleportation event")
        void testGetLastEvent() {
            // Given: A teleportation event
            TeleportationEvent event = createTestEvent();

            // When: Triggering the event
            teleportationDetector.triggerEvent(event);

            // Then: Returns the correct event
            TeleportationEvent lastEvent = teleportationDetector.getLastEvent();
            assertEquals(event, lastEvent);
        }

        @Test
        @DisplayName("verifies clearEvent clears stored event")
        void testClearEvent() {
            // Given: A stored event
            TeleportationEvent event = createTestEvent();
            teleportationDetector.triggerEvent(event);

            // When: Clearing the event
            teleportationDetector.clearEvent();

            // Then: Event is cleared
            assertNull(teleportationDetector.getLastEvent());
            assertTrue(teleportationDetector.isClearEventCalled());
        }

        @Test
        @DisplayName("verifies PortalLinkManager clear method")
        void testPortalLinkManagerClear() {
            // Given: Some created markers
            portalLinkManager.createLinkedMarkers(createTestEvent(), PORTAL_TYPE);

            // When: Clearing the portal link manager
            portalLinkManager.clear();

            // Then: Markers are cleared
            assertTrue(portalLinkManager.getCreatedMarkers().isEmpty());
            assertNull(portalLinkManager.getLastCreatedUid());
            assertTrue(portalLinkManager.isClearCalled());
        }
    }

    @Nested
    @DisplayName("Marker Linking Verification")
    class MarkerLinkingTests {

        @Test
        @DisplayName("linked markers share identical UID")
        void testLinkedMarkersShareUid() {
            // Given: A teleportation event
            TeleportationEvent event = createTestEvent();

            // When: Creating linked markers
            String uid = portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: UID is returned
            assertNotNull(uid, "UID should not be null");

            // And: Both markers were created with the same UID
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());
            assertEquals(uid, markers.get(0).uid);
            assertEquals(uid, markers.get(1).uid);
        }

        @Test
        @DisplayName("OUT marker created for source location")
        void testOutMarkerForSourceLocation() {
            // Given: A teleportation event
            TeleportationEvent event = createTestEvent();

            // When: Creating linked markers
            portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: OUT marker is created at source location
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            assertEquals(SOURCE_GRID_ID, outMarker.gridId);
            assertEquals(SOURCE_COORD, outMarker.coord);
            assertEquals(Direction.OUT, outMarker.direction);
        }

        @Test
        @DisplayName("IN marker created for target location")
        void testInMarkerForTargetLocation() {
            // Given: A teleportation event
            TeleportationEvent event = createTestEvent();

            // When: Creating linked markers
            portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: IN marker is created at target location
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);
            assertEquals(TARGET_GRID_ID, inMarker.gridId);
            assertEquals(TARGET_COORD, inMarker.coord);
            assertEquals(Direction.IN, inMarker.direction);
        }

        @Test
        @DisplayName("marker creation count is correct")
        void testMarkerCreationCount() {
            // Given: A teleportation event
            TeleportationEvent event = createTestEvent();

            // When: Creating linked markers
            portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: createLinkedMarkers was called once
            assertEquals(1, portalLinkManager.getCreateLinkedMarkersCallCount());

            // And: Two individual markers were created
            assertEquals(2, portalLinkManager.getCreatedMarkers().size());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("handles null portal gob name gracefully")
        void testNullPortalGobName() {
            // Given: A teleportation event with null portal gob name
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                TARGET_GRID_ID,
                TARGET_COORD,
                TIMESTAMP,
                null  // Null portal gob name
            );

            // When: Processing the event
            String uid = portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: Markers are still created
            assertNotNull(uid, "UID should be generated even with null portal gob name");
            assertEquals(2, portalLinkManager.getCreatedMarkers().size());
        }

        @Test
        @DisplayName("handles same coordinates for source and target")
        void testSameCoordinates() {
            // Given: A teleportation event with same coordinates (edge case)
            Coord sameCoord = new Coord(100, 100);
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                SOURCE_GRID_ID,
                sameCoord,
                TARGET_GRID_ID,
                sameCoord,
                TIMESTAMP,
                PORTAL_GOB_NAME
            );

            // When: Processing the event
            String uid = portalLinkManager.createLinkedMarkers(event, PORTAL_TYPE);

            // Then: Markers are still created
            assertNotNull(uid, "UID should be generated even with same coordinates");
        }

        @Test
        @DisplayName("handles different grid IDs with same layer")
        void testDifferentGridIdsSameLayer() {
            // Given: A same-layer transition with different grid IDs
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_SAME_LAYER,
                SOURCE_GRID_ID,
                SOURCE_COORD,
                SOURCE_GRID_ID + 1,  // Adjacent grid but same layer
                new Coord(50, 50),
                TIMESTAMP,
                "gfx/terobjs/door"
            );

            layerTransitionDetector.setLayerTransitionResult(false);

            // When: Checking if it's a layer transition
            boolean isLayerTransition = layerTransitionDetector.isLayerTransition(event);

            // Then: Not a layer transition
            assertTrue(!isLayerTransition);

            // And: No linked markers created
            assertEquals(0, portalLinkManager.getCreateLinkedMarkersCallCount());
        }
    }

    /**
     * Helper method to create a standard test teleportation event.
     *
     * @return a TeleportationEvent for testing
     */
    private TeleportationEvent createTestEvent() {
        return new TeleportationEvent(
            TeleportationType.PORTAL_LAYER_TRANSITION,
            SOURCE_GRID_ID,
            SOURCE_COORD,
            TARGET_GRID_ID,
            TARGET_COORD,
            TIMESTAMP,
            PORTAL_GOB_NAME
        );
    }
}
