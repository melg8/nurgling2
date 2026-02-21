package nurgling.markers;

import nurgling.navigation.ChunkPortal;

/**
 * Enum representing the types of portal markers.
 * Portal markers indicate special transition points in the world.
 */
public enum PortalType {
    /**
     * Cave - a natural entrance to an underground area.
     * Typically appears as a cave opening in a cliff or hillside.
     */
    CAVE,

    /**
     * Minehole - a player-dug shaft leading down into a mine.
     * This is the entrance from the surface level to the underground mine level.
     */
    MINEHOLE,

    /**
     * Ladder - a staircase or ladder leading up from a mine.
     * This is the exit from the underground mine level back to the surface.
     */
    LADDER;

    /**
     * Converts a string representation to a PortalType enum value.
     * Case-insensitive matching with fallback to CAVE for unknown values.
     *
     * @param s the string to convert (e.g., "CAVE", "MINEHOLE", "LADDER")
     * @return the corresponding PortalType, or CAVE if the string is not recognized
     */
    public static PortalType fromString(String s) {
        if (s == null) {
            return CAVE;
        }
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle legacy "MINE_ENTRANCE" - map to MINEHOLE
            if ("MINE_ENTRANCE".equalsIgnoreCase(s)) {
                return MINEHOLE;
            }
            // Default to CAVE for unknown values
            return CAVE;
        }
    }

    /**
     * Converts this PortalType to the corresponding ChunkPortal.PortalType.
     * This allows interoperability between the markers system and the navigation system.
     *
     * @return the corresponding ChunkPortal.PortalType
     */
    public ChunkPortal.PortalType toChunkPortalType() {
        switch (this) {
            case MINEHOLE:
                return ChunkPortal.PortalType.MINEHOLE;
            case LADDER:
                return ChunkPortal.PortalType.LADDER;
            case CAVE:
            default:
                // CAVE doesn't have a direct equivalent in ChunkPortal.PortalType
                // Return MINEHOLE as the closest match for underground entrances
                return ChunkPortal.PortalType.MINEHOLE;
        }
    }
}
