package nurgling.navigation;

import haven.Coord;

/**
 * Represents a linked pair of portal markers across map layers.
 * 
 * A PortalMarkerLink connects two markers:
 * - Entrance marker (IN direction) on the source segment
 * - Exit marker (OUT direction) on the destination segment
 * 
 * Both markers share the same UID, making them identifiable as a pair.
 * 
 * Example:
 * - Cave on surface: "Cave ABC123 IN" at (512, 1024) on segment 1
 * - Cave underground: "Cave ABC123 OUT" at (300, 800) on segment 12345
 * - UID: "ABC123"
 * 
 * Links are created by PortalMarkerLinker when player traverses a portal.
 * Subsequent traversals through the same portal will find existing links
 * and skip marker creation (deduplication).
 */
public class PortalMarkerLink {
    
    /**
     * Unique identifier for this link (6 alphanumeric characters).
     * Same for both entrance and exit markers.
     */
    public final String uid;
    
    /**
     * Portal resource name (e.g., "gfx/terobjs/cave").
     */
    public final String portalName;
    
    /**
     * Entrance marker ID (IN direction).
     * May be -1 if marker creation failed or not yet created.
     */
    public final long entranceMarkerId;
    
    /**
     * Entrance marker segment ID.
     */
    public final long entranceSegmentId;
    
    /**
     * Entrance marker coordinates.
     */
    public final Coord entranceCoords;
    
    /**
     * Exit marker ID (OUT direction).
     * May be -1 if marker creation failed or not yet created.
     */
    public final long exitMarkerId;
    
    /**
     * Exit marker segment ID.
     */
    public final long exitSegmentId;
    
    /**
     * Exit marker coordinates.
     */
    public final Coord exitCoords;
    
    /**
     * Timestamp when link was created (milliseconds since epoch).
     */
    public final long createdAt;
    
    /**
     * Creates a new PortalMarkerLink instance.
     * 
     * @param uid unique identifier (6 alphanumeric characters)
     * @param portalName portal resource name
     * @param entranceMarkerId entrance marker ID
     * @param entranceSegmentId entrance segment ID
     * @param entranceCoords entrance marker coordinates
     * @param exitMarkerId exit marker ID
     * @param exitSegmentId exit segment ID
     * @param exitCoords exit marker coordinates
     */
    public PortalMarkerLink(String uid, String portalName,
                            long entranceMarkerId, long entranceSegmentId, Coord entranceCoords,
                            long exitMarkerId, long exitSegmentId, Coord exitCoords) {
        this.uid = uid;
        this.portalName = portalName;
        this.entranceMarkerId = entranceMarkerId;
        this.entranceSegmentId = entranceSegmentId;
        this.entranceCoords = entranceCoords;
        this.exitMarkerId = exitMarkerId;
        this.exitSegmentId = exitSegmentId;
        this.exitCoords = exitCoords;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Checks if both entrance and exit markers were created successfully.
     * 
     * @return true if both marker IDs are valid (not -1)
     */
    public boolean isComplete() {
        return entranceMarkerId != -1 && exitMarkerId != -1;
    }
    
    /**
     * Gets the entrance marker name.
     * 
     * @return entrance marker name (e.g., "Cave ABC123 IN")
     */
    public String getEntranceMarkerName() {
        String prefix = PortalName.getNamePrefix(portalName);
        return prefix + " " + uid + " IN";
    }
    
    /**
     * Gets the exit marker name.
     * 
     * @return exit marker name (e.g., "Cave ABC123 OUT")
     */
    public String getExitMarkerName() {
        String prefix = PortalName.getNamePrefix(portalName);
        return prefix + " " + uid + " OUT";
    }
    
    @Override
    public String toString() {
        return String.format("PortalMarkerLink{uid=%s, portal=%s, entrance=(%d,%d), exit=(%d,%d)}",
            uid, portalName, entranceSegmentId, entranceMarkerId, exitSegmentId, exitMarkerId);
    }
}
