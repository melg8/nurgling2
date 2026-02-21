package nurgling.teleportation;

/**
 * Type-safe representation of teleportation methods in the game.
 * Each enum constant represents a distinct mechanism of fast travel or map transition.
 */
public enum TeleportationType {
    /**
     * Portal connecting different map layers (caves, mines, stairs).
     * Used for transitions between surface, underground, and interior locations.
     */
    PORTAL_LAYER_TRANSITION,

    /**
     * Portal within the same map layer (doors, stairs inside buildings).
     * Used for transitions that do not change the overall map layer.
     */
    PORTAL_SAME_LAYER,

    /**
     * Teleportation to hearth fire using hearthfire bone item.
     * Allows instant return to a bonded hearth fire location.
     */
    HEARTHFIRE,

    /**
     * Teleportation to village totem.
     * Used for fast travel to established village locations.
     */
    VILLAGE_TOTEM,

    /**
     * Fast travel via signpost network.
     * Used for quick transportation between connected signposts.
     */
    SIGNPOST,

    /**
     * Unrecognized or unknown teleportation type.
     * Used as fallback when teleportation method cannot be determined.
     */
    UNKNOWN;
}
