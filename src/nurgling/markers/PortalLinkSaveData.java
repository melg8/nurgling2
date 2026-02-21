package nurgling.markers;

import org.json.JSONObject;

/**
 * Data Transfer Object for JSON serialization/deserialization of PortalLink.
 * <p>
 * This class provides a flat structure suitable for JSON serialization,
 * containing all necessary fields to reconstruct a PortalLink object.
 * It is used for persisting portal links to storage and restoring them.
 * </p>
 * <p>
 * JSON schema:
 * <pre>{@code
 * {
 *   "linkUid": "abc123",
 *   "sourceLayer": 0,
 *   "sourceGridId": 12345,
 *   "sourceCoordX": 50,
 *   "sourceCoordY": 60,
 *   "sourceDirection": "OUT",
 *   "targetLayer": 1,
 *   "targetGridId": 67890,
 *   "targetCoordX": 45,
 *   "targetCoordY": 55,
 *   "targetDirection": "IN",
 *   "createdTimestamp": 1708500000000,
 *   "lastAccessedTimestamp": 1708500100000
 * }
 * }</pre>
 * </p>
 *
 * @author nurgling
 * @version 1.0
 * @see PortalLink
 */
public class PortalLinkSaveData {

    /**
     * Unique 6-character identifier for the link.
     */
    public String linkUid;

    /**
     * Source layer number (departure layer).
     */
    public int sourceLayer;

    /**
     * Grid/chunk ID where the source marker is located.
     */
    public long sourceGridId;

    /**
     * X coordinate of the source marker in local chunk space.
     */
    public int sourceCoordX;

    /**
     * Y coordinate of the source marker in local chunk space.
     */
    public int sourceCoordY;

    /**
     * Direction of the source marker (typically OUT).
     */
    public String sourceDirection;

    /**
     * Target layer number (arrival layer).
     */
    public int targetLayer;

    /**
     * Grid/chunk ID where the target marker is located.
     */
    public long targetGridId;

    /**
     * X coordinate of the target marker in local chunk space.
     */
    public int targetCoordX;

    /**
     * Y coordinate of the target marker in local chunk space.
     */
    public int targetCoordY;

    /**
     * Direction of the target marker (typically IN).
     */
    public String targetDirection;

    /**
     * Unix timestamp (milliseconds) when the link was created.
     */
    public long createdTimestamp;

    /**
     * Unix timestamp (milliseconds) when the link was last accessed.
     */
    public long lastAccessedTimestamp;

    /**
     * Default constructor for JSON deserialization.
     * Creates an empty PortalLinkSaveData instance.
     */
    public PortalLinkSaveData() {
    }

    /**
     * Constructs a PortalLinkSaveData from a PortalLink.
     * Extracts all necessary fields from the link and its markers.
     *
     * @param link the PortalLink to convert to save data
     */
    public PortalLinkSaveData(PortalLink link) {
        this.linkUid = link.getLinkUid();
        this.sourceLayer = link.getSourceLayer();
        this.sourceGridId = link.getSourceMarker().getGridId();
        this.sourceCoordX = link.getSourceMarker().getCoord().x;
        this.sourceCoordY = link.getSourceMarker().getCoord().y;
        this.sourceDirection = link.getSourceMarker().getDirection().name();
        this.targetLayer = link.getTargetLayer();
        this.targetGridId = link.getTargetMarker().getGridId();
        this.targetCoordX = link.getTargetMarker().getCoord().x;
        this.targetCoordY = link.getTargetMarker().getCoord().y;
        this.targetDirection = link.getTargetMarker().getDirection().name();
        this.createdTimestamp = link.getCreatedTimestamp();
        this.lastAccessedTimestamp = link.getLastAccessedTimestamp();
    }

    /**
     * Converts this save data to a JSONObject.
     * All public fields are serialized to JSON with their exact names.
     *
     * @return a JSONObject containing all save data fields
     */
    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        json.put("linkUid", linkUid);
        json.put("sourceLayer", sourceLayer);
        json.put("sourceGridId", sourceGridId);
        json.put("sourceCoordX", sourceCoordX);
        json.put("sourceCoordY", sourceCoordY);
        json.put("sourceDirection", sourceDirection);
        json.put("targetLayer", targetLayer);
        json.put("targetGridId", targetGridId);
        json.put("targetCoordX", targetCoordX);
        json.put("targetCoordY", targetCoordY);
        json.put("targetDirection", targetDirection);
        json.put("createdTimestamp", createdTimestamp);
        json.put("lastAccessedTimestamp", lastAccessedTimestamp);
        return json;
    }

    /**
     * Creates a PortalLinkSaveData from a JSONObject.
     * Reads all required fields from the JSON object.
     *
     * @param json the JSONObject to read from
     * @return a new PortalLinkSaveData instance populated from JSON
     */
    public static PortalLinkSaveData fromJsonObject(JSONObject json) {
        PortalLinkSaveData data = new PortalLinkSaveData();
        data.linkUid = json.getString("linkUid");
        data.sourceLayer = json.getInt("sourceLayer");
        data.sourceGridId = json.getLong("sourceGridId");
        data.sourceCoordX = json.getInt("sourceCoordX");
        data.sourceCoordY = json.getInt("sourceCoordY");
        data.sourceDirection = json.getString("sourceDirection");
        data.targetLayer = json.getInt("targetLayer");
        data.targetGridId = json.getLong("targetGridId");
        data.targetCoordX = json.getInt("targetCoordX");
        data.targetCoordY = json.getInt("targetCoordY");
        data.targetDirection = json.getString("targetDirection");
        data.createdTimestamp = json.getLong("createdTimestamp");
        data.lastAccessedTimestamp = json.getLong("lastAccessedTimestamp");
        return data;
    }

    /**
     * Creates a PortalLinkSaveData from a PortalLink.
     * Static factory method that delegates to the constructor.
     *
     * @param link the PortalLink to convert
     * @return a new PortalLinkSaveData instance containing the link's data
     */
    public static PortalLinkSaveData fromPortalLink(PortalLink link) {
        return new PortalLinkSaveData(link);
    }

    /**
     * Converts this save data back to a PortalLink.
     * Creates new PortalMarker instances and then creates a PortalLink.
     * <p>
     * Note: This method creates markers with default values for fields not
     * stored in save data (name, type, icon). The markers are functional
     * for link purposes but may need additional initialization for full use.
     * </p>
     *
     * @return a new PortalLink instance reconstructed from this save data
     */
    public PortalLink toPortalLink() {
        // Create source marker with default values for non-serialized fields
        PortalMarker sourceMarker = new PortalMarker(
            "Portal",                              // name (default)
            PortalType.CAVE,                       // type (default)
            sourceGridId,
            new haven.Coord(sourceCoordX, sourceCoordY),
            linkUid,
            Direction.fromString(sourceDirection),
            sourceLayer,
            createdTimestamp,
            null                                   // icon (not serialized)
        );

        // Create target marker with default values for non-serialized fields
        PortalMarker targetMarker = new PortalMarker(
            "Portal",                              // name (default)
            PortalType.CAVE,                       // type (default)
            targetGridId,
            new haven.Coord(targetCoordX, targetCoordY),
            linkUid,
            Direction.fromString(targetDirection),
            targetLayer,
            createdTimestamp,
            null                                   // icon (not serialized)
        );

        return new PortalLink(
            linkUid,
            sourceMarker,
            targetMarker,
            sourceLayer,
            targetLayer,
            createdTimestamp
        );
    }

    /**
     * Returns a string representation of this PortalLinkSaveData.
     * Includes all fields for debugging purposes.
     *
     * @return a formatted string containing all save data fields
     */
    @Override
    public String toString() {
        return String.format(
            "PortalLinkSaveData{linkUid='%s', sourceLayer=%d, sourceGridId=%d, sourceCoord=(%d,%d), sourceDirection=%s, targetLayer=%d, targetGridId=%d, targetCoord=(%d,%d), targetDirection=%s, createdTimestamp=%d, lastAccessedTimestamp=%d}",
            linkUid,
            sourceLayer,
            sourceGridId,
            sourceCoordX,
            sourceCoordY,
            sourceDirection,
            targetLayer,
            targetGridId,
            targetCoordX,
            targetCoordY,
            targetDirection,
            createdTimestamp,
            lastAccessedTimestamp
        );
    }
}
