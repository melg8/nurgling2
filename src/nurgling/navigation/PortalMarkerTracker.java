package nurgling.navigation;

import haven.*;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.NCore;

/**
 * Independent portal marker linking service.
 * Tracks player grid changes and creates linked markers on layer transitions.
 * 
 * This is completely independent of ChunkNav system.
 * Works even when ChunkNav overlay is disabled.
 * 
 * Detection approach:
 * 1. Monitor player's grid ID via MCache
 * 2. Monitor player's segment ID via MapFile
 * 3. When grid ID changes, check if player was near a portal
 * 4. If so, create linked markers via PortalMarkerLinker
 * 5. Exclude teleports (hearthfire, totem, signpost)
 * 6. Exclude cellar transitions
 */
public class PortalMarkerTracker {
    
    /**
     * Marker linker for creating linked markers.
     */
    private final PortalMarkerLinker markerLinker;
    
    /**
     * Logger for debugging.
     */
    private final PortalMarkerLogger logger;
    
    /**
     * Debug logger for tracing execution flow.
     */
    public static final DebugLogger debugLog = new DebugLogger("logs/portal_marker_tracker_debug.log");
    
    /**
     * Simple file-based debug logger.
     */
    public static class DebugLogger {
        private final String filename;
        
        public DebugLogger(String filename) {
            this.filename = filename;
            clear(); // Clear log on startup
        }
        
        private void clear() {
            try {
                java.io.File logDir = new java.io.File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(filename));
                fw.write("=== Portal Marker Tracker Debug Log (started " + new java.util.Date() + ") ===\n");
                fw.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        public void log(String message) {
            try {
                java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(filename), true);
                fw.write("[" + java.time.LocalTime.now().toString().substring(0,12) + "] " + message + "\n");
                fw.close();
            } catch (Exception e) {
                // Ignore logging errors
            }
        }
    }
    
    /**
     * Last known grid ID.
     */
    private long lastGridId = -1;
    
    /**
     * Last known segment ID (from MapFile).
     */
    private long lastSegmentId = -1;
    
    /**
     * Cached portal gob from lastActions.
     */
    private Gob cachedPortalGob = null;
    private Coord2d cachedPortalLocalCoord = null;
    private long cachedPortalGridId = -1;
    
    /**
     * Pending transition for retry (when MapFile not available).
     */
    private LayerTransition pendingTransition = null;
    private long pendingTransitionCreatedTime = 0;
    private static final long PENDING_TRANSITION_TIMEOUT_MS = 30000; // 30 seconds
    
    /**
     * Tracking enabled flag.
     */
    private boolean enabled = true;
    
    /**
     * Check interval in ms.
     */
    private static final long CHECK_INTERVAL_MS = 100;
    private long lastCheckTime = 0;
    
    /**
     * Duplicate grid transition prevention.
     */
    private long lastProcessedFromGridId = -1;
    private long lastProcessedToGridId = -1;
    private long lastProcessedTime = 0;
    private static final long DUPLICATE_PREVENTION_MS = 2000;
    
    /**
     * Last processed portal gob ID (prevents re-capture).
     */
    private long lastProcessedPortalGobId = -1;
    
    /**
     * Creates new tracker.
     */
    public PortalMarkerTracker() {
        this.markerLinker = new PortalMarkerLinker();
        this.logger = new PortalMarkerLogger();
    }
    
    /**
     * Call periodically from game loop.
     * Should be called from NMapView.tick() or NCore.tick().
     */
    public void tick() {
        // Check config
        Object enabledObj = NConfig.get(NConfig.Key.portalMarkerAutoCreate);
        boolean configEnabled = (enabledObj instanceof Boolean) && (Boolean) enabledObj;
        
        if (!enabled || !configEnabled) {
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;
        
        try {
            // First, try to process any pending transition (retry if MapFile was not available)
            if (pendingTransition != null) {
                debugLog.log("[tick] Processing pending transition...");
                if (now - pendingTransitionCreatedTime > PENDING_TRANSITION_TIMEOUT_MS) {
                    debugLog.log("[tick] Pending transition timeout - giving up");
                    pendingTransition = null;
                } else {
                    try {
                        PortalMarkerLink link = markerLinker.linkPortalMarkers(pendingTransition);
                        debugLog.log("[tick] Pending transition succeeded: " + link);
                        pendingTransition = null;
                    } catch (Exception e) {
                        debugLog.log("[tick] Pending transition still failing: " + e.getMessage());
                        // Keep pending for next tick
                    }
                }
            }
            
            doCheck();
        } catch (Exception e) {
            debugLog.log("[tick] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Main check logic.
     */
    private void doCheck() {
        GameUI gui = NUtils.getGameUI();
        if (gui == null || gui.map == null) {
            return;
        }
        
        Gob player = NUtils.player();
        if (player == null) {
            return;
        }
        
        // Get current grid ID from MCache
        MCache mcache = gui.map.glob.map;
        if (mcache == null) {
            return;
        }
        
        Coord2d playerRC = player.rc;
        if (playerRC == null) {
            return;
        }
        
        // Get current grid
        MCache.Grid currentGrid = mcache.getgridt(playerRC.floor(MCache.tilesz));
        if (currentGrid == null) {
            return;
        }
        
        long currentGridId = currentGrid.id;
        
        // Get current segment ID from MapFile
        long currentSegmentId = -1;
        if (gui.mapfile != null && gui.mapfile.view != null && gui.mapfile.view.sessloc != null) {
            currentSegmentId = gui.mapfile.view.sessloc.seg.id;
        }
        
        debugLog.log("[doCheck] gridId=" + currentGridId + ", segmentId=" + currentSegmentId + ", lastGridId=" + lastGridId + ", lastSegmentId=" + lastSegmentId);
        
        // Check for grid change (portal transition)
        if (lastGridId != -1 && currentGridId != lastGridId) {
            // If we already have a pending transition for this exact change, don't create another
            // This prevents duplicates when player stands on grid boundary
            if (pendingTransition != null && 
                pendingTransition.fromSegmentId == lastSegmentId && 
                pendingTransition.toSegmentId == currentSegmentId) {
                debugLog.log("[doCheck] GRID CHANGED: same as pending - skipping duplicate");
            } else {
                debugLog.log("[doCheck] GRID CHANGED: " + lastGridId + " -> " + currentGridId);
                onGridChanged(lastGridId, currentGridId, lastSegmentId, currentSegmentId, player);
            }
            // Always update lastGridId/lastSegmentId even if we skipped (prevents infinite loop)
            lastGridId = currentGridId;
            lastSegmentId = currentSegmentId;
            return; // Skip rest of doCheck() - portal already captured or pending
        }
        
        // Capture portal from lastActions BEFORE grid change
        NCore.LastActions lastActions = NUtils.getUI().core.getLastActions();
        if (lastActions != null && lastActions.gob != null && lastActions.gob.ngob != null) {
            String gobName = lastActions.gob.ngob.name.toLowerCase();
            debugLog.log("[doCheck] lastActions.gob=" + lastActions.gob.ngob.name);
            if (isPortalGob(gobName) && lastActions.gob.id != lastProcessedPortalGobId) {
                debugLog.log("[doCheck] Portal CAPTURED: " + lastActions.gob.ngob.name);
                cachedPortalGob = lastActions.gob;
                cachedPortalLocalCoord = getGobLocalCoord(lastActions.gob);
                
                // Get portal's grid ID
                if (cachedPortalLocalCoord != null) {
                    MCache.Grid portalGrid = mcache.getgridt(cachedPortalLocalCoord.floor(MCache.tilesz));
                    if (portalGrid != null) {
                        cachedPortalGridId = portalGrid.id;
                        debugLog.log("[doCheck] Portal gridId=" + cachedPortalGridId);
                    }
                }
            }
        }

        // Update state (only if no grid change - grid change updates state before return)
        lastGridId = currentGridId;
        lastSegmentId = currentSegmentId;
    }
    
    /**
     * Called when player's grid ID changes.
     */
    private void onGridChanged(long fromGridId, long toGridId, 
                               long fromSegmentId, long toSegmentId, 
                               Gob player) {
        debugLog.log("[onGridChanged] fromGrid=" + fromGridId + " -> toGrid=" + toGridId + ", fromSeg=" + fromSegmentId + " -> toSeg=" + toSegmentId);
        
        // Check for duplicate grid transition
        long now = System.currentTimeMillis();
        if (fromGridId == lastProcessedFromGridId && toGridId == lastProcessedToGridId &&
            (now - lastProcessedTime) < DUPLICATE_PREVENTION_MS) {
            debugLog.log("[onGridChanged] DUPLICATE transition - skipping");
            return;
        }
        
        // Mark this transition as processed
        lastProcessedFromGridId = fromGridId;
        lastProcessedToGridId = toGridId;
        lastProcessedTime = now;
        
        // Check if this is actually a layer transition (segments must be different)
        // DISABLED: Cave passages may have same segment ID, but still need markers
        // if (fromSegmentId == toSegmentId) {
        //     debugLog.log("[onGridChanged] Same segment - not a layer transition, skipping");
        //     return;
        // }
        
        // Check if player landed on their hearthfire - this indicates a teleport, not a portal traversal
        if (isPlayerOnHearthfire(player)) {
            debugLog.log("[onGridChanged] HEARTHFIRE teleport detected - skipping");
            logger.logSkippedTransition("hearthfire_teleport", 
                "fromGrid=" + fromGridId + ", toGrid=" + toGridId);
            return;
        }
        
        // If we don't have a cached portal, we didn't click a known portal - don't create markers
        if (cachedPortalGob == null || cachedPortalGob.ngob == null) {
            debugLog.log("[onGridChanged] NO cached portal - skipping");
            return;
        }
        
        String portalName = cachedPortalGob.ngob.name;
        debugLog.log("[onGridChanged] portalName=" + portalName);
        
        // Exclude cellar from marking
        if (portalName.toLowerCase().contains("cellar")) {
            debugLog.log("[onGridChanged] CELLAR excluded - skipping");
            logger.logSkippedTransition("cellar_excluded", "portal=" + portalName);
            return;
        }
        
        // Check if this is a portal that should be marked
        if (!PortalName.shouldMarkPortal(portalName)) {
            debugLog.log("[onGridChanged] Portal type should not be marked: " + portalName);
            logger.logSkippedTransition("unsupported_portal_type", "portal=" + portalName);
            return;
        }
        
        // Use cached portal coordinates (captured BEFORE grid change)
        Coord2d portalCoords = cachedPortalLocalCoord;
        if (portalCoords == null) {
            portalCoords = player.rc;
            debugLog.log("[onGridChanged] Using player position as fallback");
        }
        
        // Create layer transition
        LayerTransition transition = new LayerTransition(
            fromSegmentId,
            toSegmentId,
            portalCoords,
            portalName,
            player.rc
        );
        
        // Log transition
        logger.logTransition(fromSegmentId, toSegmentId, portalCoords, portalName);
        debugLog.log("[onGridChanged] Calling markerLinker.linkPortalMarkers()");
        
        // Create linked markers
        try {
            PortalMarkerLink link = markerLinker.linkPortalMarkers(transition);
            debugLog.log("[onGridChanged] Link created: " + link);
            
            // Mark the cached portal as processed
            lastProcessedPortalGobId = cachedPortalGob.id;
            
        } catch (haven.Loading e) {
            // Map data not ready - save for retry
            debugLog.log("[onGridChanged] Loading - saving for retry: " + e.getMessage());
            pendingTransition = transition;
            pendingTransitionCreatedTime = System.currentTimeMillis();
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            debugLog.log("[onGridChanged] ERROR: " + errorMsg);
            e.printStackTrace();
            logger.logMarkerError("LINK_PORTAL_MARKERS_FAILED", 
                "transition=" + transition + ", error=" + e.getMessage());
            
            // If error is "Waiting for map data", save transition for retry
            if (errorMsg != null && errorMsg.contains("Waiting for map data")) {
                debugLog.log("[onGridChanged] Saving transition for retry (MapFile not ready)");
                pendingTransition = transition;
                pendingTransitionCreatedTime = System.currentTimeMillis();
            }
        }
        
        // Clear cached portal after processing
        cachedPortalGob = null;
        cachedPortalLocalCoord = null;
        cachedPortalGridId = -1;
    }
    
    /**
     * Checks if a gob name is a portal (cave, minehole, ladder).
     */
    private boolean isPortalGob(String gobName) {
        if (gobName == null) {
            return false;
        }
        String lower = gobName.toLowerCase();
        return lower.contains("cave") || 
               lower.contains("minehole") || 
               lower.contains("ladder");
    }
    
    /**
     * Gets the local coordinate of a gob.
     */
    private Coord2d getGobLocalCoord(Gob gob) {
        if (gob == null || gob.rc == null) {
            return null;
        }
        return gob.rc;
    }
    
    /**
     * Checks if player is on their hearthfire (teleport detection).
     */
    private boolean isPlayerOnHearthfire(Gob player) {
        try {
            Glob glob = NUtils.getGameUI().ui.sess.glob;
            if (glob == null || glob.oc == null) {
                return false;
            }
            
            synchronized (glob.oc) {
                for (Gob gob : glob.oc) {
                    if (gob == null || gob.ngob == null || gob.ngob.name == null) {
                        continue;
                    }
                    
                    String name = gob.ngob.name.toLowerCase();
                    if (name.contains("hearthfire")) {
                        double distance = player.rc.dist(gob.rc);
                        if (distance < 5.0) { // Within 5 world units
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return false;
    }
}
