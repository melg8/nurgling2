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

package nurgling.utils;

import haven.Coord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link CoordinateTransformer} class methods.
 * <p>
 * These tests verify the coordinate transformation logic using the
 * CoordinateTransformer singleton instance.
 * </p>
 */
@DisplayName("CoordinateTransformer")
class CoordinateTransformerTest {

    private final CoordinateTransformer transformer = CoordinateTransformer.instance;

    @Nested
    @DisplayName("calculateOffset()")
    class CalculateOffsetTests {

        @Test
        @DisplayName("markerCoord=(50,80), portalCoord=(50,60) → offset=(0,20)")
        void testCalculateOffset_PositiveCoordinates() {
            Coord markerCoord = new Coord(50, 80);
            Coord portalCoord = new Coord(50, 60);

            Coord result = transformer.calculateOffset(markerCoord, portalCoord);

            assertEquals(0, result.x, "X offset should be 0");
            assertEquals(20, result.y, "Y offset should be 20");
        }

        @Test
        @DisplayName("Test with negative coordinates")
        void testCalculateOffset_NegativeCoordinates() {
            Coord markerCoord = new Coord(-30, -40);
            Coord portalCoord = new Coord(-10, -20);

            Coord result = transformer.calculateOffset(markerCoord, portalCoord);

            assertEquals(-20, result.x, "X offset should be -20");
            assertEquals(-20, result.y, "Y offset should be -20");
        }

        @Test
        @DisplayName("Test with mixed positive and negative coordinates")
        void testCalculateOffset_MixedCoordinates() {
            Coord markerCoord = new Coord(-25, 35);
            Coord portalCoord = new Coord(15, -45);

            Coord result = transformer.calculateOffset(markerCoord, portalCoord);

            assertEquals(-40, result.x, "X offset should be -40");
            assertEquals(80, result.y, "Y offset should be 80");
        }

        @Test
        @DisplayName("Test with zero offset (marker equals portal)")
        void testCalculateOffset_ZeroOffset() {
            Coord markerCoord = new Coord(100, 200);
            Coord portalCoord = new Coord(100, 200);

            Coord result = transformer.calculateOffset(markerCoord, portalCoord);

            assertEquals(0, result.x, "X offset should be 0");
            assertEquals(0, result.y, "Y offset should be 0");
        }
    }

    @Nested
    @DisplayName("applyOffset()")
    class ApplyOffsetTests {

        @Test
        @DisplayName("baseCoord=(45,55), offset=(0,20) → result=(45,75)")
        void testApplyOffset_PositiveOffset() {
            Coord baseCoord = new Coord(45, 55);
            Coord offset = new Coord(0, 20);

            Coord result = transformer.applyOffset(baseCoord, offset);

            assertEquals(45, result.x, "X coordinate should be 45");
            assertEquals(75, result.y, "Y coordinate should be 75");
        }

        @Test
        @DisplayName("Test with negative offset")
        void testApplyOffset_NegativeOffset() {
            Coord baseCoord = new Coord(100, 100);
            Coord offset = new Coord(-15, -25);

            Coord result = transformer.applyOffset(baseCoord, offset);

            assertEquals(85, result.x, "X coordinate should be 85");
            assertEquals(75, result.y, "Y coordinate should be 75");
        }

        @Test
        @DisplayName("Test with mixed positive and negative offset")
        void testApplyOffset_MixedOffset() {
            Coord baseCoord = new Coord(50, 50);
            Coord offset = new Coord(10, -30);

            Coord result = transformer.applyOffset(baseCoord, offset);

            assertEquals(60, result.x, "X coordinate should be 60");
            assertEquals(20, result.y, "Y coordinate should be 20");
        }

        @Test
        @DisplayName("Test with zero offset")
        void testApplyOffset_ZeroOffset() {
            Coord baseCoord = new Coord(75, 125);
            Coord offset = new Coord(0, 0);

            Coord result = transformer.applyOffset(baseCoord, offset);

            assertEquals(75, result.x, "X coordinate should remain 75");
            assertEquals(125, result.y, "Y coordinate should remain 125");
        }
    }

    @Nested
    @DisplayName("transformCoordinate()")
    class TransformCoordinateTests {

        @Test
        @DisplayName("Full transformation: markerCoord=(50,80), sourcePortalCoord=(50,60), targetPortalCoord=(45,55) → targetCoord=(45,75)")
        void testTransformCoordinate_FullTransformation() {
            Coord sourceMarkerCoord = new Coord(50, 80);
            Coord sourcePortalCoord = new Coord(50, 60);
            Coord targetPortalCoord = new Coord(45, 55);

            Coord result = transformer.transformCoordinate(sourceMarkerCoord, sourcePortalCoord, targetPortalCoord);

            assertEquals(45, result.x, "X coordinate should be 45");
            assertEquals(75, result.y, "Y coordinate should be 75");
        }

        @Test
        @DisplayName("Relative positions are preserved during transformation")
        void testTransformCoordinate_RelativePositionsPreserved() {
            // Marker is 10 units right and 15 units up from source portal
            Coord sourceMarkerCoord = new Coord(60, 75);
            Coord sourcePortalCoord = new Coord(50, 60);
            // Target portal at different location
            Coord targetPortalCoord = new Coord(100, 200);

            Coord result = transformer.transformCoordinate(sourceMarkerCoord, sourcePortalCoord, targetPortalCoord);

            // Result should maintain the same relative position: 10 right, 15 up from target portal
            assertEquals(110, result.x, "X coordinate should maintain relative position (100 + 10)");
            assertEquals(215, result.y, "Y coordinate should maintain relative position (200 + 15)");
        }

        @Test
        @DisplayName("Transformation with negative coordinates")
        void testTransformCoordinate_NegativeCoordinates() {
            Coord sourceMarkerCoord = new Coord(-50, -30);
            Coord sourcePortalCoord = new Coord(-40, -50);
            Coord targetPortalCoord = new Coord(10, 20);

            // offset = (-50 - (-40), -30 - (-50)) = (-10, 20)
            // result = (10 + (-10), 20 + 20) = (0, 40)
            Coord result = transformer.transformCoordinate(sourceMarkerCoord, sourcePortalCoord, targetPortalCoord);

            assertEquals(0, result.x, "X coordinate should be 0");
            assertEquals(40, result.y, "Y coordinate should be 40");
        }

        @Test
        @DisplayName("Transformation where source and target portals are the same")
        void testTransformCoordinate_SamePortals() {
            Coord sourceMarkerCoord = new Coord(75, 125);
            Coord sourcePortalCoord = new Coord(50, 100);
            Coord targetPortalCoord = new Coord(50, 100); // Same as source

            // offset = (75 - 50, 125 - 100) = (25, 25)
            // result = (50 + 25, 100 + 25) = (75, 125)
            Coord result = transformer.transformCoordinate(sourceMarkerCoord, sourcePortalCoord, targetPortalCoord);

            assertEquals(75, result.x, "X coordinate should equal source marker X");
            assertEquals(125, result.y, "Y coordinate should equal source marker Y");
        }
    }
}
