package nurgling.teleportation;

import nurgling.navigation.ChunkPortal;

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
 *   <li>Between different mine levels</li>
 *   <li>Between different cave levels</li>
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

    /**
     * Constructs a new LayerTransitionDetectorImpl.
     * <p>
     * The detector is stateless and can be safely shared across multiple threads.
     * </p>
     */
    public LayerTransitionDetectorImpl() {
        dprint("LayerTransitionDetectorImpl initialized");
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

        dprint("Checking layer transition for event: %s", event);

        // Check 1: Event type must be PORTAL_LAYER_TRANSITION
        if (event.getType() != TeleportationType.PORTAL_LAYER_TRANSITION) {
            dprint("Not a layer transition: wrong type %s", event.getType());
            return false;
        }

        // Check 2: Source and target grid IDs must be different
        if (event.getSourceGridId() == event.getTargetGridId()) {
            dprint("Not a layer transition: same grid ID %d", event.getSourceGridId());
            return false;
        }

        // Check 3: Portal type must be CAVE, MINEHOLE, or LADDER
        ChunkPortal.PortalType portalType = getPortalType(event);
        if (portalType == null) {
            dprint("Not a layer transition: unknown portal type");
            return false;
        }

        boolean isLayerPortal = (portalType == ChunkPortal.PortalType.CAVE ||
                                  portalType == ChunkPortal.PortalType.MINEHOLE ||
                                  portalType == ChunkPortal.PortalType.LADDER);

        if (!isLayerPortal) {
            dprint("Not a layer transition: portal type %s is not a layer portal", portalType);
            return false;
        }

        dprint("Layer transition confirmed: type=%s, grids=%d->%d, portal=%s",
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
            dprint("Transition direction: unknown portal type, returning 0");
            return 0;
        }

        int direction;
        switch (portalType) {
            case CAVE:
            case MINEHOLE:
                direction = +1;  // Descending into underground
                dprint("Transition direction: %s -> descending (+1)", portalType);
                break;
            case LADDER:
                direction = -1;  // Ascending out of underground
                dprint("Transition direction: %s -> ascending (-1)", portalType);
                break;
            default:
                direction = 0;
                dprint("Transition direction: %s -> no vertical movement (0)", portalType);
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
            dprint("Portal type: no portal gob name, returning null");
            return null;
        }

        ChunkPortal.PortalType type = classifyPortalType(portalGobName);
        dprint("Portal type for '%s': %s", portalGobName, type);
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
            dprint("Classified '%s' as CAVE (keyword match)", gobName);
            return ChunkPortal.PortalType.CAVE;
        }
        if (lowerName.contains("minehole")) {
            dprint("Classified '%s' as MINEHOLE (keyword match)", gobName);
            return ChunkPortal.PortalType.MINEHOLE;
        }
        if (lowerName.contains("ladder")) {
            dprint("Classified '%s' as LADDER (keyword match)", gobName);
            return ChunkPortal.PortalType.LADDER;
        }

        dprint("Portal '%s' is not a layer portal (type: %s)", gobName, type);
        return null;
    }

    /**
     * Debug print method for logging.
     * <p>
     * This method prints debug messages to standard output.
     * In production, this can be replaced with SLF4J logging.
     * </p>
     *
     * @param format the format string
     * @param args the arguments to be formatted
     */
    private void dprint(String format, Object... args) {
        System.out.printf("[LayerTransitionDetector] " + format + "%n", args);
    }
}
