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
import nurgling.navigation.ChunkPortal;
import nurgling.teleportation.TeleportationEvent;

/**
 * Interface for managing linked portal markers across layer transitions.
 * <p>
 * When a player teleports between layers (e.g., surface to underground),
 * this manager creates two linked markers:
 * </p>
 * <ul>
 *   <li>An OUT marker on the source layer (where the player departed)</li>
 *   <li>An IN marker on the target layer (where the player arrived)</li>
 * </ul>
 * <p>
 * Both markers share a common UID to establish the link between them.
 * </p>
 *
 * @see Direction
 * @see PortalType
 */
public interface PortalLinkManager {

    /**
     * Creates a pair of linked markers for a layer transition teleportation event.
     * <p>
     * This method creates two markers with a shared UID:
     * </p>
     * <ul>
     *   <li>OUT marker at the source location (surface/upper layer)</li>
     *   <li>IN marker at the target location (underground/lower layer)</li>
     * </ul>
     *
     * @param event the teleportation event containing source and target information
     * @param portalType the type of portal used for the transition
     * @return the shared UID linking both markers
     * @throws IllegalArgumentException if event is null or not a layer transition
     * @see TeleportationEvent
     * @see Direction
     */
    String createLinkedMarkers(TeleportationEvent event, ChunkPortal.PortalType portalType);

    /**
     * Creates a single portal marker at the specified location.
     *
     * @param gridId the grid ID where the marker should be placed
     * @param coord the coordinates within the grid
     * @param direction the direction of the portal (IN or OUT)
     * @param portalType the type of portal
     * @param uid the unique identifier linking this marker to its pair
     * @param markerName the display name for the marker
     */
    void createMarker(long gridId, Coord coord, Direction direction,
                      ChunkPortal.PortalType portalType, String uid, String markerName);

    /**
     * Returns the UID of the last created linked marker pair.
     *
     * @return the last UID, or null if no linked markers have been created
     */
    String getLastCreatedUid();

    /**
     * Clears the stored state including the last created UID.
     */
    void clear();
}
