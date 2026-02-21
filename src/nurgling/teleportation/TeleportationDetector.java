package nurgling.teleportation;

/**
 * Interface for detecting and handling teleportation events in the game.
 * <p>
 * Implementations of this interface are responsible for monitoring game state
 * changes and detecting when teleportation occurs. When a teleportation is detected,
 * registered callbacks are invoked with the teleportation event details.
 * </p>
 * <p>
 * This interface provides methods for:
 * <ul>
 *   <li>Registering callbacks to be notified of teleportation events</li>
 *   <li>Retrieving the most recent teleportation event</li>
 *   <li>Clearing stored teleportation event data</li>
 * </ul>
 * </p>
 *
 * @see TeleportationCallback
 * @see TeleportationEvent
 */
public interface TeleportationDetector {
    /**
     * Registers a callback to be invoked when a teleportation event is detected.
     * <p>
     * The callback will be called each time a new teleportation event occurs.
     * Multiple callbacks can be registered and all will be invoked in the order
     * they were added.
     * </p>
     *
     * @param callback the callback to invoke on teleportation events; must not be null
     * @throws IllegalArgumentException if callback is null
     * @see TeleportationCallback
     * @see TeleportationEvent
     */
    void onTeleportation(TeleportationCallback callback);

    /**
     * Returns the most recently detected teleportation event.
     * <p>
     * This method provides access to the last teleportation event that was detected
     * by this detector. If no teleportation has been detected yet, returns null.
     * </p>
     *
     * @return the last teleportation event, or null if no teleportation has been detected
     * @see TeleportationEvent
     */
    TeleportationEvent getLastEvent();

    /**
     * Clears the stored teleportation event data.
     * <p>
     * After calling this method, {@link #getLastEvent()} will return null
     * until a new teleportation event is detected. This is useful for resetting
     * the detector state between game sessions or when reinitializing.
     * </p>
     */
    void clearEvent();
}
