package nurgling.navigation;

import haven.Coord2d;
import haven.MapFile;

/**
 * Represents a detected transition between map layers (segments).
 *
 * This immutable data class captures all information needed to create
 * linked portal markers:
 * - Source and destination segment IDs (for UID generation via XOR)
 * - Portal coordinates (for UID generation and marker positioning)
 * - Portal resource name (for icon selection and direction detection)
 * - Timestamp and player position (for logging and debugging)
 * - Portal GridInfo (for marker coordinate computation without getgridt)
 *
 * Layer transitions are detected by PortalMarkerTracker when:
 * 1. Player's grid ID changes
 * 2. Player was near a portal before the transition
 * 3. The transition is not a teleport (Hearthfire, totem, signpost)
 *
 * Example transition: Surface (segmentId=1) → Cave (segmentId=12345)
 * - fromSegmentId: 1 (surface)
 * - toSegmentId: 12345 (underground)
 * - direction: IN (entering underground)
 * - UID generated from: (1 ^ 12345) : portalX : portalY
 */
public class LayerTransition {

    /**
     * Source segment ID (before transition).
     * Used in UID generation: XOR with toSegmentId.
     */
    public final long fromSegmentId;

    /**
     * Destination segment ID (after transition).
     * Used in UID generation: XOR with fromSegmentId.
     */
    public final long toSegmentId;

    /**
     * World coordinates of the portal used for transition.
     * Used in UID generation and marker positioning.
     */
    public final Coord2d portalCoordinates;

    /**
     * Resource name of the portal (e.g., "gfx/terobjs/cave").
     * Used for icon selection and direction detection.
     */
    public final String portalName;

    /**
     * Timestamp when transition was detected (milliseconds since epoch).
     */
    public final long timestamp;

    /**
     * Player's position when portal was clicked (for IN marker).
     */
    public final Coord2d playerPositionAtPortal;

    /**
     * Player's position after transition (for OUT marker).
     */
    public final Coord2d playerPositionAfterTransition;

    /**
     * Portal's GridInfo captured BEFORE transition.
     * Used for marker coordinate computation without getgridt.
     */
    public final MapFile.GridInfo portalGridInfo;

    /**
     * Creates a new LayerTransition instance.
     *
     * @param fromSegmentId source segment ID
     * @param toSegmentId destination segment ID
     * @param portalCoordinates portal world coordinates
     * @param portalName portal resource name
     * @param playerPositionAtPortal player's position when portal was clicked (for IN marker)
     * @param playerPositionAfterTransition player's position after transition (for OUT marker)
     * @param portalGridInfo portal's GridInfo captured before transition (can be null)
     */
    public LayerTransition(long fromSegmentId, long toSegmentId, Coord2d portalCoordinates,
                           String portalName, Coord2d playerPositionAtPortal, Coord2d playerPositionAfterTransition,
                           MapFile.GridInfo portalGridInfo) {
        this.fromSegmentId = fromSegmentId;
        this.toSegmentId = toSegmentId;
        this.portalCoordinates = portalCoordinates;
        this.portalName = portalName;
        this.playerPositionAtPortal = playerPositionAtPortal;
        this.playerPositionAfterTransition = playerPositionAfterTransition;
        this.portalGridInfo = portalGridInfo;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a new LayerTransition instance without GridInfo (backward compatibility).
     */
    public LayerTransition(long fromSegmentId, long toSegmentId, Coord2d portalCoordinates,
                           String portalName, Coord2d playerPositionAtPortal, Coord2d playerPositionAfterTransition) {
        this(fromSegmentId, toSegmentId, portalCoordinates, portalName, playerPositionAtPortal,
             playerPositionAfterTransition, null);
    }

    /**
     * Computes XOR of segment pair for UID generation.
     * XOR is commutative, ensuring same UID regardless of travel direction.
     *
     * @return XOR of fromSegmentId and toSegmentId
     */
    public long getXorSegmentPair() {
        return fromSegmentId ^ toSegmentId;
    }

    /**
     * Checks if this is a surface-to-underground transition.
     *
     * @return true if fromSegmentId is surface (1) and toSegmentId is not
     */
    public boolean isSurfaceToUnderground() {
        return fromSegmentId == 1 && toSegmentId != 1;
    }

    /**
     * Checks if this is an underground-to-surface transition.
     *
     * @return true if fromSegmentId is not surface (1) and toSegmentId is
     */
    public boolean isUndergroundToSurface() {
        return fromSegmentId != 1 && toSegmentId == 1;
    }

    /**
     * Checks if this is an underground-to-underground transition.
     * (e.g., minehole/ladder between dungeon levels)
     *
     * @return true if both segments are not surface
     */
    public boolean isUndergroundToUnderground() {
        return fromSegmentId != 1 && toSegmentId != 1;
    }

    @Override
    public String toString() {
        return String.format("LayerTransition{from=%d, to=%d, portal=%s, coords=(%.0f,%.0f)}",
            fromSegmentId, toSegmentId, portalName,
            portalCoordinates != null ? portalCoordinates.x : -1,
            portalCoordinates != null ? portalCoordinates.y : -1);
    }
}
