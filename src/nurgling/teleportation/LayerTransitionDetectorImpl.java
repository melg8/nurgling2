package nurgling.teleportation;

import nurgling.navigation.ChunkPortal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementation of {@link LayerTransitionDetector} that detects and analyzes
 * layer transitions in teleportation events.
 * <p>
 * A layer transition is a special type of teleportation where the player moves
 * between different vertical levels of the game world, such as:
 * </p>
 * <ul>
 *   <li>Surface ↔ Underground (caves, mines)</li>
 *   <li>Between different mine levels (dungeon levels 2+)</li>
 *   <li>Between different cave levels (multi-level caves)</li>
 *   <li>Between any adjacent layers via MINEHOLE or LADDER</li>
 * </ul>
 * <p>
 * This implementation supports dungeon levels 2+ by detecting transitions
 * between any grid IDs that represent different layers, not just surface↔underground.
 * The layer number is derived from grid ID ranges:
 * </p>
 * <ul>
 *   <li>Grid IDs 0-9999: layer 0 (surface)</li>
 *   <li>Grid IDs 10000-19999: layer 1 (underground level 1)</li>
 *   <li>Grid IDs 20000-29999: layer 2 (underground level 2)</li>
 *   <li>etc.</li>
 * </ul>
 * <p>
 * This implementation checks for:
 * </p>
 * <ul>
 *   <li>Event type is {@link TeleportationType#PORTAL_LAYER_TRANSITION}</li>
 *   <li>Source and target grid IDs are different</li>
 *   <li>Portal type is CAVE, MINEHOLE, or LADDER</li>
 * </ul>
 * <p>
 * Thread-safety: This class is thread-safe as it maintains no mutable state.
 * </p>
 *
 * @see LayerTransitionDetector
 * @see TeleportationEvent
 * @see ChunkPortal.PortalType
 */
public class LayerTransitionDetectorImpl implements LayerTransitionDetector {

    private static final Logger logger = LoggerFactory.getLogger(LayerTransitionDetectorImpl.class);

    /**
     * Constructs a new LayerTransitionDetectorImpl.
     * <p>
     * The detector is stateless and can be safely shared across multiple threads.
     * </p>
     */
    public LayerTransitionDetectorImpl() {
        logger.debug("LayerTransitionDetectorImpl initialized");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines if the given teleportation event represents a layer transition.
     * A layer transition must satisfy ALL of the following conditions:
     * </p>
     * <ul>
     *   <li>Event type is {@link TeleportationType#PORTAL_LAYER_TRANSITION}</li>
     *   <li>Source grid ID is different from target grid ID</li>
     *   <li>Portal type is CAVE, MINEHOLE, or LADDER</li>
     * </ul>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return true if the event represents a layer transition, false otherwise
     * @throws IllegalArgumentException if event is null
     */
    @Override
    public boolean isLayerTransition(TeleportationEvent event) {
        Objects.requireNonNull(event, "TeleportationEvent must not be null");

        logger.debug("Checking layer transition for event: {}", event);

        // T052: Check 1 - Event type must be PORTAL_LAYER_TRANSITION
        // This explicitly excludes HEARTHFIRE, VILLAGE_TOTEM, SIGNPORT, and other types
        if (event.getType() != TeleportationType.PORTAL_LAYER_TRANSITION) {
            logger.info("Not a layer transition: wrong type {} (excludes HEARTHFIRE, VILLAGE_TOTEM, SIGNPOST)", event.getType());
            return false;
        }

        // Check 2: Source and target grid IDs must be different
        if (event.getSourceGridId() == event.getTargetGridId()) {
            logger.debug("Not a layer transition: same grid ID {}", event.getSourceGridId());
            return false;
        }

        // Check 3: Portal type must be CAVE, MINEHOLE, or LADDER
        ChunkPortal.PortalType portalType = getPortalType(event);
        if (portalType == null) {
            logger.debug("Not a layer transition: unknown portal type");
            return false;
        }

        boolean isLayerPortal = (portalType == ChunkPortal.PortalType.CAVE ||
                                  portalType == ChunkPortal.PortalType.MINEHOLE ||
                                  portalType == ChunkPortal.PortalType.LADDER);

        if (!isLayerPortal) {
            logger.debug("Not a layer transition: portal type {} is not a layer portal", portalType);
            return false;
        }

        logger.info("Layer transition confirmed: type={}, grids={}->{}, portal={}",
               event.getType(), event.getSourceGridId(), event.getTargetGridId(), portalType);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the direction of the layer transition based on portal type:
     * </p>
     * <ul>
     *   <li><strong>+1</strong> - Descending (CAVE, MINEHOLE - going down into underground)</li>
     *   <li><strong>-1</strong> - Ascending (LADDER - climbing up out of underground)</li>
     *   <li><strong>0</strong> - Not a layer transition or unknown portal type</li>
     * </ul>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return +1 for descending, -1 for ascending, 0 if not a layer transition
     * @throws IllegalArgumentException if event is null
     */
    @Override
    public int getTransitionDirection(TeleportationEvent event) {
        Objects.requireNonNull(event, "TeleportationEvent must not be null");

        ChunkPortal.PortalType portalType = getPortalType(event);
        if (portalType == null) {
            logger.debug("Transition direction: unknown portal type, returning 0");
            return 0;
        }

        int direction;
        switch (portalType) {
            case CAVE:
            case MINEHOLE:
                direction = +1;  // Descending into underground
                logger.debug("Transition direction: {} -> descending (+1)", portalType);
                break;
            case LADDER:
                direction = -1;  // Ascending out of underground
                logger.debug("Transition direction: {} -> ascending (-1)", portalType);
                break;
            default:
                direction = 0;
                logger.debug("Transition direction: {} -> no vertical movement (0)", portalType);
                break;
        }

        return direction;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Identifies the specific type of portal used in the layer transition
     * by analyzing the portal gob name.
     * </p>
     *
     * @param event the teleportation event to analyze; must not be null
     * @return the portal type (CAVE, MINEHOLE, LADDER), or null if the
     *         event is not a layer transition or the portal type cannot be determined
     * @throws IllegalArgumentException if event is null
     */
    @Override
    public ChunkPortal.PortalType getPortalType(TeleportationEvent event) {
        Objects.requireNonNull(event, "TeleportationEvent must not be null");

        String portalGobName = event.getPortalGobName();
        if (portalGobName == null || portalGobName.isEmpty()) {
            logger.debug("Portal type: no portal gob name, returning null");
            return null;
        }

        ChunkPortal.PortalType type = classifyPortalType(portalGobName);
        logger.debug("Portal type for '{}': {}", portalGobName, type);
        return type;
    }

    /**
     * Classifies the type of portal based on the gob (game object) name.
     * <p>
     * This method uses {@link ChunkPortal#classifyPortal(String)} to determine
     * the portal type. The classification is based on keywords in the gob name:
     * </p>
     * <ul>
     *   <li>"cave" → CAVE</li>
     *   <li>"minehole" → MINEHOLE</li>
     *   <li>"ladder" → LADDER</li>
     * </ul>
     *
     * @param gobName the name of the portal gob (e.g., "gfx/terobjs/cave_entrance")
     * @return the classified portal type, or null if not a recognized layer portal
     */
    public ChunkPortal.PortalType classifyPortalType(String gobName) {
        if (gobName == null) {
            return null;
        }

        ChunkPortal.PortalType type = ChunkPortal.classifyPortal(gobName);

        // Only return layer-related portal types
        if (type == ChunkPortal.PortalType.CAVE ||
            type == ChunkPortal.PortalType.MINEHOLE ||
            type == ChunkPortal.PortalType.LADDER) {
            return type;
        }

        // Check if gob name contains cave-related keywords that might not be
        // caught by classifyPortal
        String lowerName = gobName.toLowerCase();
        if (lowerName.contains("cave")) {
            logger.debug("Classified '{}' as CAVE (keyword match)", gobName);
            return ChunkPortal.PortalType.CAVE;
        }
        if (lowerName.contains("minehole")) {
            logger.debug("Classified '{}' as MINEHOLE (keyword match)", gobName);
            return ChunkPortal.PortalType.MINEHOLE;
        }
        if (lowerName.contains("ladder")) {
            logger.debug("Classified '{}' as LADDER (keyword match)", gobName);
            return ChunkPortal.PortalType.LADDER;
        }

        logger.debug("Portal '{}' is not a layer portal (type: {})", gobName, type);
        return null;
    }

    /**
     * Gets the layer number for a grid ID.
     * <p>
     * This method derives the layer from the grid ID using the formula:
     * {@code layer = gridId / 10000}.
     * </p>
     * <p>
     * Grid ID ranges:
     * </p>
     * <ul>
     *   <li>0-9999: layer 0 (surface)</li>
     *   <li>10000-19999: layer 1 (underground level 1)</li>
     *   <li>20000-29999: layer 2 (underground level 2)</li>
     *   <li>etc.</li>
     * </ul>
     *
     * @param gridId the grid ID
     * @return the layer number (0 = surface, 1+ = underground)
     */
    public int getLayerForGrid(long gridId) {
        return (int) (gridId / 10000);
    }
}
