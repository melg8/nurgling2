package nurgling.navigation;

/**
 * Utility class for portal name-based operations.
 * 
 * Provides methods for:
 * - Determining IN/OUT direction from segment transition and portal type
 * - Getting display name prefix for markers (Cave, Hole)
 * - Selecting appropriate marker icon based on portal type
 * 
 * Portal Types:
 * - Cave: Surface ↔ Underground level 1 (gfx/terobjs/cave)
 * - Minehole: Underground level N → N+1, always goes down (gfx/terobjs/minehole)
 * - Ladder: Underground level N+1 → N, always goes up (gfx/terobjs/ladder)
 * 
 * Direction Rules:
 * - Surface (segmentId=1) → Underground (segmentId≠1): IN
 * - Underground (segmentId≠1) → Surface (segmentId=1): OUT
 * - Minehole: Always IN (goes down)
 * - Ladder: Always OUT (goes up)
 * - Underground → Underground: Infer from segment comparison
 * 
 * Cellar is explicitly excluded from marking (small rectangular maps).
 */
public class PortalName {
    
    /**
     * Portal resource name patterns for icon selection and naming.
     * These match the resource names used in the game client.
     */
    public static final String CAVE = "gfx/terobjs/cave";
    public static final String MINEHOLE = "gfx/terobjs/minehole";
    public static final String LADDER = "gfx/terobjs/ladder";
    
    /**
     * Surface segment ID constant.
     * All surface chunks share this segment ID.
     */
    private static final long SURFACE_INSTANCE = 1;
    
    /**
     * Gets the display name prefix for a marker based on portal type.
     * 
     * Examples:
     * - Cave → "Cave"
     * - Minehole → "Hole"
     * - Ladder → "Hole"
     * 
     * @param portalName portal resource name (e.g., "gfx/terobjs/cave")
     * @return display name prefix for marker
     */
    public static String getNamePrefix(String portalName) {
        if (portalName == null) {
            return "Portal";
        }
        
        String lower = portalName.toLowerCase();
        
        if (lower.contains("cave")) {
            return "Cave";
        }
        if (lower.contains("minehole")) {
            return "Hole";
        }
        if (lower.contains("ladder")) {
            return "Hole";
        }
        
        return "Portal";
    }
    
    /**
     * Gets the IN/OUT direction indicator based on segment transition and portal type.
     * 
     * Direction Rules:
     * 1. Cave transitions (surface ↔ underground):
     *    - Surface (seg=1) → Underground (seg≠1): IN (entering cave)
     *    - Underground (seg≠1) → Surface (seg=1): OUT (exiting cave)
     * 
     * 2. Minehole/Ladder (underground → underground):
     *    - Minehole: Always IN (goes down to deeper level)
     *    - Ladder: Always OUT (goes up to shallower level)
     * 
     * 3. Underground → Underground (generic):
     *    - Higher segment ID = deeper level
     *    - fromSeg < toSeg: IN (going deeper)
     *    - fromSeg > toSeg: OUT (going up)
     * 
     * @param fromSegmentId source segment ID (surface = 1)
     * @param toSegmentId destination segment ID
     * @param portalName portal resource name (can be null for fallback logic)
     * @return "IN" or "OUT" direction indicator
     */
    public static String getDirection(long fromSegmentId, long toSegmentId, String portalName) {
        // Normalize portal name for comparison
        String lower = (portalName != null) ? portalName.toLowerCase() : "";
        
        // 1. Cave transitions (surface ↔ underground)
        // Cave has special handling because it connects surface to underground level 1
        if (lower.contains("cave")) {
            boolean fromSurface = (fromSegmentId == SURFACE_INSTANCE);
            boolean toSurface = (toSegmentId == SURFACE_INSTANCE);
            
            if (!fromSurface && toSurface) {
                return "OUT";  // Exiting cave to surface
            }
            if (fromSurface && !toSurface) {
                return "IN";   // Entering cave from surface
            }
        }
        
        // 2. Minehole/Ladder - direction determined by portal type
        // Minehole always goes down (IN), Ladder always goes up (OUT)
        if (lower.contains("minehole")) {
            return "IN";   // Always down
        }
        if (lower.contains("ladder")) {
            return "OUT";  // Always up
        }
        
        // 3. Underground-to-underground transitions (generic)
        // Higher segment ID typically means deeper level
        if (fromSegmentId < toSegmentId) {
            return "IN";   // Going deeper
        }
        if (fromSegmentId > toSegmentId) {
            return "OUT";  // Going up
        }
        
        // Default case (should not happen in normal gameplay)
        return "IN";
    }
    
    /**
     * Gets the icon resource name for a marker based on portal type and direction.
     * 
     * Icon mapping:
     * - Cave → "gfx/hud/mmap/cave" (same for IN and OUT)
     * - Minehole/Ladder → "gfx/hud/mmap/mine" (same for IN and OUT)
     * 
     * @param portalName portal resource name
     * @param direction IN or OUT
     * @return icon resource name for marker
     */
    public static String getIconName(String portalName, String direction) {
        if (portalName == null) {
            return "gfx/hud/mmap/cave";
        }
        
        String lower = portalName.toLowerCase();
        
        if (lower.contains("cave")) {
            return "gfx/hud/mmap/cave";
        }
        if (lower.contains("minehole") || lower.contains("ladder")) {
            return "gfx/hud/mmap/mine";
        }
        
        return "gfx/hud/mmap/cave";
    }
    
    /**
     * Checks if a portal should be marked (cave, minehole, ladder).
     * Cellar is explicitly excluded.
     * 
     * @param portalName portal resource name
     * @return true if portal should be marked
     */
    public static boolean shouldMarkPortal(String portalName) {
        if (portalName == null) {
            return false;
        }
        
        String lower = portalName.toLowerCase();
        
        // Explicitly exclude cellar
        if (lower.contains("cellar")) {
            return false;
        }
        
        // Include cave, minehole, ladder
        return lower.contains("cave") || 
               lower.contains("minehole") || 
               lower.contains("ladder");
    }
    
    /**
     * Checks if a portal is a cave (surface ↔ underground).
     * 
     * @param portalName portal resource name
     * @return true if portal is a cave
     */
    public static boolean isCave(String portalName) {
        return portalName != null && portalName.toLowerCase().contains("cave");
    }
    
    /**
     * Checks if a portal is a minehole (goes down).
     * 
     * @param portalName portal resource name
     * @return true if portal is a minehole
     */
    public static boolean isMinehole(String portalName) {
        return portalName != null && portalName.toLowerCase().contains("minehole");
    }
    
    /**
     * Checks if a portal is a ladder (goes up).
     * 
     * @param portalName portal resource name
     * @return true if portal is a ladder
     */
    public static boolean isLadder(String portalName) {
        return portalName != null && portalName.toLowerCase().contains("ladder");
    }
}
