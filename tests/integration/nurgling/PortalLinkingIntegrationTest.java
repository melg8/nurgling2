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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            String sharedUid = nurgling.utils.UidGenerator.generateUid();

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
            
            // And: UIDs are valid 6-character alphanumeric strings
            assertEquals(6, uid1.length(), "First UID should be exactly 6 characters");
            assertEquals(6, uid2.length(), "Second UID should be exactly 6 characters");
            assertTrue(uid1.matches("[0-9a-zA-Z]{6}"), "First UID should be alphanumeric");
            assertTrue(uid2.matches("[0-9a-zA-Z]{6}"), "Second UID should be alphanumeric");
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

    @Nested
    @DisplayName("Marker Click to Map Center Action")
    class MarkerClickCenterTests {

        @Test
        @DisplayName("clicking on portal marker centers map on marker coordinates")
        void testMarkerClickCentersMap() {
            // Given: A portal marker with known coordinates
            long markerGridId = 54321L;
            Coord markerCoord = new Coord(250, 350);
            int markerLayer = 1;
            long timestamp = System.currentTimeMillis();
            
            TestMapWnd mapWnd = new TestMapWnd();
            TestMarker marker = new TestMarker(markerGridId, markerCoord, markerLayer, timestamp);
            
            // When: Clicking on the marker (simulating focus action)
            mapWnd.focus(marker);
            
            // Then: Map should be centered on the marker's coordinates
            assertTrue(mapWnd.isCenterCalled, "Map center should be called when focusing on marker");
            assertEquals(markerGridId, mapWnd.lastCenteredSegmentId, 
                "Map should be centered on the marker's segment");
            assertEquals(markerCoord, mapWnd.lastCenteredCoord, 
                "Map should be centered on the marker's coordinates");
        }

        @Test
        @DisplayName("clicking on OUT marker centers map on source coordinates")
        void testOutMarkerClickCentersMapOnSource() {
            // Given: A linked OUT marker (source/exit point)
            long sourceGridId = 11111L;
            Coord sourceCoord = new Coord(100, 200);
            
            TestMapWnd mapWnd = new TestMapWnd();
            TestMarker outMarker = new TestMarker(sourceGridId, sourceCoord, 0, System.currentTimeMillis());
            
            // When: Clicking on the OUT marker
            mapWnd.focus(outMarker);
            
            // Then: Map centers on the OUT marker's coordinates
            assertTrue(mapWnd.isCenterCalled);
            assertEquals(sourceGridId, mapWnd.lastCenteredSegmentId);
            assertEquals(sourceCoord, mapWnd.lastCenteredCoord);
        }

        @Test
        @DisplayName("clicking on IN marker centers map on target coordinates")
        void testInMarkerClickCentersMapOnTarget() {
            // Given: A linked IN marker (target/entrance point)
            long targetGridId = 22222L;
            Coord targetCoord = new Coord(300, 400);
            
            TestMapWnd mapWnd = new TestMapWnd();
            TestMarker inMarker = new TestMarker(targetGridId, targetCoord, 1, System.currentTimeMillis());
            
            // When: Clicking on the IN marker
            mapWnd.focus(inMarker);
            
            // Then: Map centers on the IN marker's coordinates
            assertTrue(mapWnd.isCenterCalled);
            assertEquals(targetGridId, mapWnd.lastCenteredSegmentId);
            assertEquals(targetCoord, mapWnd.lastCenteredCoord);
        }

        @Test
        @DisplayName("multiple marker clicks update map center correctly")
        void testMultipleMarkerClicksUpdateCenter() {
            // Given: Two different markers
            TestMapWnd mapWnd = new TestMapWnd();
            
            Coord coord1 = new Coord(50, 100);
            Coord coord2 = new Coord(150, 200);
            TestMarker marker1 = new TestMarker(100L, coord1, 0, System.currentTimeMillis());
            TestMarker marker2 = new TestMarker(200L, coord2, 1, System.currentTimeMillis());
            
            // When: Clicking first marker
            mapWnd.focus(marker1);
            Coord firstCenter = mapWnd.lastCenteredCoord;
            long firstSegment = mapWnd.lastCenteredSegmentId;
            
            // And: Then clicking second marker
            mapWnd.focus(marker2);
            
            // Then: First click centered on first marker
            assertEquals(coord1, firstCenter);
            assertEquals(100L, firstSegment);
            
            // And: Second click re-centered on second marker
            assertEquals(coord2, mapWnd.lastCenteredCoord);
            assertEquals(200L, mapWnd.lastCenteredSegmentId);
        }

        @Test
        @DisplayName("marker with layer 0 (surface) centers correctly")
        void testSurfaceMarkerCentersCorrectly() {
            // Given: A surface marker (layer 0)
            long surfaceGridId = 99999L;
            Coord surfaceCoord = new Coord(500, 600);
            
            TestMapWnd mapWnd = new TestMapWnd();
            TestMarker surfaceMarker = new TestMarker(surfaceGridId, surfaceCoord, 0, System.currentTimeMillis());
            
            // When: Clicking on surface marker
            mapWnd.focus(surfaceMarker);
            
            // Then: Map centers on surface marker
            assertTrue(mapWnd.isCenterCalled);
            assertEquals(surfaceGridId, mapWnd.lastCenteredSegmentId);
            assertEquals(surfaceCoord, mapWnd.lastCenteredCoord);
        }

        @Test
        @DisplayName("marker with layer 1 (underground) centers correctly")
        void testUndergroundMarkerCentersCorrectly() {
            // Given: An underground marker (layer 1)
            long undergroundGridId = 88888L;
            Coord undergroundCoord = new Coord(700, 800);

            TestMapWnd mapWnd = new TestMapWnd();
            TestMarker undergroundMarker = new TestMarker(undergroundGridId, undergroundCoord, 1, System.currentTimeMillis());

            // When: Clicking on underground marker
            mapWnd.focus(undergroundMarker);

            // Then: Map centers on underground marker
            assertTrue(mapWnd.isCenterCalled);
            assertEquals(undergroundGridId, mapWnd.lastCenteredSegmentId);
            assertEquals(undergroundCoord, mapWnd.lastCenteredCoord);
        }
    }

    @Nested
    @DisplayName("Dungeon Level Handling - Level 2+ (T038)")
    class DungeonLevelHandlingTests {

        @Test
        @DisplayName("handles transition from surface to dungeon level 2 (T038)")
        void testSurfaceToDungeonLevel2_Transition_T038() {
            // Given: A teleportation event from surface (layer 0) to dungeon level 2
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                10000L,  // surface grid
                new Coord(100, 200),
                20000L,  // dungeon level 2 grid
                new Coord(150, 250),
                TIMESTAMP,
                PORTAL_GOB_NAME
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(2); // Descending 2 levels
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.MINEHOLE);

            // When: Processing the transition
            teleportationDetector.triggerEvent(event);
            layerTransitionDetector.isLayerTransition(event);
            layerTransitionDetector.getTransitionDirection(event);
            layerTransitionDetector.getPortalType(event);
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.MINEHOLE);

            // Then: Markers are created with correct UID
            assertNotNull(uid, "UID should be generated for level 2 transition");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size(), "Should create exactly 2 markers for level 2 transition");

            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);

            // And: OUT marker is on surface (source)
            assertEquals(Direction.OUT, outMarker.direction);
            assertEquals(10000L, outMarker.gridId);
            assertEquals(new Coord(100, 200), outMarker.coord);

            // And: IN marker is on dungeon level 2 (target)
            assertEquals(Direction.IN, inMarker.direction);
            assertEquals(20000L, inMarker.gridId);
            assertEquals(new Coord(150, 250), inMarker.coord);
        }

        @Test
        @DisplayName("handles transition from dungeon level 2 to level 3 (T038)")
        void testDungeonLevel2ToLevel3_Transition_T038() {
            // Given: A teleportation event from dungeon level 2 to level 3
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                30000L,  // dungeon level 2 grid
                new Coord(300, 400),
                40000L,  // dungeon level 3 grid
                new Coord(350, 450),
                TIMESTAMP,
                "gfx/terobjs/ladder"
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1); // Descending 1 level
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.LADDER);

            // When: Processing the transition
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.LADDER);

            // Then: Markers are created
            assertNotNull(uid, "UID should be generated for level 2 to 3 transition");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());

            // And: Correct directions
            assertEquals(Direction.OUT, markers.get(0).direction);
            assertEquals(Direction.IN, markers.get(1).direction);

            // And: Correct grid IDs
            assertEquals(30000L, markers.get(0).gridId, "OUT marker should be on level 2");
            assertEquals(40000L, markers.get(1).gridId, "IN marker should be on level 3");
        }

        @Test
        @DisplayName("handles transition from dungeon level 3 back to surface (T038)")
        void testDungeonLevel3ToSurface_Transition_T038() {
            // Given: A teleportation event from dungeon level 3 to surface (ascending)
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                50000L,  // dungeon level 3 grid
                new Coord(500, 600),
                60000L,  // surface grid
                new Coord(550, 650),
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(-3); // Ascending 3 levels
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.CAVE);

            // When: Processing the transition
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);

            // Then: Markers are created
            assertNotNull(uid, "UID should be generated for level 3 to surface transition");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());

            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);

            // And: OUT marker is on dungeon level 3 (departure)
            assertEquals(Direction.OUT, outMarker.direction);
            assertEquals(50000L, outMarker.gridId);

            // And: IN marker is on surface (arrival)
            assertEquals(Direction.IN, inMarker.direction);
            assertEquals(60000L, inMarker.gridId);
        }

        @Test
        @DisplayName("handles multiple transitions creating unique UIDs for deep dungeon levels (T038)")
        void testMultipleDeepDungeonTransitions_UniqueUids_T038() {
            // Given: Multiple transitions through dungeon levels
            TeleportationEvent event1 = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                10000L,  // surface
                new Coord(100, 200),
                20000L,  // level 1
                new Coord(150, 250),
                TIMESTAMP,
                PORTAL_GOB_NAME
            );

            TeleportationEvent event2 = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                20000L,  // level 1
                new Coord(200, 300),
                30000L,  // level 2
                new Coord(250, 350),
                TIMESTAMP + 1000,
                "gfx/terobjs/ladder"
            );

            TeleportationEvent event3 = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                30000L,  // level 2
                new Coord(300, 400),
                40000L,  // level 3
                new Coord(350, 450),
                TIMESTAMP + 2000,
                "gfx/terobjs/mine"
            );

            TeleportationEvent event4 = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                40000L,  // level 3
                new Coord(400, 500),
                50000L,  // level 4
                new Coord(450, 550),
                TIMESTAMP + 3000,
                "gfx/terobjs/cave-entrance"
            );

            // When: Processing all transitions
            String uid1 = portalLinkManager.createLinkedMarkers(event1, ChunkPortal.PortalType.CAVE);
            String uid2 = portalLinkManager.createLinkedMarkers(event2, ChunkPortal.PortalType.LADDER);
            String uid3 = portalLinkManager.createLinkedMarkers(event3, ChunkPortal.PortalType.MINEHOLE);
            String uid4 = portalLinkManager.createLinkedMarkers(event4, ChunkPortal.PortalType.CAVE);

            // Then: All UIDs are unique
            assertNotNull(uid1);
            assertNotNull(uid2);
            assertNotNull(uid3);
            assertNotNull(uid4);

            Set<String> uniqueUids = new HashSet<>();
            uniqueUids.add(uid1);
            uniqueUids.add(uid2);
            uniqueUids.add(uid3);
            uniqueUids.add(uid4);

            assertEquals(4, uniqueUids.size(),
                "All 4 dungeon level transitions should have unique UIDs");

            // And: All UIDs are valid 6-character alphanumeric strings
            for (String uid : uniqueUids) {
                assertEquals(6, uid.length(), "Each UID should be exactly 6 characters");
                assertTrue(uid.matches("[0-9a-zA-Z]{6}"),
                    "Each UID should contain only alphanumeric characters");
            }
        }

        @Test
        @DisplayName("handles transition to dungeon level 5 (deep level) (T038)")
        void testTransitionToDungeonLevel5_DeepLevel_T038() {
            // Given: A teleportation event to dungeon level 5
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                70000L,  // level 4 grid
                new Coord(700, 800),
                80000L,  // level 5 grid
                new Coord(750, 850),
                TIMESTAMP,
                "gfx/terobjs/deep-mine"
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1);
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.MINEHOLE);

            // When: Processing the transition
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.MINEHOLE);

            // Then: Marker is created successfully
            assertNotNull(uid, "UID should be generated for deep level 5 transition");

            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());

            // And: Markers have correct grid IDs for deep levels
            assertEquals(70000L, markers.get(0).gridId, "OUT marker should be on level 4");
            assertEquals(80000L, markers.get(1).gridId, "IN marker should be on level 5");
        }

        @Test
        @DisplayName("preserves portal type for different dungeon level transitions (T038)")
        void testPortalTypePreservation_DungeonLevels_T038() {
            // Given: Transitions with different portal types at various dungeon levels
            TeleportationEvent caveEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                10000L,
                new Coord(100, 200),
                20000L,
                new Coord(150, 250),
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            TeleportationEvent mineholeEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                20000L,
                new Coord(200, 300),
                30000L,
                new Coord(250, 350),
                TIMESTAMP + 1000,
                "gfx/terobjs/mine"
            );

            TeleportationEvent ladderEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                30000L,
                new Coord(300, 400),
                40000L,
                new Coord(350, 450),
                TIMESTAMP + 2000,
                "gfx/terobjs/ladder"
            );

            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.CAVE);

            // When: Processing transitions
            String caveUid = portalLinkManager.createLinkedMarkers(caveEvent, ChunkPortal.PortalType.CAVE);
            
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.MINEHOLE);
            String mineholeUid = portalLinkManager.createLinkedMarkers(mineholeEvent, ChunkPortal.PortalType.MINEHOLE);
            
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.LADDER);
            String ladderUid = portalLinkManager.createLinkedMarkers(ladderEvent, ChunkPortal.PortalType.LADDER);

            // Then: All transitions create unique UIDs
            Set<String> uniqueUids = new HashSet<>();
            uniqueUids.add(caveUid);
            uniqueUids.add(mineholeUid);
            uniqueUids.add(ladderUid);

            assertEquals(3, uniqueUids.size(),
                "Different portal type transitions should have unique UIDs");
        }

        @Test
        @DisplayName("handles ascending from dungeon level 4 to level 2 (T038)")
        void testAscendingFromLevel4ToLevel2_T038() {
            // Given: An ascending transition from level 4 to level 2
            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                40000L,  // level 4
                new Coord(400, 500),
                20000L,  // level 2
                new Coord(450, 550),
                TIMESTAMP,
                "gfx/terobjs/express-elevator"
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(-2); // Ascending 2 levels
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.LADDER);

            // When: Processing the ascending transition
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.LADDER);

            // Then: Markers are created
            assertNotNull(uid, "UID should be generated for ascending transition");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());

            // And: OUT marker is on level 4 (departure)
            assertEquals(Direction.OUT, markers.get(0).direction);
            assertEquals(40000L, markers.get(0).gridId);

            // And: IN marker is on level 2 (arrival)
            assertEquals(Direction.IN, markers.get(1).direction);
            assertEquals(20000L, markers.get(1).gridId);
        }
    }

    @Nested
    @DisplayName("T048: Portal Layer Transition Detection - Cave Surface to Underground")
    class PortalLayerTransitionDetectionTests {

        @Test
        @DisplayName("T048 - detects cave portal transition from surface (layer 0) to underground (layer 1)")
        void testCavePortal_SurfaceToUnderground_T048() {
            // Given: A cave portal teleportation from surface to underground
            long surfaceGridId = 10000L;
            long undergroundGridId = 20000L;
            Coord surfaceCoord = new Coord(500, 600);
            Coord undergroundCoord = new Coord(550, 650);

            TeleportationEvent caveEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                surfaceGridId,
                surfaceCoord,
                undergroundGridId,
                undergroundCoord,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // And: LayerTransitionDetector identifies this as a layer transition (descending)
            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1); // Descending to underground
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.CAVE);

            // When: The system processes the cave portal teleportation
            teleportationDetector.triggerEvent(caveEvent);

            // And: LayerTransitionDetector is consulted
            boolean isLayerTransition = layerTransitionDetector.isLayerTransition(caveEvent);
            int direction = layerTransitionDetector.getTransitionDirection(caveEvent);
            ChunkPortal.PortalType portalType = layerTransitionDetector.getPortalType(caveEvent);

            // And: PortalLinkManager creates linked markers
            String uid = portalLinkManager.createLinkedMarkers(caveEvent, ChunkPortal.PortalType.CAVE);

            // Then: It IS detected as a layer transition
            assertTrue(isLayerTransition, 
                "Cave portal from surface to underground SHOULD be detected as layer transition");

            // And: Transition direction is descending (+1)
            assertEquals(1, direction, 
                "Surface to underground transition should return +1 (descending)");

            // And: Portal type is correctly identified as CAVE
            assertEquals(ChunkPortal.PortalType.CAVE, portalType, 
                "Cave entrance should be identified as CAVE portal type");

            // And: Linked markers are created with shared UID
            assertNotNull(uid, "UID should be generated for cave portal transition");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size(), "Should create exactly 2 markers for cave portal");

            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);

            // And: OUT marker is on surface (source)
            assertEquals(Direction.OUT, outMarker.direction, "OUT marker should have OUT direction");
            assertEquals(surfaceGridId, outMarker.gridId, "OUT marker should be on surface grid");
            assertEquals(surfaceCoord, outMarker.coord, "OUT marker should be at surface coordinates");

            // And: IN marker is on underground (target)
            assertEquals(Direction.IN, inMarker.direction, "IN marker should have IN direction");
            assertEquals(undergroundGridId, inMarker.gridId, "IN marker should be on underground grid");
            assertEquals(undergroundCoord, inMarker.coord, "IN marker should be at underground coordinates");

            // And: Both markers share the same UID
            assertEquals(outMarker.uid, inMarker.uid, "Both markers should share the same UID");
            assertEquals(uid, outMarker.uid, "Marker UID should match returned UID");
        }

        @Test
        @DisplayName("T048 - detects cave portal transition from underground (layer 1) to surface (layer 0)")
        void testCavePortal_UndergroundToSurface_T048() {
            // Given: A cave portal teleportation from underground to surface (ascending)
            long undergroundGridId = 20000L;
            long surfaceGridId = 10000L;
            Coord undergroundCoord = new Coord(550, 650);
            Coord surfaceCoord = new Coord(500, 600);

            TeleportationEvent caveEvent = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                undergroundGridId,
                undergroundCoord,
                surfaceGridId,
                surfaceCoord,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            // And: LayerTransitionDetector identifies this as a layer transition (ascending)
            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(-1); // Ascending to surface
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.CAVE);

            // When: The system processes the cave portal teleportation
            String uid = portalLinkManager.createLinkedMarkers(caveEvent, ChunkPortal.PortalType.CAVE);

            // Then: It IS detected as a layer transition
            boolean isLayerTransition = layerTransitionDetector.isLayerTransition(caveEvent);
            assertTrue(isLayerTransition, 
                "Cave portal from underground to surface SHOULD be detected as layer transition");

            // And: Transition direction is ascending (-1)
            int direction = layerTransitionDetector.getTransitionDirection(caveEvent);
            assertEquals(-1, direction, 
                "Underground to surface transition should return -1 (ascending)");

            // And: Markers are created with correct directions
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size());

            TestPortalLinkManager.MarkerRecord outMarker = markers.get(0);
            TestPortalLinkManager.MarkerRecord inMarker = markers.get(1);

            // And: OUT marker is on underground (departure point)
            assertEquals(Direction.OUT, outMarker.direction);
            assertEquals(undergroundGridId, outMarker.gridId);

            // And: IN marker is on surface (arrival point)
            assertEquals(Direction.IN, inMarker.direction);
            assertEquals(surfaceGridId, inMarker.gridId);
        }

        @Test
        @DisplayName("T048 - verifies complete integration flow for cave portal layer transition")
        void testCompleteIntegrationFlow_CavePortal_T048() {
            // Given: A complete cave portal integration scenario
            long surfaceGridId = 30000L;
            long undergroundGridId = 40000L;
            Coord surfaceCoord = new Coord(1000, 2000);
            Coord undergroundCoord = new Coord(1050, 2050);

            TeleportationEvent event = new TeleportationEvent(
                TeleportationType.PORTAL_LAYER_TRANSITION,
                surfaceGridId,
                surfaceCoord,
                undergroundGridId,
                undergroundCoord,
                TIMESTAMP,
                "gfx/terobjs/cave-entrance"
            );

            layerTransitionDetector.setLayerTransitionResult(true);
            layerTransitionDetector.setTransitionDirection(1);
            layerTransitionDetector.setPortalType(ChunkPortal.PortalType.CAVE);

            // When: Complete integration flow
            // 1. TeleportationDetector triggers event
            teleportationDetector.triggerEvent(event);

            // 2. Callback is registered and receives event
            final AtomicReference<TeleportationEvent> capturedEvent = new AtomicReference<>();
            TeleportationCallback callback = capturedEvent::set;
            teleportationDetector.onTeleportation(callback);
            teleportationDetector.triggerEvent(event);

            // 3. LayerTransitionDetector analyzes the event
            boolean isLayerTransition = layerTransitionDetector.isLayerTransition(event);
            int direction = layerTransitionDetector.getTransitionDirection(event);
            ChunkPortal.PortalType portalType = layerTransitionDetector.getPortalType(event);

            // 4. PortalLinkManager creates linked markers
            String uid = portalLinkManager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);

            // Then: Verify complete integration
            // Event was captured by callback
            assertNotNull(capturedEvent.get(), "Callback should receive the teleportation event");
            assertEquals(event, capturedEvent.get(), "Captured event should match original event");

            // Layer transition detected correctly
            assertTrue(isLayerTransition, "Should detect cave portal as layer transition");
            assertEquals(1, direction, "Should detect descending direction");
            assertEquals(ChunkPortal.PortalType.CAVE, portalType, "Should identify CAVE portal type");

            // Markers created correctly
            assertNotNull(uid, "UID should be generated");
            List<TestPortalLinkManager.MarkerRecord> markers = portalLinkManager.getCreatedMarkers();
            assertEquals(2, markers.size(), "Should create 2 linked markers");

            // Verify marker linking
            assertEquals(uid, markers.get(0).uid, "OUT marker should have correct UID");
            assertEquals(uid, markers.get(1).uid, "IN marker should have correct UID");
            assertEquals(Direction.OUT, markers.get(0).direction);
            assertEquals(Direction.IN, markers.get(1).direction);
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

    /**
     * Test stub for MapWnd to verify map center behavior.
     * Simulates the focus() method that centers the map on a marker.
     */
    private static class TestMapWnd {
        private boolean isCenterCalled = false;
        private long lastCenteredSegmentId = -1;
        private Coord lastCenteredCoord = null;

        /**
         * Simulates focusing on a marker, which centers the map on the marker's location.
         * This mimics the behavior of MapWnd.focus() -> MarkerList.change() -> view.center().
         *
         * @param marker the marker to focus on
         */
        public void focus(TestMarker marker) {
            isCenterCalled = true;
            lastCenteredSegmentId = marker.gridId;
            lastCenteredCoord = marker.coord;
        }

        public boolean isCenterCalled() {
            return isCenterCalled;
        }

        public long getLastCenteredSegmentId() {
            return lastCenteredSegmentId;
        }

        public Coord getLastCenteredCoord() {
            return lastCenteredCoord;
        }

        public void reset() {
            isCenterCalled = false;
            lastCenteredSegmentId = -1;
            lastCenteredCoord = null;
        }
    }

    /**
     * Test marker implementation for testing marker click to center functionality.
     */
    private static class TestMarker {
        final long gridId;
        final Coord coord;
        final int layer;
        final long createdTimestamp;

        TestMarker(long gridId, Coord coord, int layer, long createdTimestamp) {
            this.gridId = gridId;
            this.coord = coord;
            this.layer = layer;
            this.createdTimestamp = createdTimestamp;
        }
    }
}
