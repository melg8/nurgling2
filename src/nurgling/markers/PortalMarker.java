package nurgling.markers;

import haven.Coord;
import haven.Resource;

/**
 * Represents a portal marker in the game world.
 * Portal markers indicate special transition points such as caves, mineholes, and ladders
 * that connect different map layers (surface and underground levels).
 * <p>
 * Each marker contains information about its location, type, and linking to related markers
 * on other layers. Markers can be linked in pairs using a unique identifier (linkUid) to
 * represent bidirectional portal connections.
 * </p>
 *
 * @author nurgling
 * @version 1.0
 */
public class PortalMarker {

    /**
     * Display name of the portal marker (e.g., "Cave", "Hole", "Ladder").
     * Used for UI display and identification purposes.
     */
    private final String name;

    /**
     * Type of portal (CAVE, MINEHOLE, LADDER).
     * Determines the visual representation and behavior of the marker.
     */
    private final PortalType type;

    /**
     * Grid/chunk ID where the marker is located.
     * Used for spatial indexing and quick lookup of markers in specific areas.
     */
    private final long gridId;

    /**
     * Marker coordinate in local chunk space.
     * Represents the precise position within the grid cell.
     */
    private final Coord coord;

    /**
     * 6-character unique identifier linking related markers.
     * Null for unlinked markers (e.g., natural caves without paired exits).
     * Used to establish connections between entrance and exit markers on different layers.
     */
    private String linkUid;

    /**
     * Direction of portal transition (IN or OUT).
     * Null for unlinked markers that don't have a defined transition direction.
     * IN = entrance (arriving on current layer)
     * OUT = exit (departing from current layer)
     */
    private Direction direction;

    /**
     * Map layer number.
     * 0 = surface level
     * 1+ = underground levels (deeper levels have higher numbers)
     */
    private final int layer;

    /**
     * Unix timestamp (milliseconds) when the marker was created.
     * Used for tracking marker age and sorting by creation time.
     */
    private final long createdTimestamp;

    /**
     * Visual icon for the marker.
     * Determines how the marker is rendered on the map overlay.
     */
    private final Resource.Image icon;

    /**
     * Creates a new PortalMarker with all specified properties.
     *
     * @param name the display name of the marker (e.g., "Cave", "Hole")
     * @param type the type of portal (CAVE, MINEHOLE, LADDER)
     * @param gridId the grid/chunk ID where the marker is located
     * @param coord the marker coordinate in local chunk space
     * @param linkUid the 6-character unique identifier linking related markers, or null if unlinked
     * @param direction the direction of portal transition (IN or OUT), or null for unlinked markers
     * @param layer the map layer number (0 = surface, 1+ = underground)
     * @param createdTimestamp the Unix timestamp when the marker was created
     * @param icon the visual icon for the marker
     */
    public PortalMarker(String name, PortalType type, long gridId, Coord coord,
                        String linkUid, Direction direction, int layer,
                        long createdTimestamp, Resource.Image icon) {
        this.name = name;
        this.type = type;
        this.gridId = gridId;
        this.coord = coord;
        this.linkUid = linkUid;
        this.direction = direction;
        this.layer = layer;
        this.createdTimestamp = createdTimestamp;
        this.icon = icon;
    }

    /**
     * Gets the display name of the portal marker.
     *
     * @return the display name (e.g., "Cave", "Hole", "Ladder")
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the type of portal.
     *
     * @return the PortalType (CAVE, MINEHOLE, or LADDER)
     */
    public PortalType getType() {
        return type;
    }

    /**
     * Gets the grid/chunk ID where the marker is located.
     *
     * @return the grid ID as a long value
     */
    public long getGridId() {
        return gridId;
    }

    /**
     * Gets the marker coordinate in local chunk space.
     *
     * @return the Coord representing the marker's position
     */
    public Coord getCoord() {
        return coord;
    }

    /**
     * Gets the unique identifier linking related markers.
     *
     * @return the 6-character linkUid, or null if the marker is unlinked
     */
    public String getLinkUid() {
        return linkUid;
    }

    /**
     * Sets the unique identifier for linking this marker to a related marker.
     * Used to establish connections between entrance and exit markers on different layers.
     *
     * @param linkUid the 6-character unique identifier, or null to unlink
     */
    public void setLinkUid(String linkUid) {
        this.linkUid = linkUid;
    }

    /**
     * Gets the direction of portal transition.
     *
     * @return the Direction (IN or OUT), or null if the marker is unlinked
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Sets the direction of portal transition.
     * Used when linking markers to specify the flow of movement.
     *
     * @param direction the Direction (IN or OUT), or null to clear direction
     */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /**
     * Gets the map layer number.
     *
     * @return the layer number (0 = surface, 1+ = underground)
     */
    public int getLayer() {
        return layer;
    }

    /**
     * Gets the Unix timestamp when the marker was created.
     *
     * @return the creation timestamp in milliseconds since epoch
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Gets the visual icon for the marker.
     *
     * @return the Resource.Image used for rendering the marker
     */
    public Resource.Image getIcon() {
        return icon;
    }

    /**
     * Checks if this marker is linked to another marker.
     * A marker is considered linked if it has a non-null linkUid.
     *
     * @return true if the marker has a linkUid (is linked to another marker), false otherwise
     */
    public boolean isLinked() {
        return linkUid != null;
    }

    /**
     * Returns a string representation of this PortalMarker for debugging purposes.
     * Includes all key fields: name, type, gridId, coord, link status, direction, and layer.
     *
     * @return a formatted string containing the marker's properties
     */
    @Override
    public String toString() {
        return String.format("PortalMarker{name='%s', type=%s, gridId=%d, coord=%s, linkUid=%s, direction=%s, layer=%d, createdTimestamp=%d, icon=%s}",
                name, type, gridId, coord,
                linkUid != null ? "'" + linkUid + "'" : "null",
                direction != null ? direction : "null",
                layer, createdTimestamp,
                icon != null ? icon.toString() : "null");
    }
}
