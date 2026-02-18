package nurgling.overlays;

import haven.*;
import haven.render.*;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Debug overlay for CliffClimberTest bot.
 * Shows path line, cliff cells, neighbor cells, and target points.
 * 
 * THREAD SAFETY: All overlay operations are queued and executed safely to avoid
 * ConcurrentModificationException during game tick.
 */
public class CliffClimberDebugOverlay {
    private final MCache map;
    
    // Queue for pending overlay operations
    private final Queue<Runnable> pendingOperations = new ConcurrentLinkedQueue<>();
    
    // Overlay maps
    private final Map<Coord, MCache.Overlay> cliffOverlays = new HashMap<>();
    private final Map<Coord, MCache.Overlay> neighborOverlays = new HashMap<>();
    private final Map<Coord, MCache.Overlay> targetOverlays = new HashMap<>();
    private final Map<Coord, MCache.Overlay> pathOverlays = new HashMap<>();
    
    // Colors
    private static final Color CLIFF_COLOR = new Color(255, 100, 100, 150);      // Red - cliff cells
    private static final Color NEIGHBOR_COLOR = new Color(255, 255, 100, 150);   // Yellow - neighbor cells
    private static final Color TARGET_COLOR = new Color(100, 255, 100, 150);     // Green - target point
    private static final Color PATH_COLOR = new Color(100, 100, 255, 200);       // Blue - path cells
    
    public CliffClimberDebugOverlay(MCache map) {
        this.map = map;
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
     */
    public void clearAll() {
        pendingOperations.add(() -> {
            clearMap(cliffOverlays);
            clearMap(neighborOverlays);
            clearMap(targetOverlays);
            clearMap(pathOverlays);
        });
        // Don't process immediately - let the main loop handle it
    }
    
    private void clearMap(Map<Coord, MCache.Overlay> overlayMap) {
        List<MCache.Overlay> toDestroy = new ArrayList<>(overlayMap.values());
        overlayMap.clear();
        for (MCache.Overlay overlay : toDestroy) {
            try {
                overlay.destroy();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Mark cliff cells in red
     */
    public void markCliffCells(Collection<Coord> cells) {
        pendingOperations.add(() -> {
            clearMap(cliffOverlays);
            for (Coord cell : cells) {
                markCell(cell, CLIFF_COLOR, cliffOverlays);
            }
        });
    }
    
    /**
     * Mark neighbor cells in yellow
     */
    public void markNeighborCells(Collection<Coord> cells) {
        pendingOperations.add(() -> {
            clearMap(neighborOverlays);
            for (Coord cell : cells) {
                markCell(cell, NEIGHBOR_COLOR, neighborOverlays);
            }
        });
    }
    
    /**
     * Mark target point in green
     */
    public void markTarget(Coord cell) {
        pendingOperations.add(() -> {
            clearMap(targetOverlays);
            markCell(cell, TARGET_COLOR, targetOverlays);
        });
    }
    
    /**
     * Mark path cells in blue
     */
    public void markPathCells(Collection<Coord> cells) {
        pendingOperations.add(() -> {
            clearMap(pathOverlays);
            for (Coord cell : cells) {
                markCell(cell, PATH_COLOR, pathOverlays);
            }
        });
    }
    
    private void markCell(Coord cell, Color color, Map<Coord, MCache.Overlay> overlayMap) {
        try {
            Area area = new Area(cell, cell.add(1, 1));
            MCache.Overlay overlay = map.new Overlay(area, createOverlayInfo(color));
            overlayMap.put(cell, overlay);
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Draw a line showing the planned path by marking cells
     */
    public void drawPathLine(Coord start, Coord end) {
        pendingOperations.add(() -> {
            try {
                // Clear previous path overlays
                clearMap(pathOverlays);
                
                // Mark path cells between start and end
                int dx = Integer.compare(end.x, start.x);
                int dy = Integer.compare(end.y, start.y);
                
                Coord current = start;
                while (!current.equals(end)) {
                    markCell(current, PATH_COLOR, pathOverlays);
                    current = current.add(dx, dy);
                }
                markCell(end, PATH_COLOR, pathOverlays);
            } catch (Exception e) {
                // Ignore
            }
        });
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
