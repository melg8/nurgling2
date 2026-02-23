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
            doCheck();
        } catch (Exception e) {
            // Silently ignore errors in tick
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
        
        // Check for grid change (portal transition)
        if (lastGridId != -1 && currentGridId != lastGridId) {
            onGridChanged(lastGridId, currentGridId, lastSegmentId, currentSegmentId, player);
        }
        
        // Capture portal from lastActions BEFORE grid change
        NCore.LastActions lastActions = NUtils.getUI().core.getLastActions();
        if (lastActions != null && lastActions.gob != null && lastActions.gob.ngob != null) {
            String gobName = lastActions.gob.ngob.name.toLowerCase();
            if (isPortalGob(gobName) && lastActions.gob.id != lastProcessedPortalGobId) {
                cachedPortalGob = lastActions.gob;
                cachedPortalLocalCoord = getGobLocalCoord(lastActions.gob);
                
                // Get portal's grid ID
                if (cachedPortalLocalCoord != null) {
                    MCache.Grid portalGrid = mcache.getgridt(cachedPortalLocalCoord.floor(MCache.tilesz));
                    if (portalGrid != null) {
                        cachedPortalGridId = portalGrid.id;
                    }
                }
            }
        }
        
        // Update state
        lastGridId = currentGridId;
        lastSegmentId = currentSegmentId;
    }
    
    /**
     * Called when player's grid ID changes.
     */
    private void onGridChanged(long fromGridId, long toGridId, 
                               long fromSegmentId, long toSegmentId, 
                               Gob player) {
        // Check for duplicate grid transition
        long now = System.currentTimeMillis();
        if (fromGridId == lastProcessedFromGridId && toGridId == lastProcessedToGridId &&
            (now - lastProcessedTime) < DUPLICATE_PREVENTION_MS) {
            return;
        }
        
        // Mark this transition as processed
        lastProcessedFromGridId = fromGridId;
        lastProcessedToGridId = toGridId;
        lastProcessedTime = now;
        
        // Check if player landed on their hearthfire - this indicates a teleport, not a portal traversal
        if (isPlayerOnHearthfire(player)) {
            logger.logSkippedTransition("hearthfire_teleport", 
                "fromGrid=" + fromGridId + ", toGrid=" + toGridId);
            return;
        }
        
        // If we don't have a cached portal, we didn't click a known portal - don't create markers
        if (cachedPortalGob == null || cachedPortalGob.ngob == null) {
            return;
        }
        
        String portalName = cachedPortalGob.ngob.name;
        
        // Exclude cellar from marking
        if (portalName.toLowerCase().contains("cellar")) {
            logger.logSkippedTransition("cellar_excluded", "portal=" + portalName);
            return;
        }
        
        // Check if this is a portal that should be marked
        if (!PortalName.shouldMarkPortal(portalName)) {
            logger.logSkippedTransition("unsupported_portal_type", "portal=" + portalName);
            return;
        }
        
        // Use cached portal coordinates (captured BEFORE grid change)
        Coord2d portalCoords = cachedPortalLocalCoord;
        if (portalCoords == null) {
            portalCoords = player.rc; // Fallback to player position
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
        
        // Create linked markers
        try {
            PortalMarkerLink link = markerLinker.linkPortalMarkers(transition);
            
            // Mark the cached portal as processed
            lastProcessedPortalGobId = cachedPortalGob.id;
            
        } catch (Exception e) {
            logger.logMarkerError("LINK_PORTAL_MARKERS_FAILED", 
                "transition=" + transition + ", error=" + e.getMessage());
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
