package nurgling.markers;

/**
 * Enum representing the direction of portal transitions.
 * Portal directions indicate the flow of movement between layers.
 */
public enum Direction {
    /**
     * IN - entrance marker.
     * The player exits from a portal on this layer (entering the current layer).
     * This marks where the player arrives on the current layer.
     */
    IN,

    /**
     * OUT - exit marker.
     * The player enters into a portal on this layer (leaving the current layer).
     * This marks where the player departs from the current layer.
     */
    OUT;

    /**
     * Converts a string representation to a Direction enum value.
     * Case-insensitive matching with fallback to IN for unknown values.
     *
     * @param s the string to convert (e.g., "IN", "OUT")
     * @return the corresponding Direction, or IN if the string is not recognized
     */
    public static Direction fromString(String s) {
        if (s == null) {
            return IN;
        }
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle legacy formats if needed
            if ("ENTER".equalsIgnoreCase(s) || "ENTRY".equalsIgnoreCase(s)) {
                return IN;
            }
            if ("EXIT".equalsIgnoreCase(s) || "LEAVE".equalsIgnoreCase(s)) {
                return OUT;
            }
            // Default to IN for unknown values
            return IN;
        }
    }

    /**
     * Returns the opposite direction.
     * IN becomes OUT, and OUT becomes IN.
     *
     * @return the opposite Direction
     */
    public Direction opposite() {
        switch (this) {
            case IN:
                return OUT;
            case OUT:
                return IN;
            default:
                return IN;
        }
    }
}
