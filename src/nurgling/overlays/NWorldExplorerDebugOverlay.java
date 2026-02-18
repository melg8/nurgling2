package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NMapView;
import nurgling.NUtils;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Debug overlay for WorldExplorer bot.
 * Shows visited tiles (light green), wall tiles (light yellow), and current target (light blue).
 * 
 * THREAD SAFETY: All overlay operations are queued and executed safely to avoid
 * ConcurrentModificationException during game tick.
 */
public class NWorldExplorerDebugOverlay {
    // Queue for pending overlay operations
    private final Queue<Runnable> pendingOperations = new ConcurrentLinkedQueue<>();
    
    // Overlay maps: tile coordinate -> overlay
    private final Map<Coord, MCache.Overlay> visitedOverlays = new HashMap<>();
    private final Map<Coord, MCache.Overlay> wallOverlays = new HashMap<>();
    private MCache.Overlay targetOverlay = null;
    
    private final MCache map;
    
    // Colors
    private static final Color VISITED_COLOR = new Color(144, 238, 144, 128);  // Light green
    private static final Color WALL_COLOR = new Color(255, 255, 224, 128);     // Light yellow
    private static final Color TARGET_COLOR = new Color(173, 216, 230, 128);   // Light blue
    
    // Flag to prevent modifications during game tick
    private volatile boolean isTickInProgress = false;
    
    public NWorldExplorerDebugOverlay(MCache map) {
        this.map = map;
    }
    
    /**
     * Mark a tile as visited (light green)
     */
    public void markVisited(Coord tile) {
        pendingOperations.add(() -> {
            clearTileInternal(tile);
            
            Area area = new Area(tile, tile.add(1, 1));
            MCache.Overlay overlay = map.new Overlay(area, createOverlayInfo(VISITED_COLOR));
            visitedOverlays.put(tile, overlay);
        });
    }
    
    /**
     * Mark a tile as wall (light yellow)
     */
    public void markWall(Coord tile) {
        pendingOperations.add(() -> {
            clearTileInternal(tile);
            
            Area area = new Area(tile, tile.add(1, 1));
            MCache.Overlay overlay = map.new Overlay(area, createOverlayInfo(WALL_COLOR));
            wallOverlays.put(tile, overlay);
        });
    }
    
    /**
     * Mark the current target tile (light blue)
     */
    public void markTarget(Coord tile) {
        pendingOperations.add(() -> {
            clearTargetInternal();
            
            Area area = new Area(tile, tile.add(1, 1));
            targetOverlay = map.new Overlay(area, createOverlayInfo(TARGET_COLOR));
        });
    }
    
    /**
     * Clear a specific tile from all overlays
     */
    public void clearTile(Coord tile) {
        pendingOperations.add(() -> clearTileInternal(tile));
    }
    
    private void clearTileInternal(Coord tile) {
        MCache.Overlay overlay = visitedOverlays.remove(tile);
        if (overlay != null) {
            overlay.destroy();
        }
        
        overlay = wallOverlays.remove(tile);
        if (overlay != null) {
            overlay.destroy();
        }
    }
    
    /**
     * Clear only the target overlay
     */
    public void clearTarget() {
        pendingOperations.add(() -> clearTargetInternal());
    }
    
    private void clearTargetInternal() {
        if (targetOverlay != null) {
            targetOverlay.destroy();
            targetOverlay = null;
        }
    }
    
    /**
     * Process pending operations safely.
     * Call this from main thread when it's safe to modify overlays.
     */
    public void processPendingOperations() {
        Runnable op;
        while ((op = pendingOperations.poll()) != null) {
            try {
                op.run();
            } catch (Exception e) {
                // Ignore errors during overlay operations
            }
        }
    }
    
    /**
     * Clear all overlays safely
     * Uses a copy of the collections to avoid ConcurrentModificationException
     */
    public void clearAll() {
        pendingOperations.add(() -> {
            // Create copies to avoid concurrent modification
            List<MCache.Overlay> toDestroy = new ArrayList<>();
            toDestroy.addAll(visitedOverlays.values());
            toDestroy.addAll(wallOverlays.values());
            if (targetOverlay != null) {
                toDestroy.add(targetOverlay);
            }
            
            // Clear maps first
            visitedOverlays.clear();
            wallOverlays.clear();
            targetOverlay = null;
            
            // Then destroy overlays
            for (MCache.Overlay overlay : toDestroy) {
                try {
                    overlay.destroy();
                } catch (Exception e) {
                    // Ignore concurrent modification errors during cleanup
                }
            }
        });
        
        // Process immediately
        processPendingOperations();
    }
    
    private MCache.OverlayInfo createOverlayInfo(Color color) {
        return new MCache.OverlayInfo() {
            final Material mat = new Material(
                new BaseColor(color),
                States.maskdepth
            );
            
            public Collection<String> tags() {
                return Arrays.asList("show");
            }
            
            public Material mat() {
                return mat;
            }
        };
    }
}
