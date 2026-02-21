package nurgling.markers;

/**
 * Represents a link between two portal markers on adjacent layers.
 * A PortalLink connects an OUT marker on the source layer to an IN marker
 * on the target layer, enabling player transition between map levels.
 * <p>
 * Each link has a unique identifier (linkUid) and maintains references to both
 * connected markers. The link enforces validation rules to ensure proper
 * portal connections between adjacent layers.
 * </p>
 * <p>
 * Validation rules:
 * <ul>
 *   <li>sourceLayer and targetLayer must differ by exactly 1 (adjacent levels)</li>
 *   <li>sourceMarker.direction must be OUT (departure point)</li>
 *   <li>targetMarker.direction must be IN (arrival point)</li>
 * </ul>
 * </p>
 *
 * @author nurgling
 * @version 1.0
 */
public class PortalLink {

    /**
     * Unique 6-character identifier for this link (primary key).
     * Used to associate this link with related data structures.
     */
    private final String linkUid;

    /**
     * Marker on the source layer (OUT direction).
     * This is the departure point where the player enters the portal.
     */
    private final PortalMarker sourceMarker;

    /**
     * Marker on the target layer (IN direction).
     * This is the arrival point where the player exits the portal.
     */
    private final PortalMarker targetMarker;

    /**
     * Source layer number.
     * The layer where the player departs from (OUT marker location).
     */
    private final int sourceLayer;

    /**
     * Target layer number.
     * The layer where the player arrives at (IN marker location).
     */
    private final int targetLayer;

    /**
     * Unix timestamp (milliseconds) when this link was created.
     * Used for tracking link age and sorting by creation time.
     */
    private final long createdTimestamp;

    /**
     * Unix timestamp (milliseconds) when this link was last accessed for switching.
     * Used for tracking usage patterns and optimizing frequently-used portals.
     */
    private long lastAccessedTimestamp;

    /**
     * Creates a new PortalLink with validation.
     * Validates that:
     * <ul>
     *   <li>sourceLayer and targetLayer differ by exactly 1</li>
     *   <li>sourceMarker.direction is OUT</li>
     *   <li>targetMarker.direction is IN</li>
     * </ul>
     *
     * @param linkUid the unique 6-character identifier for this link
     * @param sourceMarker the marker on the source layer (must have OUT direction)
     * @param targetMarker the marker on the target layer (must have IN direction)
     * @param sourceLayer the source layer number
     * @param targetLayer the target layer number
     * @param createdTimestamp the Unix timestamp when the link was created
     * @throws IllegalArgumentException if validation fails:
     *         - layers are not adjacent (differ by exactly 1)
     *         - sourceMarker direction is not OUT
     *         - targetMarker direction is not IN
     */
    public PortalLink(String linkUid, PortalMarker sourceMarker, PortalMarker targetMarker,
                      int sourceLayer, int targetLayer, long createdTimestamp) {
        // Validate layers are adjacent
        if (Math.abs(sourceLayer - targetLayer) != 1) {
            throw new IllegalArgumentException(
                String.format("Source layer (%d) and target layer (%d) must be adjacent (differ by exactly 1)",
                    sourceLayer, targetLayer));
        }

        // Validate source marker direction is OUT
        if (sourceMarker.getDirection() != Direction.OUT) {
            throw new IllegalArgumentException(
                String.format("Source marker direction must be OUT, but was %s",
                    sourceMarker.getDirection()));
        }

        // Validate target marker direction is IN
        if (targetMarker.getDirection() != Direction.IN) {
            throw new IllegalArgumentException(
                String.format("Target marker direction must be IN, but was %s",
                    targetMarker.getDirection()));
        }

        this.linkUid = linkUid;
        this.sourceMarker = sourceMarker;
        this.targetMarker = targetMarker;
        this.sourceLayer = sourceLayer;
        this.targetLayer = targetLayer;
        this.createdTimestamp = createdTimestamp;
        this.lastAccessedTimestamp = createdTimestamp; // Initialize to creation time
    }

    /**
     * Gets the unique identifier for this link.
     *
     * @return the 6-character linkUid
     */
    public String getLinkUid() {
        return linkUid;
    }

    /**
     * Gets the source marker (OUT direction).
     *
     * @return the PortalMarker on the source layer
     */
    public PortalMarker getSourceMarker() {
        return sourceMarker;
    }

    /**
     * Gets the target marker (IN direction).
     *
     * @return the PortalMarker on the target layer
     */
    public PortalMarker getTargetMarker() {
        return targetMarker;
    }

    /**
     * Gets the source layer number.
     *
     * @return the source layer number (departure layer)
     */
    public int getSourceLayer() {
        return sourceLayer;
    }

    /**
     * Gets the target layer number.
     *
     * @return the target layer number (arrival layer)
     */
    public int getTargetLayer() {
        return targetLayer;
    }

    /**
     * Gets the timestamp when this link was created.
     *
     * @return the Unix timestamp in milliseconds
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Gets the timestamp when this link was last accessed for switching.
     *
     * @return the Unix timestamp in milliseconds
     */
    public long getLastAccessedTimestamp() {
        return lastAccessedTimestamp;
    }

    /**
     * Updates the last accessed timestamp to the current time.
     * Should be called when the link is used for player transition.
     *
     * @param timestamp the new timestamp to set
     */
    public void updateLastAccessedTimestamp(long timestamp) {
        this.lastAccessedTimestamp = timestamp;
    }

    /**
     * Gets the marker for the specified layer.
     * Returns the source marker if the layer matches sourceLayer,
     * or the target marker if the layer matches targetLayer.
     *
     * @param layer the layer number to get the marker for
     * @return the PortalMarker for the specified layer
     * @throws IllegalArgumentException if the layer is neither source nor target layer
     */
    public PortalMarker getMarker(int layer) {
        if (layer == sourceLayer) {
            return sourceMarker;
        } else if (layer == targetLayer) {
            return targetMarker;
        } else {
            throw new IllegalArgumentException(
                String.format("Layer %d is not part of this link (source=%d, target=%d)",
                    layer, sourceLayer, targetLayer));
        }
    }

    /**
     * Gets the other marker given one of the link's markers.
     * Returns the opposite marker from the one provided.
     *
     * @param one the marker to get the opposite of (must be either sourceMarker or targetMarker)
     * @return the other PortalMarker (target if one is source, source if one is target)
     * @throws IllegalArgumentException if the provided marker is not part of this link
     */
    public PortalMarker getOtherMarker(PortalMarker one) {
        if (one == null) {
            throw new IllegalArgumentException("Marker cannot be null");
        }
        if (one == sourceMarker) {
            return targetMarker;
        } else if (one == targetMarker) {
            return sourceMarker;
        } else {
            throw new IllegalArgumentException(
                "Provided marker is not part of this link");
        }
    }

    /**
     * Returns a string representation of this PortalLink for debugging purposes.
     * Includes all key fields: linkUid, source/target markers, layers, and timestamps.
     *
     * @return a formatted string containing the link's properties
     */
    @Override
    public String toString() {
        return String.format("PortalLink{linkUid='%s', sourceMarker=%s, targetMarker=%s, sourceLayer=%d, targetLayer=%d, createdTimestamp=%d, lastAccessedTimestamp=%d}",
                linkUid,
                sourceMarker != null ? sourceMarker.toString() : "null",
                targetMarker != null ? targetMarker.toString() : "null",
                sourceLayer,
                targetLayer,
                createdTimestamp,
                lastAccessedTimestamp);
    }
}
