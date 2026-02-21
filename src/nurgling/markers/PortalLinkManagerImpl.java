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
import nurgling.teleportation.LayerTransitionDetector;
import nurgling.teleportation.TeleportationEvent;
import nurgling.utils.CoordinateTransformer;
import nurgling.utils.UidGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link PortalLinkManager} for managing linked portal markers
 * across layer transitions.
 * <p>
 * This manager handles the creation and management of linked portal markers when
 * a player teleports between layers (e.g., surface to underground). It creates
 * pairs of markers with a shared unique identifier (UID):
 * </p>
 * <ul>
 *   <li>An OUT marker on the source layer (where the player departed)</li>
 *   <li>An IN marker on the target layer (where the player arrived)</li>
 * </ul>
 * <p>
 * The implementation provides O(1) lookup for links by UID using a HashMap,
 * and supports coordinate transformation for markers during teleportation events.
 * </p>
 *
 * @author nurgling
 * @version 1.0
 * @see PortalLink
 * @see PortalMarker
 * @see CoordinateTransformer
 */
public class PortalLinkManagerImpl implements PortalLinkManager {

    /**
     * HashMap for O(1) lookup of links by UID.
     * Key: 6-character alphanumeric link UID
     * Value: PortalLink object containing source and target markers
     */
    private final Map<String, PortalLink> linksByUid;

    /**
     * Coordinate transformer for transforming marker coordinates between layers.
     */
    private final CoordinateTransformer transformer;

    /**
     * Layer transition detector for checking if events are layer transitions.
     */
    private final LayerTransitionDetector layerTransitionDetector;

    /**
     * Marker storage for managing portal markers.
     * Key: gridId
     * Value: Map of coordinate to PortalMarker
     */
    private final Map<Long, Map<Coord, PortalMarker>> markersByGrid;

    /**
     * The UID of the last created linked marker pair.
     */
    private String lastCreatedUid;

    /**
     * Constructs a new PortalLinkManagerImpl with default dependencies.
     * <p>
     * Initializes the links HashMap, coordinate transformer, and layer transition detector.
     * </p>
     */
    public PortalLinkManagerImpl() {
        this.linksByUid = new HashMap<>();
        this.transformer = CoordinateTransformer.instance;
        this.layerTransitionDetector = new nurgling.teleportation.LayerTransitionDetectorImpl();
        this.markersByGrid = new HashMap<>();
        this.lastCreatedUid = null;
        dprint("PortalLinkManagerImpl initialized");
    }

    /**
     * Constructs a new PortalLinkManagerImpl with specified dependencies.
     * <p>
     * This constructor allows injection of dependencies for testing purposes.
     * </p>
     *
     * @param layerTransitionDetector the layer transition detector to use
     */
    public PortalLinkManagerImpl(LayerTransitionDetector layerTransitionDetector) {
        this.linksByUid = new HashMap<>();
        this.transformer = CoordinateTransformer.instance;
        this.layerTransitionDetector = layerTransitionDetector;
        this.markersByGrid = new HashMap<>();
        this.lastCreatedUid = null;
        dprint("PortalLinkManagerImpl initialized with custom LayerTransitionDetector");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a pair of linked markers for a layer transition teleportation event.
     * This method:
     * </p>
     * <ol>
     *   <li>Validates that the event is a layer transition</li>
     *   <li>Generates a unique 6-character alphanumeric UID</li>
     *   <li>Creates an OUT marker on the source layer</li>
     *   <li>Creates an IN marker on the target layer</li>
     *   <li>Links both markers with the shared UID</li>
     *   <li>Stores the link in the internal map</li>
     * </ol>
     *
     * @param event the teleportation event containing source and target information
     * @param portalType the type of portal used for the transition
     * @return the shared UID linking both markers
     * @throws IllegalArgumentException if event is null or not a layer transition
     * @see TeleportationEvent
     * @see Direction
     */
    @Override
    public String createLinkedMarkers(TeleportationEvent event, ChunkPortal.PortalType portalType) {
        Objects.requireNonNull(event, "TeleportationEvent must not be null");
        dprint("Creating linked markers for event: %s, portalType: %s", event, portalType);

        // Validate that this is a layer transition
        if (!isLayerTransition(event)) {
            throw new IllegalArgumentException(
                String.format("Event is not a layer transition: %s", event));
        }

        // Generate unique UID with collision check
        String linkUid = generateUid();
        dprint("Generated link UID: %s", linkUid);

        long timestamp = System.currentTimeMillis();

        // Create source marker (OUT direction - departure point)
        PortalMarker sourceMarker = createMarkerInternal(
            event.getSourceGridId(),
            event.getSourceCoord(),
            Direction.OUT,
            portalType,
            linkUid,
            "Portal OUT",
            getLayerForGrid(event.getSourceGridId())
        );

        // Create target marker (IN direction - arrival point)
        PortalMarker targetMarker = createMarkerInternal(
            event.getTargetGridId(),
            event.getTargetCoord(),
            Direction.IN,
            portalType,
            linkUid,
            "Portal IN",
            getLayerForGrid(event.getTargetGridId())
        );

        // Create the link
        int sourceLayer = sourceMarker.getLayer();
        int targetLayer = targetMarker.getLayer();

        PortalLink link = new PortalLink(linkUid, sourceMarker, targetMarker,
                                         sourceLayer, targetLayer, timestamp);

        // Store the link
        linksByUid.put(linkUid, link);
        lastCreatedUid = linkUid;

        dprint("Created linked markers: source=%s, target=%s, linkUid=%s",
               sourceMarker, targetMarker, linkUid);

        return linkUid;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a single portal marker at the specified location.
     * This method is typically called internally by createLinkedMarkers(),
     * but can also be used to create standalone markers.
     * </p>
     *
     * @param gridId the grid ID where the marker should be placed
     * @param coord the coordinates within the grid
     * @param direction the direction of the portal (IN or OUT)
     * @param portalType the type of portal
     * @param uid the unique identifier linking this marker to its pair (or null for unlinked)
     * @param markerName the display name for the marker
     */
    @Override
    public void createMarker(long gridId, Coord coord, Direction direction,
                             ChunkPortal.PortalType portalType, String uid, String markerName) {
        int layer = getLayerForGrid(gridId);
        createMarkerInternal(gridId, coord, direction, portalType, uid, markerName, layer);
        dprint("Created marker: gridId=%d, coord=%s, direction=%s, uid=%s",
               gridId, coord, direction, uid);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the UID of the last created linked marker pair.
     * </p>
     *
     * @return the last UID, or null if no linked markers have been created
     */
    @Override
    public String getLastCreatedUid() {
        return lastCreatedUid;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears the stored state including all links, markers, and the last created UID.
     * </p>
     */
    @Override
    public void clear() {
        dprint("Clearing PortalLinkManager state");
        linksByUid.clear();
        markersByGrid.clear();
        lastCreatedUid = null;
    }

    /**
     * Creates a link between two existing portal markers.
     * <p>
     * This method validates that the markers are on adjacent layers
     * (|sourceLayer - targetLayer| == 1), generates a unique UID,
     * sets the direction for each marker, and creates a PortalLink.
     * </p>
     *
     * @param sourceMarker the source marker (will be set to OUT direction)
     * @param targetMarker the target marker (will be set to IN direction)
     * @return the created PortalLink
     * @throws IllegalArgumentException if markers are not on adjacent layers
     * @see PortalLink
     */
    public PortalLink createLink(PortalMarker sourceMarker, PortalMarker targetMarker) {
        Objects.requireNonNull(sourceMarker, "Source marker must not be null");
        Objects.requireNonNull(targetMarker, "Target marker must not be null");

        int sourceLayer = sourceMarker.getLayer();
        int targetLayer = targetMarker.getLayer();

        // Validate: markers must be on adjacent layers
        if (Math.abs(sourceLayer - targetLayer) != 1) {
            throw new IllegalArgumentException(
                String.format("Markers must be on adjacent layers (|sourceLayer - targetLayer| == 1). " +
                              "Source layer: %d, Target layer: %d", sourceLayer, targetLayer));
        }

        // Generate unique UID with collision check
        String linkUid = generateUid();
        dprint("Creating link with UID: %s between markers on layers %d and %d",
               linkUid, sourceLayer, targetLayer);

        // Set directions
        sourceMarker.setDirection(Direction.OUT);
        targetMarker.setDirection(Direction.IN);

        // Set link UID on both markers
        sourceMarker.setLinkUid(linkUid);
        targetMarker.setLinkUid(linkUid);

        // Create the link
        long timestamp = System.currentTimeMillis();
        PortalLink link = new PortalLink(linkUid, sourceMarker, targetMarker,
                                         sourceLayer, targetLayer, timestamp);

        // Store the link
        linksByUid.put(linkUid, link);

        dprint("Link created: %s", link);

        return link;
    }

    /**
     * Gets the linked marker(s) for a given marker.
     * <p>
     * If the marker has a linkUid, finds the link and returns the other marker.
     * If the marker is not linked, returns an empty list.
     * </p>
     *
     * @param marker the marker to find linked markers for
     * @return a list containing the linked marker (if any), or empty list
     * @see PortalMarker
     */
    public List<PortalMarker> getLinkedMarkers(PortalMarker marker) {
        if (marker == null || marker.getLinkUid() == null) {
            return new ArrayList<>();
        }

        String linkUid = marker.getLinkUid();
        PortalLink link = linksByUid.get(linkUid);

        if (link == null) {
            dprint("No link found for UID: %s", linkUid);
            return new ArrayList<>();
        }

        try {
            PortalMarker other = link.getOtherMarker(marker);
            List<PortalMarker> result = new ArrayList<>();
            result.add(other);
            dprint("Found linked marker: %s", other);
            return result;
        } catch (IllegalArgumentException e) {
            // Marker is not part of this link
            dprint("Marker is not part of link %s: %s", linkUid, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gets a link by its unique identifier.
     * <p>
     * This method provides O(1) lookup using the internal HashMap.
     * </p>
     *
     * @param linkUid the 6-character alphanumeric link UID
     * @return Optional containing the PortalLink if found, or empty Optional
     * @see PortalLink
     */
    public Optional<PortalLink> getLinkByUid(String linkUid) {
        if (linkUid == null || linkUid.isEmpty()) {
            return Optional.empty();
        }

        PortalLink link = linksByUid.get(linkUid);
        if (link != null) {
            dprint("Found link for UID: %s", linkUid);
        } else {
            dprint("No link found for UID: %s", linkUid);
        }
        return Optional.ofNullable(link);
    }

    /**
     * Processes a teleportation event and creates linked markers.
     * <p>
     * This method handles layer transition events by:
     * </p>
     * <ol>
     *   <li>Checking if the event is a layer transition</li>
     *   <li>Getting source and target portal coordinates</li>
     *   <li>For each marker on the source layer near the portal:
     *     <ul>
     *       <li>Calculating offset from portal to marker</li>
     *       <li>Transforming to target layer coordinates</li>
     *       <li>Checking if a marker exists at target coordinates</li>
     *       <li>If exists: linking the markers</li>
     *       <li>If not exists: creating a new marker on target layer</li>
     *     </ul>
     *   </li>
     *   <li>Saving all created links</li>
     * </ol>
     *
     * @param event the teleportation event to process
     * @return true if the event was processed as a layer transition, false otherwise
     * @see TeleportationEvent
     * @see CoordinateTransformer
     */
    public boolean processTeleportation(TeleportationEvent event) {
        Objects.requireNonNull(event, "TeleportationEvent must not be null");
        dprint("Processing teleportation event: %s", event);

        // Check if this is a layer transition
        if (!isLayerTransition(event)) {
            dprint("Not a layer transition, skipping processing");
            return false;
        }

        Coord sourcePortalCoord = event.getSourceCoord();
        Coord targetPortalCoord = event.getTargetCoord();
        long sourceGridId = event.getSourceGridId();
        long targetGridId = event.getTargetGridId();

        dprint("Layer transition detected: sourceGrid=%d, targetGrid=%d, " +
               "sourcePortal=%s, targetPortal=%s",
               sourceGridId, targetGridId, sourcePortalCoord, targetPortalCoord);

        // Get markers on source layer near the portal
        Map<Coord, PortalMarker> sourceMarkers = markersByGrid.getOrDefault(sourceGridId, new HashMap<>());

        // Process each marker on source layer
        for (Map.Entry<Coord, PortalMarker> entry : sourceMarkers.entrySet()) {
            Coord markerCoord = entry.getKey();
            PortalMarker sourceMarker = entry.getValue();

            // Calculate offset from portal to marker
            Coord offset = transformer.calculateOffset(markerCoord, sourcePortalCoord);
            dprint("Marker offset from portal: %s", offset);

            // Transform to target layer coordinates
            Coord targetCoord = transformer.applyOffset(targetPortalCoord, offset);
            dprint("Transformed target coordinate: %s", targetCoord);

            // Check if marker exists at target coordinates
            Map<Coord, PortalMarker> targetMarkers = markersByGrid.getOrDefault(targetGridId, new HashMap<>());
            PortalMarker existingTargetMarker = targetMarkers.get(targetCoord);

            if (existingTargetMarker != null) {
                // Link existing markers
                dprint("Linking existing markers: source=%s, target=%s",
                       sourceMarker, existingTargetMarker);
                createLink(sourceMarker, existingTargetMarker);
            } else {
                // Create new marker on target layer
                dprint("Creating new marker on target layer at %s", targetCoord);

                ChunkPortal.PortalType portalType = getPortalTypeFromEvent(event);
                int targetLayer = getLayerForGrid(targetGridId);

                PortalMarker targetMarker = createMarkerInternal(
                    targetGridId,
                    targetCoord,
                    Direction.IN,
                    portalType != null ? portalType : ChunkPortal.PortalType.CAVE,
                    null,  // Will be set by createLink
                    "Portal",
                    targetLayer
                );

                // Create link between source and new target marker
                createLink(sourceMarker, targetMarker);
            }
        }

        dprint("Teleportation processing complete");
        return true;
    }

    /**
     * Generates a unique 6-character alphanumeric UID.
     * <p>
     * Uses {@link UidGenerator#generateUid()} and checks for collisions
     * in the existing links map. If a collision is detected, generates
     * a new UID until a unique one is found.
     * </p>
     *
     * @return a unique 6-character alphanumeric string
     * @see UidGenerator
     */
    public String generateUid() {
        String uid;
        int maxAttempts = 100;
        int attempts = 0;

        do {
            uid = UidGenerator.generateUid();
            attempts++;

            if (attempts >= maxAttempts) {
                dprint("Warning: Generated %d UIDs without finding unique one. " +
                       "Current links count: %d", attempts, linksByUid.size());
                break;
            }
        } while (linksByUid.containsKey(uid));

        dprint("Generated unique UID: %s (attempts: %d)", uid, attempts);
        return uid;
    }

    /**
     * Checks if a teleportation event is a layer transition.
     * <p>
     * Delegates to the LayerTransitionDetector for validation.
     * </p>
     *
     * @param event the teleportation event to check
     * @return true if the event is a layer transition, false otherwise
     */
    private boolean isLayerTransition(TeleportationEvent event) {
        return layerTransitionDetector != null &&
               layerTransitionDetector.isLayerTransition(event);
    }

    /**
     * Creates a portal marker internally and stores it in the marker map.
     *
     * @param gridId the grid ID where the marker should be placed
     * @param coord the coordinates within the grid
     * @param direction the direction of the portal
     * @param portalType the type of portal
     * @param uid the unique identifier
     * @param markerName the display name
     * @param layer the map layer number
     * @return the created PortalMarker
     */
    private PortalMarker createMarkerInternal(long gridId, Coord coord, Direction direction,
                                               ChunkPortal.PortalType portalType, String uid,
                                               String markerName, int layer) {
        long timestamp = System.currentTimeMillis();

        // Convert ChunkPortal.PortalType to markers.PortalType
        PortalType markerPortalType = convertPortalType(portalType);

        // Get icon based on portal type
        haven.Resource.Image icon = getIconForPortalType(markerPortalType);

        PortalMarker marker = new PortalMarker(
            markerName,
            markerPortalType,
            gridId,
            coord,
            uid,
            direction,
            layer,
            timestamp,
            icon
        );

        // Store marker
        markersByGrid.computeIfAbsent(gridId, k -> new HashMap<>()).put(coord, marker);

        return marker;
    }

    /**
     * Converts ChunkPortal.PortalType to markers.PortalType.
     *
     * @param portalType the ChunkPortal.PortalType to convert
     * @return the corresponding markers.PortalType
     */
    private PortalType convertPortalType(ChunkPortal.PortalType portalType) {
        if (portalType == null) {
            return PortalType.CAVE;
        }
        switch (portalType) {
            case MINEHOLE:
                return PortalType.MINEHOLE;
            case LADDER:
                return PortalType.LADDER;
            case CAVE:
            default:
                return PortalType.CAVE;
        }
    }

    /**
     * Gets the icon for a portal type.
     * <p>
     * Returns null as icons are loaded from resources and may not be
     * available in all contexts.
     * </p>
     *
     * @param portalType the portal type
     * @return the Resource.Image for the portal type, or null
     */
    private haven.Resource.Image getIconForPortalType(PortalType portalType) {
        // Icons would be loaded from resources here
        // For now, return null as icon loading requires game context
        return null;
    }

    /**
     * Gets the layer number for a grid ID.
     * <p>
     * This is a simplified implementation that returns 0 for all grids.
     * In a full implementation, this would look up the layer from a grid registry.
     * </p>
     *
     * @param gridId the grid ID
     * @return the layer number (0 = surface, 1+ = underground)
     */
    private int getLayerForGrid(long gridId) {
        // Simplified: return 0 (surface) by default
        // In production, this would look up the actual layer from grid data
        return 0;
    }

    /**
     * Gets the portal type from a teleportation event.
     *
     * @param event the teleportation event
     * @return the PortalType, or null if not determinable
     */
    private ChunkPortal.PortalType getPortalTypeFromEvent(TeleportationEvent event) {
        if (layerTransitionDetector != null) {
            return layerTransitionDetector.getPortalType(event);
        }
        return null;
    }

    /**
     * Debug print method for logging.
     * <p>
     * This method prints debug messages to standard output.
     * In production, this can be replaced with SLF4J logging.
     * </p>
     *
     * @param format the format string
     * @param args the arguments to be formatted
     */
    private void dprint(String format, Object... args) {
        System.out.printf("[PortalLinkManager] " + format + "%n", args);
    }
}
