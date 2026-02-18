package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.GoTo;
import nurgling.actions.Results;
import nurgling.overlays.CliffClimberDebugOverlay;
import nurgling.widgets.bots.CliffClimberTestWnd;

import java.util.*;

/**
 * Cliff Climber Test Bot (#16)
 * 
 * Tests cliff crossing logic with debug visualization.
 * 
 * Behavior:
 * 1. Move forward in look direction until reaching cliff
 * 2. Analyze cliff pattern (1 or 2 cells ahead)
 * 3. Cross cliff using appropriate strategy
 * 4. Move 2 cells beyond cliff
 * 
 * Visualization:
 * - Shows planned path for 20 cells ahead
 * - Marks cliff cells in red
 * - Marks neighbor cells in yellow
 * - Marks target point in green
 * - Shows path line in blue
 */
public class CliffClimberTest implements Action {
    
    // Direction vectors: 0=East, 1=North, 2=West, 3=South
    private static final Coord[] dirVectors = {
        Coord.of(1, 0),   // East
        Coord.of(0, -1),  // North
        Coord.of(-1, 0),  // West
        Coord.of(0, 1)    // South
    };
    
    private CliffClimberDebugOverlay debugOverlay;
    private int lookDir = 1;  // Default look direction (North)
    private Coord lastPlayerPos = null;  // Track player position for direction detection
    
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        GameUI gameui = NUtils.getGameUI();
        
        // Create window
        CliffClimberTestWnd w = null;
        try {
            NUtils.getUI().core.addTask(new nurgling.tasks.WaitCheckable(
                NUtils.getGameUI().add((w = new CliffClimberTestWnd()), UI.scale(300, 200))
            ));
        } catch (InterruptedException e) {
            throw e;
        } finally {
            if (w != null) w.destroy();
        }
        
        // Create debug overlay
        debugOverlay = new CliffClimberDebugOverlay(gameui.ui.sess.glob.map);
        
        try {
            // Get initial look direction from player
            lookDir = getLookDirection();
            
            gui.ui.gui.msg("CliffClimberTest started. Visualization active.");
            gui.ui.gui.msg("Move around - visualization will follow your movement direction");
            
            // Main loop - continuously update visualization
            lastPlayerPos = NUtils.player().rc.div(MCache.tilesz).floor();  // Initialize position
            int lastLookDir = lookDir;
            while (true) {
                Coord playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                
                // Update look direction based on player movement
                if (!playerPos.equals(lastPlayerPos)) {
                    lookDir = getLookDirection(playerPos, lastPlayerPos);
                    lastPlayerPos = playerPos;
                }
                
                // Update visualization if direction changed
                if (lookDir != lastLookDir) {
                    updateVisualization(playerPos, gui);
                    lastLookDir = lookDir;
                }
                
                Thread.sleep(100);
            }
        } finally {
            // Clear overlays safely
            if (debugOverlay != null) {
                debugOverlay.clearAll();
            }
            lastPlayerPos = null;  // Reset position tracker
        }
    }
    
    /**
     * Update debug visualization
     */
    private void updateVisualization(Coord playerPos, NGameUI gui) {
        try {
            // Get path cells (20 cells ahead in look direction)
            List<Coord> pathCells = getPathCells(playerPos, 20);
            
            // Find first cliff cell
            Coord firstCliff = findFirstCliff(pathCells, gui);
            
            if (firstCliff != null) {
                // Analyze cliff pattern
                CliffAnalysis analysis = analyzeCliff(firstCliff, lookDir, gui);
                
                // Set cliff cells
                debugOverlay.setCliffCells(analysis.cliffCells);
                
                // Set neighbor cells
                debugOverlay.setNeighborCells(analysis.neighborCells);
                
                // Set target
                debugOverlay.setTargetCell(analysis.targetCell);
                
                // Set path line (first 5 cells)
                Coord endPos = pathCells.get(Math.min(pathCells.size() - 1, 5));
                debugOverlay.setPath(playerPos, endPos);
            } else {
                // No cliff - just show path (first 5 cells)
                Coord endPos = pathCells.get(Math.min(pathCells.size() - 1, 5));
                debugOverlay.setPath(playerPos, endPos);
            }
            
            // Set all path cells
            debugOverlay.setPathCells(pathCells);
            
            // Update overlays
            debugOverlay.update();
            
        } catch (Exception e) {
            // Ignore visualization errors
        }
    }
    
    /**
     * Get path cells in look direction
     */
    private List<Coord> getPathCells(Coord start, int count) {
        List<Coord> cells = new ArrayList<>();
        Coord dir = dirVectors[lookDir];
        
        for (int i = 0; i < count; i++) {
            cells.add(start.add(dir.mul(i + 1)));
        }
        
        return cells;
    }
    
    /**
     * Find first cliff cell in path
     */
    private Coord findFirstCliff(List<Coord> pathCells, NGameUI gui) {
        for (Coord cell : pathCells) {
            if (isCliffCell(cell, gui)) {
                return cell;
            }
        }
        return null;
    }
    
    /**
     * Check if a cell has a cliff using the same method as highlight cliffs
     */
    private boolean isCliffCell(Coord tc, NGameUI gui) {
        try {
            MCache map = gui.ui.sess.glob.map;
            double centerZ = map.getfz(tc);
            double threshold = 3.0;  // Same as Ridges.java
            
            // Check all 4 neighbors for height difference
            Coord[] neighbors = {
                tc.add(1, 0), tc.add(-1, 0),
                tc.add(0, 1), tc.add(0, -1)
            };
            
            for (Coord n : neighbors) {
                double neighborZ = map.getfz(n);
                if (Math.abs(centerZ - neighborZ) > threshold) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
    
    /**
     * Analyze cliff pattern and determine crossing strategy
     */
    private CliffAnalysis analyzeCliff(Coord firstCliff, int direction, NGameUI gui) {
        CliffAnalysis analysis = new CliffAnalysis();
        analysis.cliffCells.add(firstCliff);
        
        Coord dir = dirVectors[direction];
        Coord secondCliff = firstCliff.add(dir);
        
        // Check if second cell is also a cliff
        if (isCliffCell(secondCliff, gui)) {
            analysis.cliffCells.add(secondCliff);
            analysis.cliffType = "DOUBLE_CLIFF";
            
            // Find alternative path through row 2
            Coord[] row2 = getRow2Cells(firstCliff, direction);
            Coord freeCell = findFreeCell(row2, gui);
            
            if (freeCell != null) {
                analysis.neighborCells.addAll(Arrays.asList(row2));
                analysis.targetCell = freeCell;
                analysis.strategy = "CORNER_APPROACH";
            }
        } else {
            analysis.cliffType = "SINGLE_CLIFF";
            
            // Target is cell after cliff
            Coord target = firstCliff.add(dir.mul(2));
            analysis.targetCell = target;
            analysis.strategy = "DIRECT_CLICK";
        }
        
        return analysis;
    }
    
    /**
     * Get row 2 cells (cells one row to the side of the path)
     */
    private Coord[] getRow2Cells(Coord start, int direction) {
        Coord[] row2 = new Coord[2];
        Coord dir = dirVectors[direction];
        Coord sideDir;
        
        // Get perpendicular direction
        if (direction == 0 || direction == 2) {
            sideDir = Coord.of(0, -1);  // North-South for East-West path
        } else {
            sideDir = Coord.of(1, 0);  // East-West for North-South path
        }
        
        // Get both sides
        row2[0] = start.add(dir).add(sideDir);
        row2[1] = start.add(dir).add(sideDir.mul(-1));
        
        return row2;
    }
    
    /**
     * Find a free (non-cliff) cell from the given cells
     */
    private Coord findFreeCell(Coord[] cells, NGameUI gui) {
        for (Coord cell : cells) {
            if (!isCliffCell(cell, gui)) {
                return cell;
            }
        }
        return null;
    }
    
    /**
     * Get movement direction based on player position change
     * @param currentPos Current player position
     * @param lastPos Previous player position
     * @return Direction: 0=East, 1=North, 2=West, 3=South
     */
    private int getLookDirection(Coord currentPos, Coord lastPos) {
        int dx = currentPos.x - lastPos.x;
        int dy = currentPos.y - lastPos.y;
        
        // Determine primary direction of movement
        if (Math.abs(dx) > Math.abs(dy)) {
            // Horizontal movement
            return (dx > 0) ? 0 : 2;  // East or West
        } else if (dy != 0) {
            // Vertical movement
            return (dy < 0) ? 1 : 3;  // North or South (Y is inverted: negative = up/north)
        }
        
        // No significant movement - keep current direction
        return lookDir;
    }
    
    /**
     * Get initial look direction (defaults to North)
     */
    private int getLookDirection() {
        return 1;  // North
    }
    
    private String dirName(int dir) {
        return new String[]{"E", "N", "W", "S"}[dir];
    }
    
    /**
     * Cliff analysis result
     */
    private static class CliffAnalysis {
        String cliffType = "UNKNOWN";
        String strategy = "UNKNOWN";
        List<Coord> cliffCells = new ArrayList<>();
        List<Coord> neighborCells = new ArrayList<>();
        Coord targetCell = null;
    }
}
