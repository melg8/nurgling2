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
    private Coord2d cachedPortalPlayerPosition = null;
    private long cachedPortalGridId = -1;
    
    /**
     * Player position logger (logs every 500ms).
     */
    private final PlayerPositionLogger positionLogger = new PlayerPositionLogger();
    
    /**
     * Pending transition for retry (when MapFile not available).
     */
    private LayerTransition pendingTransition = null;
    private long pendingTransitionCreatedTime = 0;
    private static final long PENDING_TRANSITION_TIMEOUT_MS = 30000; // 30 seconds
    
    /**
     * Static reference to the tracker instance (for clearing from tests).
     */
    private static PortalMarkerTracker instance = null;

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
        instance = this;
    }
    
    /**
     * Clear all tracker state (for testing).
     */
    public void clear() {
        cachedPortalGob = null;
        cachedPortalLocalCoord = null;
        cachedPortalPlayerPosition = null;
        cachedPortalGridId = -1;
        pendingTransition = null;
        pendingTransitionCreatedTime = 0;
        lastProcessedFromGridId = -1;
        lastProcessedToGridId = -1;
        lastProcessedTime = 0;
        lastProcessedPortalGobId = -1;
        lastGridId = -1;
        lastSegmentId = -1;
        debugLog.log("[clear] Tracker state cleared");
    }
    
    /**
     * Static method to clear tracker from anywhere (for tests).
     */
    public static void clearInstance() {
        if (instance != null) {
            instance.clear();
        }
    }
    
    /**
     * Call periodically from game loop.
     * Should be called from NMapView.tick() or NCore.tick().
     */
    public void tick() {
        // Log player position every 500ms
        positionLogger.tick();
        
        // Check config
        Object enabledObj = NConfig.get(NConfig.Key.portalMarkerAutoCreate);
        boolean configEnabled = (enabledObj instanceof Boolean) && (Boolean) enabledObj;

        debugLog.log("[tick] enabled=" + enabled + ", configEnabled=" + configEnabled);

        if (!enabled || !configEnabled) {
            debugLog.log("[tick] DISABLED - returning");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        debugLog.log("[tick] calling doCheck()");

        try {
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
        debugLog.log("[doCheck] START");
        
        GameUI gui = NUtils.getGameUI();
        if (gui == null || gui.map == null) {
            debugLog.log("[doCheck] gui or map is null - returning");
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

        // Capture portal from lastActions BEFORE checking grid change
        // This is critical - portal must be captured before onGridChanged() is called
        NCore.LastActions lastActions = NUtils.getUI().core.getLastActions();
        if (lastActions != null && lastActions.gob != null && lastActions.gob.ngob != null) {
            String gobName = lastActions.gob.ngob.name.toLowerCase();
            debugLog.log("[doCheck] lastActions.gob=" + lastActions.gob.ngob.name);
            if (isPortalGob(gobName) && lastActions.gob.id != lastProcessedPortalGobId) {
                debugLog.log("[doCheck] Portal CAPTURED: " + lastActions.gob.ngob.name);
                cachedPortalGob = lastActions.gob;
                cachedPortalLocalCoord = getGobLocalCoord(lastActions.gob);
                cachedPortalPlayerPosition = player.rc; // Save player position at time of portal capture
                lastProcessedPortalGobId = lastActions.gob.id; // Mark as processed to prevent recapture
                debugLog.log("[doCheck] Portal local coords: " + cachedPortalLocalCoord + ", player at " + cachedPortalPlayerPosition);

                // Get portal's grid ID
                if (cachedPortalLocalCoord != null) {
                    try {
                        MCache.Grid portalGrid = mcache.getgridt(cachedPortalLocalCoord.floor(MCache.tilesz));
                        if (portalGrid != null) {
                            cachedPortalGridId = portalGrid.id;
                            debugLog.log("[doCheck] Portal gridId=" + cachedPortalGridId + " at " + cachedPortalLocalCoord.floor(MCache.tilesz));
                        }
                    } catch (Exception e) {
                        debugLog.log("[doCheck] Failed to get portal grid: " + e.getMessage());
                        // Continue without portal grid
                    }
                }
            }
        }

        // Initialize lastGridId on first run
        if (lastGridId == -1) {
            debugLog.log("[doCheck] Initializing lastGridId=" + currentGridId);
            lastGridId = currentGridId;
            lastSegmentId = currentSegmentId;
            return; // Skip grid change check on first run
        }

        // Check for segment change (layer transition via cave/minehole/ladder)
        // Cave passages change segment ID but NOT grid ID
        debugLog.log("[doCheck] Checking segment change: currentSegmentId=" + currentSegmentId + ", lastSegmentId=" + lastSegmentId);
        debugLog.log("[doCheck] Player position: " + player.rc);
        if (lastSegmentId != -1 && currentSegmentId != -1 && currentSegmentId != lastSegmentId) {
            debugLog.log("[doCheck] SEGMENT CHANGED DETECTED: " + lastSegmentId + " -> " + currentSegmentId);
            debugLog.log("[doCheck] Grid IDs: currentGridId=" + currentGridId + ", lastGridId=" + lastGridId);
            try {
                onGridChanged(lastGridId, currentGridId, lastSegmentId, currentSegmentId, player);
            } catch (Exception e) {
                debugLog.log("[doCheck] onGridChanged threw exception: " + e.getMessage());
                e.printStackTrace();
            }

            // Always update lastSegmentId after segment change
            lastGridId = currentGridId;
            lastSegmentId = currentSegmentId;
            return; // Skip rest of doCheck()
        }
        debugLog.log("[doCheck] No segment change");

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
        
        // Use cached portal coordinates and player position (captured BEFORE grid change)
        Coord2d portalCoords = cachedPortalLocalCoord;
        Coord2d playerPosAtPortal = cachedPortalPlayerPosition;
        if (portalCoords == null) {
            portalCoords = player.rc;
            playerPosAtPortal = player.rc;
            debugLog.log("[onGridChanged] Using player position as fallback");
        }

        // Create layer transition with player position at time of portal click
        LayerTransition transition = new LayerTransition(
            fromSegmentId,
            toSegmentId,
            portalCoords,
            portalName,
            playerPosAtPortal, // Player position when portal was clicked (for IN marker)
            player.rc          // Player position after transition (for OUT marker)
        );
        
        // Log transition
        logger.logTransition(fromSegmentId, toSegmentId, portalCoords, portalName);
        debugLog.log("[onGridChanged] Calling markerLinker.linkPortalMarkers()");
        
        // Create linked markers
        try {
            PortalMarkerLink link = markerLinker.linkPortalMarkers(transition);
            debugLog.log("[onGridChanged] Link created: " + link);

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
        cachedPortalPlayerPosition = null;
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
