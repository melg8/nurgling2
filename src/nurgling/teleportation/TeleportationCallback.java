package nurgling.teleportation;

/**
 * Functional interface for receiving callbacks when a teleportation event occurs.
 * Implementations define the action to be taken when teleportation is detected.
 */
@FunctionalInterface
public interface TeleportationCallback {
    /**
     * Called when a teleportation event is detected.
     *
     * @param event the teleportation event containing all relevant information
     *              about the teleportation including source/target coordinates,
     *              grid IDs, teleportation type, and timestamp
     */
    void onTeleport(TeleportationEvent event);
}
