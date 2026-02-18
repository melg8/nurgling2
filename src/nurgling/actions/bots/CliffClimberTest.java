package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.GoTo;
import nurgling.actions.Results;
import nurgling.overlays.CliffClimberDebugOverlay;
import nurgling.widgets.bots.CliffClimberTestWnd;
import haven.render.*;

import java.awt.Color;
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
 * - Shows FULL PATH from start to end in ORANGE (emulating complete travel through cliff)
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
    
    // Full path visualization (similar to NPathVisualizer)
    private static final VertexArray.Layout PATH_LAYOUT = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 12));
    private static final float PATH_Z = 1f;
    private static final Color FULL_PATH_COLOR = new Color(255, 165, 0, 255);  // Orange path
    private Model fullPathModel;
    private Collection<RenderTree.Slot> fullPathSlots = new ArrayList<>(1);
    private Pipe.Op fullPathState;
    
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
        
        // Initialize full path rendering state
        fullPathState = Pipe.Op.compose(
                new BaseColor(FULL_PATH_COLOR),
                new States.LineWidth(2.0f),
                Rendered.last, States.Depthtest.none, States.maskdepth
        );

        try {
            // Get initial look direction from player
            lookDir = getLookDirection();
            
            gui.ui.gui.msg("CliffClimberTest started. Visualization active.");
            gui.ui.gui.msg("Move around - visualization will follow your movement direction");
            
            // Main loop - continuously update visualization
            lastPlayerPos = NUtils.player().rc.div(MCache.tilesz).floor();  // Initialize position
            int lastLookDir = lookDir;
            
            // Register full path overlay in render tree
            RenderTree.Slot fullPathSlot = gameui.map.basic.add(new RenderTree.Node() {
                @Override
                public void added(RenderTree.Slot slot) {
                    slot.ostate(fullPathState);
                    fullPathSlots.add(slot);
                }
                
                @Override
                public void removed(RenderTree.Slot slot) {
                    fullPathSlots.remove(slot);
                }
                
                public void draw(Pipe context, Render out) {
                    if (fullPathModel != null) {
                        out.draw(context, fullPathModel);
                    }
                }
            });
            
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
            // Clear full path model
            fullPathModel = null;
            for (RenderTree.Slot slot : fullPathSlots) {
                slot.remove();
            }
            fullPathSlots.clear();
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
                
                // Draw full path from player to final target (emulating travel through cliff)
                updateFullPath(playerPos, analysis, gui);
            } else {
                // No cliff - just show path (first 5 cells)
                Coord endPos = pathCells.get(Math.min(pathCells.size() - 1, 5));
                debugOverlay.setPath(playerPos, endPos);
                
                // Draw short path ahead (no cliff detected)
                updateFullPathNoCliff(playerPos, pathCells, gui);
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

    /**
     * Update full path visualization from player to final target
     * Shows the complete emulated travel path through the cliff
     */
    private void updateFullPath(Coord playerPos, CliffAnalysis analysis, NGameUI gui) {
        try {
            // Build complete path: player -> approach -> cliff crossing -> target -> 2 cells beyond
            List<Coord3f> pathPoints = new ArrayList<>();
            
            // Start from player position
            Coord2d playerWorld = playerPos.mul(MCache.tilesz).add(MCache.tilesz.div(2));
            pathPoints.add(new Coord3f((float)playerWorld.x, -(float)playerWorld.y, (float)getZ(playerPos, gui) + PATH_Z));
            
            // Add cliff cells
            for (Coord cliffCell : analysis.cliffCells) {
                Coord2d world = cliffCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
                pathPoints.add(new Coord3f((float)world.x, -(float)world.y, (float)getZ(cliffCell, gui) + PATH_Z));
            }
            
            // Add neighbor cells (approach path)
            for (Coord neighborCell : analysis.neighborCells) {
                Coord2d world = neighborCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
                pathPoints.add(new Coord3f((float)world.x, -(float)world.y, (float)getZ(neighborCell, gui) + PATH_Z));
            }
            
            // Add target cell
            if (analysis.targetCell != null) {
                Coord2d world = analysis.targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
                pathPoints.add(new Coord3f((float)world.x, -(float)world.y, (float)getZ(analysis.targetCell, gui) + PATH_Z));
                
                // Add 2 cells beyond target in look direction
                Coord dir = dirVectors[lookDir];
                for (int i = 1; i <= 2; i++) {
                    Coord beyond = analysis.targetCell.add(dir.mul(i));
                    Coord2d worldBeyond = beyond.mul(MCache.tilesz).add(MCache.tilesz.div(2));
                    pathPoints.add(new Coord3f((float)worldBeyond.x, -(float)worldBeyond.y, (float)getZ(beyond, gui) + PATH_Z));
                }
            }
            
            updateFullPathModel(pathPoints);
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    /**
     * Update full path visualization when no cliff is detected
     * Shows short path ahead
     */
    private void updateFullPathNoCliff(Coord playerPos, List<Coord> pathCells, NGameUI gui) {
        try {
            List<Coord3f> pathPoints = new ArrayList<>();
            
            // Start from player position
            Coord2d playerWorld = playerPos.mul(MCache.tilesz).add(MCache.tilesz.div(2));
            pathPoints.add(new Coord3f((float)playerWorld.x, -(float)playerWorld.y, (float)getZ(playerPos, gui) + PATH_Z));
            
            // Add first 10 path cells
            for (int i = 0; i < Math.min(10, pathCells.size()); i++) {
                Coord cell = pathCells.get(i);
                Coord2d world = cell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
                pathPoints.add(new Coord3f((float)world.x, -(float)world.y, (float)getZ(cell, gui) + PATH_Z));
            }
            
            updateFullPathModel(pathPoints);
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    /**
     * Update the full path model with new points
     */
    private void updateFullPathModel(List<Coord3f> pathPoints) {
        if (pathPoints.isEmpty()) {
            fullPathModel = null;
            updateFullPathSlots();
            return;
        }
        
        try {
            // Convert path points to line segments
            float[] data = new float[pathPoints.size() * 3];
            for (int i = 0; i < pathPoints.size(); i++) {
                Coord3f p = pathPoints.get(i);
                data[i * 3] = p.x;
                data[i * 3 + 1] = p.y;
                data[i * 3 + 2] = p.z;
            }
            
            VertexArray.Buffer vbo = new VertexArray.Buffer(data.length * 4,
                    DataBuffer.Usage.STATIC, DataBuffer.Filler.of(data));
            VertexArray va = new VertexArray(PATH_LAYOUT, vbo);
            
            fullPathModel = new Model(Model.Mode.LINE_STRIP, va, null);
            updateFullPathSlots();
        } catch (Exception e) {
            fullPathModel = null;
        }
    }
    
    /**
     * Update all full path rendering slots
     */
    private void updateFullPathSlots() {
        Collection<RenderTree.Slot> tslots;
        synchronized (fullPathSlots) {
            tslots = new ArrayList<>(fullPathSlots);
        }
        try {
            tslots.forEach(RenderTree.Slot::update);
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Get Z coordinate (height) at given tile position
     */
    private float getZ(Coord tc, NGameUI gui) {
        try {
            return (float) gui.ui.sess.glob.map.getfz(tc);
        } catch (Exception e) {
            return 0f;
        }
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
