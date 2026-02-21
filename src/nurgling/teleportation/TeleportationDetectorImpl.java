package nurgling.teleportation;

import haven.Coord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link TeleportationDetector} that detects discontinuous
 * position changes (>10 tiles) and classifies teleportation types.
 * <p>
 * This class monitors player position updates and detects when the player
 * has been teleported by comparing the distance between consecutive positions.
 * When a teleportation is detected (distance > 10 tiles), registered callbacks
 * are invoked with the teleportation event details.
 * </p>
 * <p>
 * Thread-safety: This class is not thread-safe. External synchronization is
 * required if accessed from multiple threads.
 * </p>
 *
 * @see TeleportationDetector
 * @see TeleportationEvent
 * @see TeleportationCallback
 */
public class TeleportationDetectorImpl implements TeleportationDetector {

    private static final Logger logger = LoggerFactory.getLogger(TeleportationDetectorImpl.class);

    /**
     * Threshold distance in tiles for detecting teleportation.
     * If the player moves more than this distance between updates,
     * it is considered a teleportation.
     */
    private static final double TELEPORTATION_THRESHOLD = 10.0;

    /**
     * List of registered callbacks to be invoked on teleportation events.
     */
    private final List<TeleportationCallback> callbacks;

    /**
     * The last detected teleportation event.
     * Null if no teleportation has been detected yet or after clearing.
     */
    private TeleportationEvent lastEvent;

    /**
     * The last known player coordinate.
     * Used to compare with new positions to detect teleportation.
     */
    private Coord lastKnownCoord;

    /**
     * The last known grid ID.
     * Grid ID changes indicate the player moved to a different map grid.
     */
    private long lastKnownGridId;

    /**
     * Constructs a new TeleportationDetectorImpl with empty state.
     * <p>
     * After construction, the detector is ready to receive position updates
     * via {@link #updatePlayerPosition(Coord, long)}. No callbacks are
     * registered initially.
     * </p>
     */
    public TeleportationDetectorImpl() {
        this.callbacks = new ArrayList<>();
        this.lastEvent = null;
        this.lastKnownCoord = null;
        this.lastKnownGridId = -1L;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a callback to be invoked when a teleportation event is detected.
     * The callback will be called for each new teleportation event in the order
     * they were registered.
     * </p>
     *
     * @param callback the callback to invoke on teleportation events
     * @throws IllegalArgumentException if callback is null
     */
    @Override
    public void onTeleportation(TeleportationCallback callback) {
        Objects.requireNonNull(callback, "Callback must not be null");
        callbacks.add(callback);
        logger.debug("Registered teleportation callback. Total callbacks: {}", callbacks.size());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the most recently detected teleportation event.
     * This method does not clear the event - use {@link #clearEvent()} for that.
     * </p>
     *
     * @return the last teleportation event, or null if none detected
     */
    @Override
    public TeleportationEvent getLastEvent() {
        return lastEvent;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears the stored teleportation event data. After calling this method,
     * {@link #getLastEvent()} will return null until a new teleportation
     * is detected. The last known position and grid ID are preserved.
     * </p>
     */
    @Override
    public void clearEvent() {
        logger.debug("Clearing last teleportation event: {}", lastEvent);
        lastEvent = null;
    }

    /**
     * Updates the player's position and checks for teleportation.
     * <p>
     * This method should be called whenever the player's position is updated.
     * It compares the new position with the last known position to detect
     * discontinuous movement (teleportation).
     * </p>
     * <p>
     * Teleportation is detected when:
     * <ul>
     *   <li>This is not the first position update, AND</li>
     *   <li>The distance between old and new position exceeds 10 tiles, OR</li>
     *   <li>The grid ID has changed</li>
     * </ul>
     * </p>
     *
     * @param coord the new player coordinate
     * @param gridId the new grid ID
     * @throws IllegalArgumentException if coord is null
     */
    public void updatePlayerPosition(Coord coord, long gridId) {
        Objects.requireNonNull(coord, "Coordinate must not be null");

        // Check if this is the first position update
        if (lastKnownCoord == null) {
            logger.debug("Initial position set: coord={}, gridId={}", coord, gridId);
            lastKnownCoord = coord;
            lastKnownGridId = gridId;
            return;
        }

        // Check for teleportation
        boolean isTeleportation = false;
        String context = null;

        // Grid ID change always indicates teleportation
        if (gridId != lastKnownGridId) {
            isTeleportation = true;
            context = "grid_change";
            logger.info("Grid ID changed: old={}, new={}", lastKnownGridId, gridId);
        } else {
            // Same grid - check distance
            double distance = lastKnownCoord.dist(coord);
            if (distance > TELEPORTATION_THRESHOLD) {
                isTeleportation = true;
                context = "distance_" + String.format("%.1f", distance);
                logger.info("Large distance detected: {} tiles (threshold: {})", distance, TELEPORTATION_THRESHOLD);
            }
        }

        if (isTeleportation) {
            // Classify the teleportation type
            TeleportationType type = classifyTeleportation(context);

            // Create the teleportation event
            long timestamp = System.currentTimeMillis();
            TeleportationEvent event = new TeleportationEvent(
                    type,
                    lastKnownGridId,
                    lastKnownCoord,
                    gridId,
                    coord,
                    timestamp,
                    null // portalGobName is null as we don't have portal info here
            );

            logger.info("Teleportation detected: {}", event);

            // Store the event
            lastEvent = event;

            // Invoke all registered callbacks
            for (TeleportationCallback callback : callbacks) {
                try {
                    callback.onTeleport(event);
                } catch (Exception e) {
                    logger.warn("Error invoking callback: {}", e.getMessage());
                }
            }
        } else {
            logger.debug("Position update (no teleportation): coord={}, gridId={}", coord, gridId);
        }

        // Update last known position
        lastKnownCoord = coord;
        lastKnownGridId = gridId;
    }

    /**
     * Classifies the type of teleportation based on context information.
     * <p>
     * This method analyzes the context string to determine the most likely
     * teleportation type. The context can contain information about:
     * </p>
     * <ul>
     *   <li>Grid changes (different map grids)</li>
     *   <li>Distance traveled</li>
     *   <li>UI interaction type (hearthfire, village totem, signpost)</li>
     *   <li>Portal or other teleportation mechanism identifiers</li>
     * </ul>
     * <p>
     * For User Story 4 (false positive prevention), this method checks for
     * specific UI interaction contexts:
     * </p>
     * <ul>
     *   <li>"hearthfire_ui" - hearthfire bone teleportation</li>
     *   <li>"village_totem_ui" - village totem teleportation</li>
     *   <li>"signpost_ui" - signpost fast travel</li>
     * </ul>
     *
     * @param context the context string providing hints about the teleportation
     * @return the classified teleportation type, or UNKNOWN if cannot be determined
     */
    public TeleportationType classifyTeleportation(String context) {
        if (context == null || context.isEmpty()) {
            logger.debug("Classifying teleportation: no context, returning UNKNOWN");
            return TeleportationType.UNKNOWN;
        }

        // T049: Check for hearthfire UI interaction
        if (context.contains("hearthfire_ui")) {
            logger.info("Classifying teleportation: hearthfire UI interaction detected -> HEARTHFIRE");
            return TeleportationType.HEARTHFIRE;
        }

        // T050: Check for village totem UI interaction
        if (context.contains("village_totem_ui")) {
            logger.info("Classifying teleportation: village totem UI interaction detected -> VILLAGE_TOTEM");
            return TeleportationType.VILLAGE_TOTEM;
        }

        // T051: Check for signpost UI interaction
        if (context.contains("signpost_ui")) {
            logger.info("Classifying teleportation: signpost UI interaction detected -> SIGNPOST");
            return TeleportationType.SIGNPOST;
        }

        // Check for grid change - typically indicates portal between layers
        if (context.contains("grid_change")) {
            logger.info("Classifying teleportation: grid_change -> PORTAL_LAYER_TRANSITION");
            return TeleportationType.PORTAL_LAYER_TRANSITION;
        }

        // Check for distance-based teleportation
        if (context.startsWith("distance_")) {
            try {
                double distance = Double.parseDouble(context.substring("distance_".length()));
                // Very long distances might indicate hearthfire or village totem
                if (distance > 1000.0) {
                    logger.info("Classifying teleportation: long distance ({}) -> HEARTHFIRE", distance);
                    return TeleportationType.HEARTHFIRE;
                } else if (distance > 500.0) {
                    logger.info("Classifying teleportation: medium distance ({}) -> VILLAGE_TOTEM", distance);
                    return TeleportationType.VILLAGE_TOTEM;
                } else if (distance > 100.0) {
                    logger.info("Classifying teleportation: short distance ({}) -> SIGNPOST", distance);
                    return TeleportationType.SIGNPOST;
                } else {
                    logger.info("Classifying teleportation: very short distance ({}) -> PORTAL_SAME_LAYER", distance);
                    return TeleportationType.PORTAL_SAME_LAYER;
                }
            } catch (NumberFormatException e) {
                logger.warn("Classifying teleportation: invalid distance format '{}', returning UNKNOWN", context);
            }
        }

        logger.debug("Classifying teleportation: unrecognized context '{}', returning UNKNOWN", context);
        return TeleportationType.UNKNOWN;
    }

    /**
     * Returns the last known coordinate.
     * <p>
     * This method is useful for debugging and testing purposes.
     * </p>
     *
     * @return the last known coordinate, or null if no position has been set
     */
    public Coord getLastKnownCoord() {
        return lastKnownCoord;
    }

    /**
     * Returns the last known grid ID.
     * <p>
     * This method is useful for debugging and testing purposes.
     * </p>
     *
     * @return the last known grid ID, or -1 if no position has been set
     */
    public long getLastKnownGridId() {
        return lastKnownGridId;
    }

    /**
     * Returns the number of registered callbacks.
     * <p>
     * This method is useful for debugging and testing purposes.
     * </p>
     *
     * @return the number of registered callbacks
     */
    public int getCallbackCount() {
        return callbacks.size();
    }
}
