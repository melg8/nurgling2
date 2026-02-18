package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.overlays.CliffClimberDebugOverlay;
import nurgling.widgets.bots.CliffClimberTestWnd;
import haven.render.*;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Cliff Crossing Test Bot - Tests cliff crossing logic with full path visualization
 * 
 * Purpose: Test and debug cliff crossing behavior before integrating into Explorer bot
 * 
 * Behavior:
 * 1. Wait for user to set look direction (WASD or arrow keys)
 * 2. Move forward in that direction until reaching cell adjacent to cliff
 * 3. Analyze cliff pattern:
 *    - SINGLE_CLIFF (1 cell): Click through center of cliff cell to center of next cell beyond
 *    - DOUBLE_CLIFF (2 cells): Find free cell in row 2, approach corner, click corner, then center
 * 4. Wait for climb/descend animation to complete
 * 5. Verify correct positioning
 * 6. Continue movement until 2 cells beyond cliff on opposite side
 * 
 * Visualization (ORANGE line shows complete planned path):
 * - Player position → Cliff cells → Approach path → Target → 2 cells beyond
 * 
 * Controls:
 * - WASD or Arrow keys: Set movement direction
 * - Space: Start movement
 * - Escape: Cancel
 */
public class CliffCrossingTestBot implements Action {

    // Direction vectors: 0=East, 1=North, 2=West, 3=South
    private static final Coord[] dirVectors = {
        Coord.of(1, 0),   // East
        Coord.of(0, -1),  // North
        Coord.of(-1, 0),  // West
        Coord.of(0, 1)    // South
    };
    
    private static final String[] dirNames = {"East", "North", "West", "South"};

    private CliffClimberDebugOverlay debugOverlay;
    private int lookDir = 1;  // Default look direction (North)
    private boolean directionLocked = false;  // Wait for user to confirm direction
    
    // Full path visualization (similar to NPathVisualizer)
    private static final VertexArray.Layout PATH_LAYOUT = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 12));
    private static final float PATH_Z = 1f;
    private static final Color FULL_PATH_COLOR = new Color(255, 165, 0, 255);  // Orange path
    private Model fullPathModel;
    private Collection<RenderTree.Slot> fullPathSlots = new ArrayList<>(1);
    private Pipe.Op fullPathState;
    private RenderTree.Slot fullPathSlot;
    
    // Cancellation flag
    private volatile boolean cancelled = false;
    
    // Log file writer
    private PrintWriter logWriter = null;
    private String logFileName = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        GameUI gameui = NUtils.getGameUI();

        // Create log file
        try {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            logFileName = "bot_log_" + dateStr + ".txt";
            logWriter = new PrintWriter(new FileWriter(logFileName));
            log("=== Cliff Crossing Test Bot Started ===");
            log("Log file: " + logFileName);
        } catch (IOException e) {
            log(gui, "Failed to create log file: " + e.getMessage());
        }

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
            // Wait for user to set direction using character's look direction
            log(gui, "=== Cliff Crossing Test ===");
            log(gui, "Turn your character to set direction");
            log(gui, "Bot will start automatically in 5 seconds...");
            
            // Wait 5 seconds for user to set direction
            for (int i = 5; i > 0 && !cancelled; i--) {
                // Get current look direction from character
                int charDir = getCharacterLookDirection();
                if (charDir != -1 && charDir != lookDir) {
                    lookDir = charDir;
                    log(gui, "Direction updated: " + dirName(lookDir));
                    
                    // Update visualization to show new direction
                    Coord playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                    List<Coord> pathCells = getPathCells(playerPos, 20);
                    updateVisualizationNoCliff(playerPos, pathCells, gui);
                }
                
                log(gui, "Starting in " + i + "...");
                Thread.sleep(1000);
            }
            
            if (cancelled) {
                log(gui, "Cliff crossing test cancelled");
                return Results.FAIL();
            }
            
            log(gui, "Direction locked: " + dirName(lookDir));
            log(gui, "Starting cliff crossing test...");
            
            // Get initial position
            Coord playerPos = NUtils.player().rc.div(MCache.tilesz).floor();

            // Register full path overlay in render tree ONCE
            fullPathSlot = gameui.map.basic.add(new RenderTree.Node() {
                @Override
                public void added(RenderTree.Slot slot) {
                    slot.ostate(fullPathState);
                    fullPathSlots.add(slot);
                    log(gui, "Full path overlay added to render tree");
                    // Force initial update
                    if (fullPathModel != null) {
                        slot.update();
                        log(gui, "Initial path model updated");
                    }
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
            
            log(gui, "Full path overlay registered, slots=" + fullPathSlots.size());

            // Main test loop - move forward until 2 cells beyond cliff
            playerPos = executeCliffCrossing(gui, playerPos);
            
            log(gui, "Cliff crossing test completed successfully!");
            return Results.SUCCESS();
            
        } catch (InterruptedException e) {
            log(gui, "Cliff crossing test interrupted");
            cancelled = true;
            throw e;
        } catch (Exception e) {
            log(gui, "Cliff crossing test failed: " + e.getMessage());
            e.printStackTrace();
            return Results.FAIL();
        } finally {
            // Clear overlays safely
            cleanup(gui);
        }
    }
    
    /**
     * Execute complete cliff crossing maneuver
     */
    private Coord executeCliffCrossing(NGameUI gui, Coord startPos) throws InterruptedException {
        Coord playerPos = startPos;
        int stepsBeyondCliff = 0;
        boolean cliffCrossed = false;
        int maxIterations = 100; // Safety limit
        int iteration = 0;
        
        log(gui, "Starting movement in direction: " + dirName(lookDir));
        
        while (stepsBeyondCliff < 2 && !cancelled && iteration < maxIterations) {
            iteration++;
            playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
            log(gui, "Iteration " + iteration + ": Position=" + playerPos + ", stepsBeyond=" + stepsBeyondCliff + ", cliffCrossed=" + cliffCrossed);
            
            // Get path ahead
            List<Coord> pathCells = getPathCells(playerPos, 20);
            log(gui, "Checking " + pathCells.size() + " cells ahead for cliffs...");
            
            Coord firstCliff = findFirstCliff(pathCells, gui);
            
            if (firstCliff != null) {
                log(gui, "Cliff found at: " + firstCliff);
                // Analyze cliff and determine strategy
                CliffAnalysis analysis = analyzeCliff(firstCliff, lookDir, gui);
                
                // Update visualization
                updateVisualization(playerPos, analysis, pathCells, gui);
                
                if (!cliffCrossed) {
                    // Execute cliff crossing
                    log(gui, "Cliff detected! Type: " + analysis.cliffType);
                    log(gui, "Strategy: " + analysis.strategy);
                    
                    if (analysis.cliffType.equals("SINGLE_CLIFF")) {
                        // Single cliff - click through center
                        executeSingleCliffCrossing(gui, analysis, playerPos);
                    } else if (analysis.cliffType.equals("DOUBLE_CLIFF")) {
                        // Double cliff - approach corner and click
                        executeDoubleCliffCrossing(gui, analysis, playerPos);
                    } else {
                        log(gui, "Unknown cliff type: " + analysis.cliffType);
                    }
                    
                    // Wait for movement to complete
                    waitForMovementCompletion(gui);
                    
                    // Verify position and retry if needed
                    if (!verifyPositionAfterCrossing(gui, analysis)) {
                        log(gui, "Position verification failed - retrying...");
                        executeRetryCrossing(gui, analysis);
                    }
                    
                    cliffCrossed = true;
                    log(gui, "Cliff crossed successfully!");
                } else {
                    // Already crossed - just move forward
                    log(gui, "Moving beyond cliff, step " + (stepsBeyondCliff + 1));
                    moveForward(gui, playerPos);
                    waitForMovementCompletion(gui);
                    stepsBeyondCliff++;
                    Thread.sleep(1000);
                }
            } else {
                log(gui, "No cliff ahead, moving forward...");
                // No cliff ahead - move forward and update visualization
                updateVisualizationNoCliff(playerPos, pathCells, gui);
                moveForward(gui, playerPos);
                waitForMovementCompletion(gui);
                Thread.sleep(1000);
            }
        }
        
        log(gui, "Loop finished: iterations=" + iteration + ", stepsBeyond=" + stepsBeyondCliff);
        return playerPos;
    }
    
    /**
     * Execute crossing for single cliff cell
     * Click from current cell through cliff cell to center of cell beyond
     */
    private void executeSingleCliffCrossing(NGameUI gui, CliffAnalysis analysis, Coord playerPos) throws InterruptedException {
        Coord targetCell = analysis.targetCell;
        if (targetCell == null) {
            throw new RuntimeException("No target cell for single cliff crossing");
        }
        
        // Calculate world position (center of target cell)
        Coord2d targetWorld = targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        
        log(gui, "Single cliff - clicking center of target cell at: " + targetCell);
        
        // Click on target cell center
        NUtils.getGameUI().map.wdgmsg("click",
            Coord2d.of(targetWorld.x, targetWorld.y),
            0, 0, 0, 0, 0, 0);
        
        Thread.sleep(1000); // Wait for click to register
    }
    
    /**
     * Execute crossing for double cliff cells
     * 1. Move to corner of current cell adjacent to free cell in row 2
     * 2. Click corner (not center) of free cell
     * 3. Wait for movement
     * 4. Click center of free cell
     */
    private void executeDoubleCliffCrossing(NGameUI gui, CliffAnalysis analysis, Coord playerPos) throws InterruptedException {
        if (analysis.neighborCells.isEmpty()) {
            throw new RuntimeException("No free neighbor cells found for double cliff crossing");
        }
        
        Coord freeCell = analysis.neighborCells.get(0);
        log(gui, "Double cliff - using free cell at: " + freeCell);
        
        // Step 1: Move to corner of current cell
        Coord2d cornerPos = getCornerPosition(playerPos, freeCell);
        log(gui, "Moving to corner: " + cornerPos);
        
        NUtils.getGameUI().map.wdgmsg("click",
            Coord2d.of(cornerPos.x, cornerPos.y),
            0, 0, 0, 0, 0, 0);
        
        waitForMovementCompletion(gui);
        Thread.sleep(500);
        
        // Step 2: Click center of free cell
        Coord2d centerPos = freeCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        log(gui, "Clicking center of free cell: " + centerPos);
        
        NUtils.getGameUI().map.wdgmsg("click",
            Coord2d.of(centerPos.x, centerPos.y),
            0, 0, 0, 0, 0, 0);
        
        waitForMovementCompletion(gui);
    }
    
    /**
     * Get corner position of current cell closest to target cell
     */
    private Coord2d getCornerPosition(Coord currentCell, Coord targetCell) {
        Coord diff = targetCell.sub(currentCell);
        
        // Determine which corner based on direction
        double cornerX = currentCell.x * MCache.tilesz.x;
        double cornerY = currentCell.y * MCache.tilesz.y;
        
        // Add tile size if target is in positive direction
        if (diff.x > 0) cornerX += MCache.tilesz.x;  // Right side
        if (diff.y > 0) cornerY += MCache.tilesz.y;  // Bottom side
        
        return new Coord2d(cornerX, cornerY);
    }
    
    /**
     * Execute retry crossing if position verification fails
     */
    private void executeRetryCrossing(NGameUI gui, CliffAnalysis analysis) throws InterruptedException {
        log(gui, "Retrying crossing...");
        
        if (analysis.targetCell != null) {
            Coord2d targetWorld = analysis.targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
            NUtils.getGameUI().map.wdgmsg("click",
                Coord2d.of(targetWorld.x, targetWorld.y),
                0, 0, 0, 0, 0, 0);
            
            waitForMovementCompletion(gui);
        }
    }
    
    /**
     * Wait for movement to complete
     */
    private void waitForMovementCompletion(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) {
            log(gui, "Player is null");
            return;
        }
        
        Moving moving = player.getattr(Moving.class);
        if (moving != null) {
            log(gui, "Waiting for movement...");
            int timeout = 0;
            while (player.getattr(Moving.class) != null && timeout < 50) {
                Thread.sleep(200);
                timeout++;
            }
            log(gui, "Movement done (timeout=" + timeout + ")");
            Thread.sleep(500);
        } else {
            log(gui, "Not moving");
        }
    }
    
    /**
     * Verify player position after crossing
     */
    private boolean verifyPositionAfterCrossing(NGameUI gui, CliffAnalysis analysis) {
        try {
            Coord playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (analysis.targetCell != null) {
                // Check if player is at or near target cell
                int dx = Math.abs(playerPos.x - analysis.targetCell.x);
                int dy = Math.abs(playerPos.y - analysis.targetCell.y);
                return (dx <= 1 && dy <= 1);
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Simple forward movement
     */
    private void moveForward(NGameUI gui, Coord playerPos) throws InterruptedException {
        Coord dir = dirVectors[lookDir];
        Coord targetCell = playerPos.add(dir);
        Coord2d targetWorld = targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        
        log(gui, "Moving to: " + targetCell);
        
        NUtils.getGameUI().map.wdgmsg("click",
            Coord2d.of(targetWorld.x, targetWorld.y),
            0, 0, 0, 0, 0, 0);
        
        log(gui, "Click sent");
        Thread.sleep(500);
    }
    
    /**
     * Get character's current look direction from movement or orientation
     * @return Direction index (0=East, 1=North, 2=West, 3=South) or -1 if unknown
     */
    private int getCharacterLookDirection() {
        try {
            Gob player = NUtils.player();
            if (player == null) return -1;
            
            // Get player's orientation angle
            double angle = player.a;
            
            // Convert angle to direction (0=East, 1=North, 2=West, 3=South)
            // Normalize angle to 0-2PI
            angle = Utils.cangle(angle);
            
            // Divide circle into 4 quadrants
            if (angle >= -Math.PI/4 && angle < Math.PI/4) {
                return 0; // East
            } else if (angle >= Math.PI/4 && angle < 3*Math.PI/4) {
                return 1; // North
            } else if (angle >= 3*Math.PI/4 || angle < -3*Math.PI/4) {
                return 2; // West
            } else {
                return 3; // South
            }
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Log message to both GUI and file
     */
    private void log(NGameUI gui, String message) {
        if (gui != null) {
            gui.ui.gui.msg(message);
        }
        log(message);
    }
    
    /**
     * Log message to file
     */
    private void log(String message) {
        if (logWriter != null) {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logWriter.println("[" + timestamp + "] " + message);
            logWriter.flush();
        }
    }
    
    /**
     * Cleanup resources
     */
    private void cleanup(NGameUI gui) {
        cancelled = true;
        
        log(gui, "=== Cleaning up ===");
        
        if (debugOverlay != null) {
            debugOverlay.clearAll();
        }

        fullPathModel = null;
        if (fullPathSlot != null) {
            try {
                fullPathSlot.remove();
            } catch (RenderTree.SlotRemoved e) {
                // Slot already removed, ignore
            }
            fullPathSlot = null;
        }
        fullPathSlots.clear();
        
        // Close log file
        if (logWriter != null) {
            log("=== Test finished ===");
            logWriter.close();
            logWriter = null;
            if (gui != null) {
                log(gui, "Log saved to: " + logFileName);
            }
        }
    }
    
    /**
     * Cleanup resources (no GUI)
     */
    private void cleanup() {
        cleanup(null);
    }

    /**
     * Get direction name
     */
    private String dirName(int dir) {
        return dirNames[dir];
    }
    
    /**
     * Update visualization with cliff analysis
     */
    private void updateVisualization(Coord playerPos, CliffAnalysis analysis, List<Coord> pathCells, NGameUI gui) {
        // Update debug overlay
        debugOverlay.setCliffCells(analysis.cliffCells);
        debugOverlay.setNeighborCells(analysis.neighborCells);
        debugOverlay.setTargetCell(analysis.targetCell);
        debugOverlay.setPathCells(pathCells);
        debugOverlay.update();
        
        // Update full path
        updateFullPath(playerPos, analysis, gui);
    }
    
    /**
     * Update visualization when no cliff detected
     */
    private void updateVisualizationNoCliff(Coord playerPos, List<Coord> pathCells, NGameUI gui) {
        debugOverlay.setCliffCells(new HashSet<>());
        debugOverlay.setNeighborCells(new HashSet<>());
        debugOverlay.setTargetCell(null);
        debugOverlay.setPathCells(pathCells);
        debugOverlay.update();
        
        updateFullPathNoCliff(playerPos, pathCells, gui);
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
     * Check if a cell has a cliff
     */
    private boolean isCliffCell(Coord tc, NGameUI gui) {
        try {
            MCache map = gui.ui.sess.glob.map;
            
            // Get center height
            double centerZ = map.getfz(tc);
            double threshold = 3.0;  // Height difference threshold
            
            log(gui, "Checking cell " + tc + " for cliff, centerZ=" + centerZ);
            
            // Check all 4 neighbors for height difference
            Coord[] neighbors = {
                tc.add(1, 0), tc.add(-1, 0),
                tc.add(0, 1), tc.add(0, -1)
            };
            
            for (Coord n : neighbors) {
                double neighborZ = map.getfz(n);
                double diff = Math.abs(centerZ - neighborZ);
                log(gui, "  Neighbor " + n + " has Z=" + neighborZ + ", diff=" + diff);
                
                if (diff > threshold) {
                    log(gui, "  CLIFF DETECTED at " + tc + " (diff=" + diff + " > " + threshold + ")");
                    return true;
                }
            }
        } catch (Exception e) {
            log(gui, "Error checking cliff at " + tc + ": " + e.getMessage());
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
                analysis.neighborCells.add(freeCell);
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
     * Update full path visualization from player to final target
     */
    private void updateFullPath(Coord playerPos, CliffAnalysis analysis, NGameUI gui) {
        try {
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

            updateFullPathModel(pathPoints, gui);
        } catch (Exception e) {
            // Ignore errors
        }
    }

    /**
     * Update full path visualization when no cliff is detected
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

            updateFullPathModel(pathPoints, gui);
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    /**
     * Update the full path model with new points
     */
    private void updateFullPathModel(List<Coord3f> pathPoints, NGameUI gui) {
        if (pathPoints.isEmpty()) {
            fullPathModel = null;
            updateFullPathSlots(gui);
            return;
        }

        try {
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
            updateFullPathSlots(gui);
        } catch (Exception e) {
            fullPathModel = null;
        }
    }
    
    /**
     * Update all full path rendering slots
     */
    private void updateFullPathSlots(NGameUI gui) {
        Collection<RenderTree.Slot> tslots;
        synchronized (fullPathSlots) {
            tslots = new ArrayList<>(fullPathSlots);
        }
        try {
            log(gui, "Updating full path slots, count=" + tslots.size());
            for (RenderTree.Slot slot : tslots) {
                slot.update();
            }
        } catch (Exception e) {
            log(gui, "Error updating slots: " + e.getMessage());
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
