/*
 * Copyright (C) 2024 Nurgling Project
 *
 * This file is part of the Nurgling mod for Haven & Hearth.
 *
 * Redistribution and/or modification of this file is subject to the
 * terms of the GNU Lesser General Public License, version 3, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU Lesser General Public License is distributed
 * with this source code. If not, see <https://www.gnu.org/licenses/>.
 */

package nurgling.utils;

import haven.Coord;

/**
 * Implementation of coordinate transformation between different map layers or portals.
 * <p>
 * This class provides methods for calculating and applying coordinate offsets
 * when transforming positions from one coordinate system (e.g., source portal layer)
 * to another (e.g., target portal layer).
 * </p>
 * <p>
 * The transformation formula is:
 * <pre>
 *   offset = markerCoord - portalCoord
 *   targetCoord = targetPortalCoord + offset
 * </pre>
 * </p>
 * <p>
 * Usage example:
 * <pre>
 *   Coord targetCoord = CoordinateTransformer.instance.transformCoordinate(
 *       sourceMarkerCoord, sourcePortalCoord, targetPortalCoord
 *   );
 * </pre>
 * </p>
 */
public class CoordinateTransformer {
    /**
     * Static singleton instance for convenient access.
     */
    public static final CoordinateTransformer instance = new CoordinateTransformer();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private CoordinateTransformer() {
    }

    /**
     * Transforms a coordinate from a source marker position to a target coordinate system.
     * <p>
     * This method calculates the offset from the source portal to the marker,
     * then applies that offset to the target portal coordinate to get the
     * transformed coordinate on the target layer.
     * </p>
     * <p>
     * Formula: {@code targetCoord = targetPortalCoord + (sourceMarkerCoord - sourcePortalCoord)}
     * </p>
     *
     * @param sourceMarkerCoord the coordinate of the marker on the source layer
     * @param sourcePortalCoord the coordinate of the portal on the source layer
     * @param targetPortalCoord the coordinate of the portal on the target layer
     * @return the transformed coordinate on the target layer
     */
    public Coord transformCoordinate(Coord sourceMarkerCoord, Coord sourcePortalCoord, Coord targetPortalCoord) {
        Coord offset = calculateOffset(sourceMarkerCoord, sourcePortalCoord);
        Coord result = applyOffset(targetPortalCoord, offset);

        dprint("Transform coordinate: sourceMarker=%s, sourcePortal=%s, targetPortal=%s, offset=%s, result=%s",
                sourceMarkerCoord, sourcePortalCoord, targetPortalCoord, offset, result);

        return result;
    }

    /**
     * Calculates the offset vector from a portal coordinate to a marker coordinate.
     * <p>
     * The offset represents the relative position of the marker with respect to the portal.
     * </p>
     * <p>
     * Formula: {@code offset = markerCoord - portalCoord}
     * </p>
     *
     * @param markerCoord the coordinate of the marker
     * @param portalCoord the coordinate of the portal
     * @return the offset vector from portal to marker
     */
    public Coord calculateOffset(Coord markerCoord, Coord portalCoord) {
        Coord offset = markerCoord.sub(portalCoord);

        dprint("Calculate offset: markerCoord=%s, portalCoord=%s, offset=%s",
                markerCoord, portalCoord, offset);

        return offset;
    }

    /**
     * Applies an offset vector to a base coordinate.
     * <p>
     * This method adds the offset to the base coordinate to produce a new coordinate.
     * </p>
     * <p>
     * Formula: {@code resultCoord = baseCoord + offset}
     * </p>
     *
     * @param baseCoord the base coordinate to apply the offset to
     * @param offset the offset vector to apply
     * @return the resulting coordinate after applying the offset
     */
    public Coord applyOffset(Coord baseCoord, Coord offset) {
        Coord result = baseCoord.add(offset);

        dprint("Apply offset: baseCoord=%s, offset=%s, result=%s",
                baseCoord, offset, result);

        return result;
    }

    /**
     * Debug print method for logging transformation operations.
     * <p>
     * This method prints debug messages to standard output.
     * In production, this can be replaced with SLF4J logging (see task T027).
     * </p>
     *
     * @param format the format string
     * @param args the arguments to be formatted
     */
    private void dprint(String format, Object... args) {
        System.out.printf("[CoordinateTransformer] " + format + "%n", args);
    }
}
