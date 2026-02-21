package nurgling.teleportation;

import haven.Coord;

/**
 * Represents a teleportation event that occurred in the game.
 * Contains all relevant information about the teleportation including
 * source and destination coordinates, grid IDs, timestamp, and portal information.
 * <p>
 * This class is immutable - all fields are final and set only through the constructor.
 */
public class TeleportationEvent {
    /**
     * The type of teleportation that occurred.
     * Examples include portal transitions, hearthfire teleportation, village totem, etc.
     */
    private final TeleportationType type;

    /**
     * The Grid ID before teleportation occurred.
     * This identifies the map grid the player was on before teleporting.
     */
    private final long sourceGridId;

    /**
     * The coordinates before teleportation occurred.
     * This represents the player's position on the source grid.
     */
    private final Coord sourceCoord;

    /**
     * The Grid ID after teleportation occurred.
     * This identifies the map grid the player arrived at after teleporting.
     */
    private final long targetGridId;

    /**
     * The coordinates after teleportation occurred.
     * This represents the player's position on the target grid.
     */
    private final Coord targetCoord;

    /**
     * Unix timestamp (in milliseconds) when the teleportation occurred.
     * This can be used for ordering events and calculating time differences.
     */
    private final long timestamp;

    /**
     * The name of the gob (game object) if this teleportation was via a portal.
     * This field is nullable - it will be null for non-portal teleportations
     * such as hearthfire or village totem teleportation.
     */
    private final String portalGobName;

    /**
     * Constructs a new TeleportationEvent with all required fields.
     *
     * @param type the type of teleportation that occurred
     * @param sourceGridId the Grid ID before teleportation
     * @param sourceCoord the coordinates before teleportation
     * @param targetGridId the Grid ID after teleportation
     * @param targetCoord the coordinates after teleportation
     * @param timestamp the Unix timestamp when the teleportation occurred
     * @param portalGobName the name of the portal gob if applicable, or null
     */
    public TeleportationEvent(TeleportationType type, long sourceGridId, Coord sourceCoord,
                              long targetGridId, Coord targetCoord, long timestamp, String portalGobName) {
        this.type = type;
        this.sourceGridId = sourceGridId;
        this.sourceCoord = sourceCoord;
        this.targetGridId = targetGridId;
        this.targetCoord = targetCoord;
        this.timestamp = timestamp;
        this.portalGobName = portalGobName;
    }

    /**
     * Returns the type of teleportation.
     *
     * @return the teleportation type
     */
    public TeleportationType getType() {
        return type;
    }

    /**
     * Returns the Grid ID before teleportation.
     *
     * @return the source Grid ID
     */
    public long getSourceGridId() {
        return sourceGridId;
    }

    /**
     * Returns the coordinates before teleportation.
     *
     * @return the source coordinates
     */
    public Coord getSourceCoord() {
        return sourceCoord;
    }

    /**
     * Returns the Grid ID after teleportation.
     *
     * @return the target Grid ID
     */
    public long getTargetGridId() {
        return targetGridId;
    }

    /**
     * Returns the coordinates after teleportation.
     *
     * @return the target coordinates
     */
    public Coord getTargetCoord() {
        return targetCoord;
    }

    /**
     * Returns the Unix timestamp when the teleportation occurred.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the name of the portal gob if this was a portal teleportation.
     *
     * @return the portal gob name, or null if not applicable
     */
    public String getPortalGobName() {
        return portalGobName;
    }

    /**
     * Returns a string representation of this teleportation event for debugging purposes.
     * Includes all field values in a readable format.
     *
     * @return a string representation of this event
     */
    @Override
    public String toString() {
        return "TeleportationEvent{" +
                "type=" + type +
                ", sourceGridId=" + sourceGridId +
                ", sourceCoord=" + sourceCoord +
                ", targetGridId=" + targetGridId +
                ", targetCoord=" + targetCoord +
                ", timestamp=" + timestamp +
                ", portalGobName='" + portalGobName + '\'' +
                '}';
    }
}
