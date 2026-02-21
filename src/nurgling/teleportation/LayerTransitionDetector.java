package nurgling.teleportation;

import nurgling.navigation.ChunkPortal;

/**
 * Interface for detecting and analyzing layer transitions in teleportation events.
 * <p>
 * Layer transitions are special teleportation events where the player moves between
 * different vertical layers of the game world, such as:
 * </p>
 * <ul>
 *   <li>Surface ↔ Underground (caves, mines)</li>
 *   <li>Between different mine levels</li>
 *   <li>Between different cave levels</li>
 * </ul>
 * <p>
 * Implementations of this interface analyze {@link TeleportationEvent} objects to
 * determine if they represent layer transitions and provide information about
 * the direction and type of transition.
 * </p>
 *
 * @see TeleportationEvent
 * @see ChunkPortal.PortalType
 */
public interface LayerTransitionDetector {

    /**
     * Determines if the given teleportation event represents a layer transition.
     * <p>
     * A layer transition is a teleportation between different vertical levels
     * of the game world, such as entering/exiting caves, mines, or other
     * underground areas.
     * </p>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return true if the event represents a layer transition, false otherwise
     * @throws IllegalArgumentException if event is null
     * @see TeleportationEvent
     */
    boolean isLayerTransition(TeleportationEvent event);

    /**
     * Returns the direction of the layer transition.
     * <p>
     * The direction indicates whether the player is moving to a higher or lower
     * vertical level:
     * </p>
     * <ul>
     *   <li><strong>+1</strong> - Descending (surface → underground, upper level → lower level)</li>
     *   <li><strong>-1</strong> - Ascending (underground → surface, lower level → upper level)</li>
     *   <li><strong>0</strong> - No layer transition or horizontal movement only</li>
     * </ul>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return +1 for descending, -1 for ascending, 0 if not a layer transition
     * @throws IllegalArgumentException if event is null
     * @see #isLayerTransition(TeleportationEvent)
     */
    int getTransitionDirection(TeleportationEvent event);

    /**
     * Returns the type of portal used in the layer transition.
     * <p>
     * This method identifies the specific type of portal that facilitated
     * the layer transition, such as cave entrances, mineholes, or ladders.
     * </p>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return the portal type (CAVE, MINEHOLE, LADDER, etc.), or null if the
     *         event is not a layer transition or the portal type cannot be determined
     * @throws IllegalArgumentException if event is null
     * @see ChunkPortal.PortalType
     * @see #isLayerTransition(TeleportationEvent)
     */
    ChunkPortal.PortalType getPortalType(TeleportationEvent event);
}
