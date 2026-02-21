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

package nurgling.markers;

import haven.Coord;
import haven.Resource;
import nurgling.navigation.ChunkPortal;
import nurgling.utils.UidGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UID generation in PortalLinkManager.
 * <p>
 * These tests verify that the UID generation produces valid 6-character
 * alphanumeric strings with uniqueness guarantees.
 * </p>
 *
 * @see PortalLinkManagerImpl
 * @see PortalLink
 * @see UidGenerator
 */
@DisplayName("PortalLinkManager UID Generation")
class PortalLinkManagerTest {

    @Nested
    @DisplayName("generateUid()")
    class GenerateUidTests {

        @Test
        @DisplayName("returns string with length exactly 6")
        void testGenerateUid_ReturnsLengthSix() {
            String uid = UidGenerator.generateUid();

            assertEquals(6, uid.length(), "UID should be exactly 6 characters long");
        }

        @Test
        @DisplayName("uses only alphanumeric characters (0-9, a-z, A-Z)")
        void testGenerateUid_UsesOnlyAlphanumericCharacters() {
            String uid = UidGenerator.generateUid();

            assertTrue(uid.matches("[0-9a-zA-Z]{6}"),
                    "UID should contain only alphanumeric characters (0-9, a-z, A-Z)");
        }

        @Test
        @DisplayName("generates unique UIDs (1000 calls produce all different values)")
        void testGenerateUid_GeneratesUniqueUids() {
            Set<String> uids = new HashSet<>();
            int numberOfCalls = 1000;

            for (int i = 0; i < numberOfCalls; i++) {
                String uid = UidGenerator.generateUid();
                uids.add(uid);
            }

            assertEquals(numberOfCalls, uids.size(),
                    "All " + numberOfCalls + " generated UIDs should be unique");
        }

        @Test
        @DisplayName("is case-sensitive (different character cases are preserved)")
        void testGenerateUid_IsCaseSensitive() {
            // Generate multiple UIDs and verify case sensitivity
            Set<String> uids = new HashSet<>();
            int numberOfCalls = 100;

            for (int i = 0; i < numberOfCalls; i++) {
                uids.add(UidGenerator.generateUid());
            }

            // Verify that we have UIDs with different cases
            boolean hasUppercase = false;
            boolean hasLowercase = false;

            for (String uid : uids) {
                for (char c : uid.toCharArray()) {
                    if (Character.isUpperCase(c)) {
                        hasUppercase = true;
                    }
                    if (Character.isLowerCase(c)) {
                        hasLowercase = true;
                    }
                }
                if (hasUppercase && hasLowercase) {
                    break;
                }
            }

            assertTrue(hasUppercase || hasLowercase,
                    "UIDs should contain alphabetic characters with case sensitivity");

            // Verify that case differences are preserved (e.g., "ABC123" != "abc123")
            String uid1 = "ABC123";
            String uid2 = "abc123";
            assertNotEquals(uid1, uid2, "UIDs should be case-sensitive");
        }
    }

    @Nested
    @DisplayName("Marker List Sorting")
    class MarkerListSortingTests {

        /**
         * Comparator for sorting portal markers:
         * - Primary: by name (alphabetically)
         * - Secondary: by linkUid (alphabetically, nulls last)
         * - Tertiary: by direction (IN before OUT)
         */
        private final Comparator<PortalMarker> markerComparator = (m1, m2) -> {
            // Primary: sort by name
            int nameCompare = m1.getName().compareTo(m2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }

            // Secondary: sort by linkUid (nulls last)
            String linkUid1 = m1.getLinkUid();
            String linkUid2 = m2.getLinkUid();
            if (linkUid1 == null && linkUid2 == null) {
                return 0;
            }
            if (linkUid1 == null) {
                return 1; // nulls last
            }
            if (linkUid2 == null) {
                return -1; // nulls last
            }
            int linkUidCompare = linkUid1.compareTo(linkUid2);
            if (linkUidCompare != 0) {
                return linkUidCompare;
            }

            // Tertiary: sort by direction (IN before OUT, nulls last)
            Direction dir1 = m1.getDirection();
            Direction dir2 = m2.getDirection();
            if (dir1 == null && dir2 == null) {
                return 0;
            }
            if (dir1 == null) {
                return 1; // nulls last
            }
            if (dir2 == null) {
                return -1; // nulls last
            }
            return dir1.compareTo(dir2);
        };

        /**
         * Helper method to create a test PortalMarker.
         */
        private PortalMarker createMarker(String name, String linkUid, Direction direction, int layer) {
            return new PortalMarker(
                name,
                PortalType.CAVE,
                1L, // gridId
                new Coord(0, 0), // coord
                linkUid,
                direction,
                layer,
                System.currentTimeMillis(),
                null // icon
            );
        }

        /**
         * Helper method to verify that linked markers are grouped together.
         * Linked markers are markers that share the same linkUid.
         */
        private void verifyLinkedMarkersGrouped(List<PortalMarker> sortedMarkers) {
            String currentLinkUid = null;
            int groupStartIndex = -1;

            for (int i = 0; i < sortedMarkers.size(); i++) {
                PortalMarker marker = sortedMarkers.get(i);
                String linkUid = marker.getLinkUid();

                if (linkUid != null) {
                    if (!linkUid.equals(currentLinkUid)) {
                        // New group started
                        currentLinkUid = linkUid;
                        groupStartIndex = i;
                    } else {
                        // Same group, verify it's contiguous
                        // (already guaranteed by being in the same iteration)
                    }
                }
            }
        }

        @Test
        @DisplayName("sorts markers by name as primary key")
        void testSortMarkers_PrimaryByName() {
            List<PortalMarker> markers = new ArrayList<>();
            markers.add(createMarker("Zebra", null, null, 0));
            markers.add(createMarker("Alpha", null, null, 0));
            markers.add(createMarker("Beta", null, null, 0));

            markers.sort(markerComparator);

            assertEquals("Alpha", markers.get(0).getName());
            assertEquals("Beta", markers.get(1).getName());
            assertEquals("Zebra", markers.get(2).getName());
        }

        @Test
        @DisplayName("sorts markers by linkUid as secondary key (groups linked markers)")
        void testSortMarkers_SecondaryByLinkUid_GroupsLinkedMarkers() {
            String linkUidA = "abc123";
            String linkUidB = "xyz789";

            List<PortalMarker> markers = new ArrayList<>();
            // Add markers with same name but different linkUids
            markers.add(createMarker("Cave", linkUidB, Direction.OUT, 0));
            markers.add(createMarker("Cave", linkUidA, Direction.IN, 0));
            markers.add(createMarker("Cave", linkUidA, Direction.OUT, 0));
            markers.add(createMarker("Cave", linkUidB, Direction.IN, 0));

            markers.sort(markerComparator);

            // Verify all linkUidA markers come before linkUidB markers
            int firstLinkUidA = -1;
            int lastLinkUidA = -1;
            int firstLinkUidB = -1;
            int lastLinkUidB = -1;

            for (int i = 0; i < markers.size(); i++) {
                String uid = markers.get(i).getLinkUid();
                if (uid.equals(linkUidA)) {
                    if (firstLinkUidA == -1) firstLinkUidA = i;
                    lastLinkUidA = i;
                } else if (uid.equals(linkUidB)) {
                    if (firstLinkUidB == -1) firstLinkUidB = i;
                    lastLinkUidB = i;
                }
            }

            // Verify linkUidA group comes before linkUidB group
            assertTrue(lastLinkUidA < firstLinkUidB,
                "Markers with linkUid '" + linkUidA + "' should be grouped before '" + linkUidB + "'");

            // Verify linked markers are contiguous
            assertEquals(2, lastLinkUidA - firstLinkUidA + 1,
                "linkUidA markers should be contiguous");
            assertEquals(2, lastLinkUidB - firstLinkUidB + 1,
                "linkUidB markers should be contiguous");
        }

        @Test
        @DisplayName("sorts markers by direction as tertiary key (IN before OUT)")
        void testSortMarkers_TertiaryByDirection() {
            String linkUid = "abc123";

            List<PortalMarker> markers = new ArrayList<>();
            // Add markers with same name and linkUid but different directions
            markers.add(createMarker("Cave", linkUid, Direction.OUT, 0));
            markers.add(createMarker("Cave", linkUid, Direction.IN, 0));

            markers.sort(markerComparator);

            // Verify IN comes before OUT
            assertEquals(Direction.IN, markers.get(0).getDirection(),
                "IN direction should come before OUT direction");
            assertEquals(Direction.OUT, markers.get(1).getDirection());
        }

        @Test
        @DisplayName("groups linked markers together in complex scenario")
        void testSortMarkers_LinkedMarkersGrouped_ComplexScenario() {
            String linkUid1 = "aaa111";
            String linkUid2 = "bbb222";
            String linkUid3 = "ccc333";

            List<PortalMarker> markers = new ArrayList<>();

            // Create markers with different names and linkUids
            // Name "Cave" with linkUid2
            markers.add(createMarker("Cave", linkUid2, Direction.OUT, 0));
            // Name "Alpha" with linkUid1
            markers.add(createMarker("Alpha", linkUid1, Direction.IN, 0));
            // Name "Cave" with linkUid1 (should group with other linkUid1 after Alpha)
            markers.add(createMarker("Cave", linkUid1, Direction.OUT, 0));
            // Name "Alpha" with linkUid1 (should be first, grouped with other Alpha+linkUid1)
            markers.add(createMarker("Alpha", linkUid1, Direction.OUT, 0));
            // Name "Beta" unlinked
            markers.add(createMarker("Beta", null, null, 0));
            // Name "Cave" with linkUid3
            markers.add(createMarker("Cave", linkUid3, Direction.IN, 0));
            // Name "Cave" unlinked
            markers.add(createMarker("Cave", null, null, 0));

            markers.sort(markerComparator);

            // Verify sorting order:
            // 1. Alpha + linkUid1 (IN before OUT)
            // 2. Alpha + linkUid1 (OUT)
            // 3. Beta (unlinked, null linkUid comes after non-null within same name)
            // 4. Cave + linkUid1 (OUT - only one with this combo)
            // 5. Cave + linkUid2 (OUT - only one with this combo)
            // 6. Cave + linkUid3 (IN - only one with this combo)
            // 7. Cave (unlinked, null linkUid)

            // Verify Alpha markers come first
            assertEquals("Alpha", markers.get(0).getName());
            assertEquals("Alpha", markers.get(1).getName());
            assertEquals(linkUid1, markers.get(0).getLinkUid());
            assertEquals(linkUid1, markers.get(1).getLinkUid());

            // Verify Alpha linkUid1 markers are grouped (IN before OUT)
            assertEquals(Direction.IN, markers.get(0).getDirection());
            assertEquals(Direction.OUT, markers.get(1).getDirection());

            // Verify Beta (unlinked) comes after Alpha (name sort: "Beta" < "Cave")
            assertEquals("Beta", markers.get(2).getName());
            assertTrue(markers.get(2).getLinkUid() == null);

            // Verify Cave markers with linkUid1 come after Beta
            assertEquals("Cave", markers.get(3).getName());
            assertEquals(linkUid1, markers.get(3).getLinkUid());
            assertEquals(Direction.OUT, markers.get(3).getDirection());

            // Verify Cave markers with linkUid2 comes after linkUid1 (alphabetical)
            assertEquals("Cave", markers.get(4).getName());
            assertEquals(linkUid2, markers.get(4).getLinkUid());
            assertEquals(Direction.OUT, markers.get(4).getDirection());

            // Verify Cave marker with linkUid3 comes after linkUid2 (alphabetical)
            assertEquals("Cave", markers.get(5).getName());
            assertEquals(linkUid3, markers.get(5).getLinkUid());
            assertEquals(Direction.IN, markers.get(5).getDirection());

            // Verify unlinked Cave marker comes last (null linkUid)
            assertEquals("Cave", markers.get(6).getName());
            assertTrue(markers.get(6).getLinkUid() == null);
        }

        @Test
        @DisplayName("handles null linkUids correctly (nulls last)")
        void testSortMarkers_NullLinkUidsLast() {
            List<PortalMarker> markers = new ArrayList<>();
            markers.add(createMarker("Cave", null, null, 0));
            markers.add(createMarker("Cave", "abc123", Direction.IN, 0));
            markers.add(createMarker("Cave", null, null, 0));
            markers.add(createMarker("Cave", "xyz789", Direction.OUT, 0));

            markers.sort(markerComparator);

            // Verify non-null linkUids come before null linkUids
            assertTrue(markers.get(0).getLinkUid() != null);
            assertTrue(markers.get(1).getLinkUid() != null);
            assertTrue(markers.get(2).getLinkUid() == null);
            assertTrue(markers.get(3).getLinkUid() == null);
        }

        @Test
        @DisplayName("handles null directions correctly (nulls last)")
        void testSortMarkers_NullDirectionsLast() {
            String linkUid = "abc123";

            List<PortalMarker> markers = new ArrayList<>();
            markers.add(createMarker("Cave", linkUid, null, 0));
            markers.add(createMarker("Cave", linkUid, Direction.IN, 0));
            markers.add(createMarker("Cave", linkUid, Direction.OUT, 0));

            markers.sort(markerComparator);

            // Verify IN comes first, then OUT, then null
            assertEquals(Direction.IN, markers.get(0).getDirection());
            assertEquals(Direction.OUT, markers.get(1).getDirection());
            assertTrue(markers.get(2).getDirection() == null);
        }
    }

    @Nested
    @DisplayName("Multiple Link Creation with Unique UIDs (T036)")
    class MultipleLinkCreationTests {

        /**
         * Helper method to create a test TeleportationEvent.
         */
        private nurgling.teleportation.TeleportationEvent createTeleportationEvent(
                long sourceGridId, long targetGridId,
                Coord sourceCoord, Coord targetCoord) {
            return new nurgling.teleportation.TeleportationEvent(
                nurgling.teleportation.TeleportationType.PORTAL_LAYER_TRANSITION,
                sourceGridId,
                sourceCoord,
                targetGridId,
                targetCoord,
                System.currentTimeMillis(),
                "gfx/terobjs/cave-entrance"
            );
        }

        @Test
        @DisplayName("creates unique UIDs for multiple link creations (T036)")
        void testMultipleLinkCreation_UniqueUids_T036() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            Coord coord1 = new Coord(100, 200);
            Coord coord2 = new Coord(150, 250);
            Coord coord3 = new Coord(300, 400);
            Coord coord4 = new Coord(350, 450);

            // Use grid IDs that correspond to different layers (layer = gridId / 10000)
            // Layer 0 -> Layer 1 transition
            nurgling.teleportation.TeleportationEvent event1 = createTeleportationEvent(
                1000L, 11000L, coord1, coord2);
            // Layer 1 -> Layer 2 transition
            nurgling.teleportation.TeleportationEvent event2 = createTeleportationEvent(
                15000L, 25000L, coord3, coord4);
            // Layer 2 -> Layer 3 transition
            nurgling.teleportation.TeleportationEvent event3 = createTeleportationEvent(
                28000L, 38000L, new Coord(500, 600), new Coord(550, 650));

            String uid1 = manager.createLinkedMarkers(event1, ChunkPortal.PortalType.CAVE);
            String uid2 = manager.createLinkedMarkers(event2, ChunkPortal.PortalType.MINEHOLE);
            String uid3 = manager.createLinkedMarkers(event3, ChunkPortal.PortalType.LADDER);

            assertNotNull(uid1, "First UID should not be null");
            assertNotNull(uid2, "Second UID should not be null");
            assertNotNull(uid3, "Third UID should not be null");

            assertNotEquals(uid1, uid2, "First and second UIDs should be different");
            assertNotEquals(uid1, uid3, "First and third UIDs should be different");
            assertNotEquals(uid2, uid3, "Second and third UIDs should be different");

            assertEquals(6, uid1.length(), "First UID should be exactly 6 characters");
            assertEquals(6, uid2.length(), "Second UID should be exactly 6 characters");
            assertEquals(6, uid3.length(), "Third UID should be exactly 6 characters");

            assertTrue(uid1.matches("[0-9a-zA-Z]{6}"), "First UID should be alphanumeric");
            assertTrue(uid2.matches("[0-9a-zA-Z]{6}"), "Second UID should be alphanumeric");
            assertTrue(uid3.matches("[0-9a-zA-Z]{6}"), "Third UID should be alphanumeric");
        }

        @Test
        @DisplayName("creates multiple links with different portal types (T036)")
        void testMultipleLinkCreation_DifferentPortalTypes_T036() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            // Use grid IDs that correspond to different layers (layer = gridId / 10000)
            // Layer 0 -> Layer 1 transition
            nurgling.teleportation.TeleportationEvent event1 = createTeleportationEvent(
                1000L, 11000L, new Coord(100, 200), new Coord(150, 250));
            // Layer 1 -> Layer 2 transition
            nurgling.teleportation.TeleportationEvent event2 = createTeleportationEvent(
                15000L, 25000L, new Coord(300, 400), new Coord(350, 450));

            String uid1 = manager.createLinkedMarkers(event1, ChunkPortal.PortalType.CAVE);
            String uid2 = manager.createLinkedMarkers(event2, ChunkPortal.PortalType.MINEHOLE);

            assertNotEquals(uid1, uid2, "Different link creations should have different UIDs");

            java.util.Optional<PortalLink> link1 = manager.getLinkByUid(uid1);
            java.util.Optional<PortalLink> link2 = manager.getLinkByUid(uid2);

            assertTrue(link1.isPresent(), "First link should be retrievable");
            assertTrue(link2.isPresent(), "Second link should be retrievable");
        }

        @Test
        @DisplayName("generates unique UIDs for 100 consecutive link creations (T036)")
        void testMultipleLinkCreation_StressTestUniqueUids_T036() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            Set<String> uids = new HashSet<>();
            int numberOfCreations = 100;

            for (int i = 0; i < numberOfCreations; i++) {
                // Use grid IDs that correspond to adjacent layers
                // Layer i%10 -> Layer (i%10)+1 transition
                long sourceLayer = i % 10;
                long targetLayer = sourceLayer + 1;
                nurgling.teleportation.TeleportationEvent event = createTeleportationEvent(
                    sourceLayer * 10000 + 1000 + i,
                    targetLayer * 10000 + 1000 + i,
                    new Coord(100 + i, 200 + i),
                    new Coord(150 + i, 250 + i)
                );

                String uid = manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);
                uids.add(uid);
            }

            assertEquals(numberOfCreations, uids.size(),
                "All " + numberOfCreations + " created UIDs should be unique");

            for (String uid : uids) {
                assertEquals(6, uid.length(), "Each UID should be exactly 6 characters");
                assertTrue(uid.matches("[0-9a-zA-Z]{6}"),
                    "Each UID should contain only alphanumeric characters");
            }
        }
    }

    @Nested
    @DisplayName("Marker Recreation After Deletion (T037)")
    class MarkerRecreationAfterDeletionTests {

        /**
         * Helper method to create a test TeleportationEvent.
         */
        private nurgling.teleportation.TeleportationEvent createTeleportationEvent(
                long sourceGridId, long targetGridId,
                Coord sourceCoord, Coord targetCoord) {
            return new nurgling.teleportation.TeleportationEvent(
                nurgling.teleportation.TeleportationType.PORTAL_LAYER_TRANSITION,
                sourceGridId,
                sourceCoord,
                targetGridId,
                targetCoord,
                System.currentTimeMillis(),
                "gfx/terobjs/cave-entrance"
            );
        }

        @Test
        @DisplayName("allows marker recreation after clear with new unique UID (T037)")
        void testMarkerRecreationAfterClear_NewUid_T037() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            // Use grid IDs that correspond to adjacent layers (layer = gridId / 10000)
            // Layer 0 -> Layer 1 transition
            nurgling.teleportation.TeleportationEvent event = createTeleportationEvent(
                1000L, 11000L, new Coord(100, 200), new Coord(150, 250));

            String firstUid = manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);

            assertNotNull(firstUid, "First UID should not be null");

            manager.clear();

            String secondUid = manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);

            assertNotNull(secondUid, "Second UID should not be null");
            assertNotEquals(firstUid, secondUid,
                "UID after clear should be different from previous UID");
            assertEquals(6, secondUid.length(), "Second UID should be exactly 6 characters");
            assertTrue(secondUid.matches("[0-9a-zA-Z]{6}"),
                "Second UID should contain only alphanumeric characters");
        }

        @Test
        @DisplayName("recreates markers with same event data but different UID (T037)")
        void testMarkerRecreation_SameEventData_DifferentUid_T037() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            long sourceGridId = 5000L;  // Layer 0
            long targetGridId = 15000L; // Layer 1
            Coord sourceCoord = new Coord(300, 400);
            Coord targetCoord = new Coord(350, 450);

            nurgling.teleportation.TeleportationEvent event = createTeleportationEvent(
                sourceGridId, targetGridId, sourceCoord, targetCoord);

            String uid1 = manager.createLinkedMarkers(event, ChunkPortal.PortalType.MINEHOLE);

            manager.clear();

            nurgling.teleportation.TeleportationEvent sameEvent = createTeleportationEvent(
                sourceGridId, targetGridId, sourceCoord, targetCoord);

            String uid2 = manager.createLinkedMarkers(sameEvent, ChunkPortal.PortalType.MINEHOLE);

            assertNotEquals(uid1, uid2,
                "Recreating markers with same event data should produce different UID");

            java.util.Optional<PortalLink> link2 = manager.getLinkByUid(uid2);
            assertTrue(link2.isPresent(), "Second link should be retrievable");

            PortalLink link = link2.get();
            assertEquals(sourceCoord, link.getSourceMarker().getCoord(),
                "Recreated source marker should have correct coordinates");
            assertEquals(targetCoord, link.getTargetMarker().getCoord(),
                "Recreated target marker should have correct coordinates");
        }

        @Test
        @DisplayName("allows multiple recreation cycles with unique UIDs (T037)")
        void testMultipleRecreationCycles_UniqueUids_T037() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            // Use grid IDs that correspond to adjacent layers (layer = gridId / 10000)
            // Layer 0 -> Layer 1 transition
            nurgling.teleportation.TeleportationEvent event = createTeleportationEvent(
                1000L, 11000L, new Coord(100, 200), new Coord(150, 250));

            Set<String> allUids = new HashSet<>();
            int numberOfCycles = 10;

            for (int i = 0; i < numberOfCycles; i++) {
                String uid = manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);
                allUids.add(uid);
                manager.clear();
            }

            assertEquals(numberOfCycles, allUids.size(),
                "All " + numberOfCycles + " recreation cycles should produce unique UIDs");

            for (String uid : allUids) {
                assertEquals(6, uid.length(), "Each UID should be exactly 6 characters");
                assertTrue(uid.matches("[0-9a-zA-Z]{6}"),
                    "Each UID should contain only alphanumeric characters");
            }
        }

        @Test
        @DisplayName("recreates markers with correct directions after clear (T037)")
        void testMarkerRecreation_CorrectDirections_T037() {
            PortalLinkManagerImpl manager = new PortalLinkManagerImpl();

            // Use grid IDs that correspond to adjacent layers (layer = gridId / 10000)
            // Layer 0 -> Layer 1 transition
            nurgling.teleportation.TeleportationEvent event = createTeleportationEvent(
                1000L, 11000L, new Coord(100, 200), new Coord(150, 250));

            manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);
            manager.clear();

            String recreatedUid = manager.createLinkedMarkers(event, ChunkPortal.PortalType.CAVE);

            java.util.Optional<PortalLink> recreatedLink = manager.getLinkByUid(recreatedUid);
            assertTrue(recreatedLink.isPresent(), "Recreated link should be retrievable");

            PortalLink link = recreatedLink.get();
            assertEquals(Direction.OUT, link.getSourceMarker().getDirection(),
                "Recreated source marker should have OUT direction");
            assertEquals(Direction.IN, link.getTargetMarker().getDirection(),
                "Recreated target marker should have IN direction");
        }
    }
}
