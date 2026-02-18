package nurgling.overlays;

import haven.*;
import haven.render.*;
import java.awt.Color;
import java.util.*;

/**
 * Debug overlay for CliffClimberTest bot.
 * Shows path line, cliff cells, neighbor cells, and target points.
 * 
 * THREAD SAFETY: Uses batched updates to avoid ConcurrentModificationException.
 * All updates happen in a single batch per frame.
 */
public class CliffClimberDebugOverlay {
    private final MCache map;
    
    // Current state
    private Set<Coord> cliffCells = new HashSet<>();
    private Set<Coord> neighborCells = new HashSet<>();
    private Set<Coord> targetCells = new HashSet<>();
    private Set<Coord> pathCells = new HashSet<>();
    
    // Active overlays (key = "type_x_y")
    private Map<String, MCache.Overlay> activeOverlays = new HashMap<>();
    
    // Colors
    private static final Color CLIFF_COLOR = new Color(255, 100, 100, 150);      // Red - cliff cells
    private static final Color NEIGHBOR_COLOR = new Color(255, 255, 100, 150);   // Yellow - neighbor cells
    private static final Color TARGET_COLOR = new Color(100, 255, 100, 150);     // Green - target point
    private static final Color PATH_COLOR = new Color(100, 100, 255, 200);       // Blue - path cells
    
    public CliffClimberDebugOverlay(MCache map) {
        this.map = map;
    }
    
    /**
     * Process all pending updates and sync overlays with current state.
     * Call this from main thread when it's safe to modify overlays.
     */
    public void update() {
        try {
            // Update cliff overlays
            syncOverlays(cliffCells, CLIFF_COLOR, "cliff_");
            
            // Update neighbor overlays
            syncOverlays(neighborCells, NEIGHBOR_COLOR, "neighbor_");
            
            // Update target overlays
            syncOverlays(targetCells, TARGET_COLOR, "target_");
            
            // Update path overlays
            syncOverlays(pathCells, PATH_COLOR, "path_");
        } catch (Exception e) {
            // Ignore overlay errors
        }
    }
    
    /**
     * Sync overlays with desired cell set
     */
    private void syncOverlays(Set<Coord> desiredCells, Color color, String prefix) {
        // Find cells to remove (in active but not in desired)
        List<String> toRemove = new ArrayList<>();
        for (String key : activeOverlays.keySet()) {
            if (key.startsWith(prefix)) {
                Coord cell = parseCoord(key.substring(prefix.length()));
                if (cell != null && !desiredCells.contains(cell)) {
                    toRemove.add(key);
                }
            }
        }
        
        // Remove old overlays
        for (String key : toRemove) {
            MCache.Overlay overlay = activeOverlays.remove(key);
            if (overlay != null) {
                try {
                    overlay.destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        
        // Add new overlays
        for (Coord cell : desiredCells) {
            String key = prefix + cell.x + "_" + cell.y;
            if (!activeOverlays.containsKey(key)) {
                try {
                    Area area = new Area(cell, cell.add(1, 1));
                    MCache.Overlay overlay = map.new Overlay(area, createOverlayInfo(color));
                    activeOverlays.put(key, overlay);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    private Coord parseCoord(String s) {
        try {
            String[] parts = s.split("_");
            return new Coord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Clear all overlays safely
     */
    public void clearAll() {
        cliffCells.clear();
        neighborCells.clear();
        targetCells.clear();
        pathCells.clear();
        update();  // Process immediately
    }
    
    /**
     * Set cliff cells
     */
    public void setCliffCells(Collection<Coord> cells) {
        cliffCells = new HashSet<>(cells);
    }
    
    /**
     * Set neighbor cells
     */
    public void setNeighborCells(Collection<Coord> cells) {
        neighborCells = new HashSet<>(cells);
    }
    
    /**
     * Set target cell
     */
    public void setTargetCell(Coord cell) {
        targetCells.clear();
        if (cell != null) {
            targetCells.add(cell);
        }
    }
    
    /**
     * Set path cells
     */
    public void setPathCells(Collection<Coord> cells) {
        pathCells = new HashSet<>(cells);
    }
    
    /**
     * Set path from start to end
     */
    public void setPath(Coord start, Coord end) {
        pathCells.clear();
        
        int dx = Integer.compare(end.x, start.x);
        int dy = Integer.compare(end.y, start.y);
        
        Coord current = start;
        while (!current.equals(end)) {
            pathCells.add(current);
            current = current.add(dx, dy);
        }
        pathCells.add(end);
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
