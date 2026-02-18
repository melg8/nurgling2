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
        int maxIterations = 100;
        int iteration = 0;
        int noProgressCount = 0;
        Coord firstCliff = null; // Track current cliff being crossed
        
        log(gui, "Starting movement in direction: " + dirName(lookDir));
        log(gui, "Session location tc: " + gui.mmap.sessloc.tc);
        log(gui, "Start position: " + startPos);
        
        while (stepsBeyondCliff < 2 && !cancelled && iteration < maxIterations && noProgressCount < 5) {
            iteration++;
            
            // Always get fresh player position
            Coord freshPlayerPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (freshPlayerPos.equals(playerPos)) {
                noProgressCount++;
                log(gui, "Iteration " + iteration + ": Position=" + playerPos + " (NO PROGRESS, count=" + noProgressCount + ")");
            } else {
                log(gui, "Iteration " + iteration + ": Position=" + freshPlayerPos + ", moved from " + playerPos);
                playerPos = freshPlayerPos;
                noProgressCount = 0;
            }
            
            // Get path ahead
            List<Coord> pathCells = getPathCells(playerPos, 20);
            log(gui, "Checking " + pathCells.size() + " cells ahead for cliffs...");

            Coord foundCliff = findFirstCliff(pathCells, gui);

            if (foundCliff != null) {
                // Check if this is a NEW cliff (different from previous)
                if (firstCliff != null && !foundCliff.equals(firstCliff)) {
                    // New cliff - reset cliffCrossed flag
                    cliffCrossed = false;
                    log(gui, "New cliff detected at " + foundCliff + " (previous was " + firstCliff + "), resetting cliffCrossed");
                }
                
                firstCliff = foundCliff; // Update current cliff
                log(gui, "Cliff found at: " + firstCliff);

                // Analyze cliff and determine strategy
                CliffAnalysis analysis = analyzeCliff(firstCliff, lookDir, gui);
                
                // Update visualization
                updateVisualization(playerPos, analysis, pathCells, gui);
                
                if (!cliffCrossed) {
                    // Execute cliff crossing
                    log(gui, "Cliff detected! Type: " + analysis.cliffType);
                    log(gui, "Strategy: " + analysis.strategy);
                    
                    if (analysis.strategy.equals("SINGLE_CLIFF")) {
                        executeSingleCliffCrossing(gui, analysis, playerPos);
                    } else if (analysis.strategy.equals("CORNER_APPROACH")) {
                        executeCornerApproachCrossing(gui, analysis, playerPos);
                    } else if (analysis.strategy.equals("BLOCKED")) {
                        log(gui, "Path is blocked! Using 2-click blocked crossing...");
                        executeBlockedCrossing(gui, analysis, playerPos);
                    } else if (analysis.strategy.equals("DIRECT_CLICK")) {
                        // Single cliff - just click through
                        log(gui, "Direct click through single cliff...");
                        executeSingleCliffCrossing(gui, analysis, playerPos);
                    } else {
                        log(gui, "Unknown strategy: " + analysis.strategy);
                    }
                    
                    // Update position after crossing
                    playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                    log(gui, "After crossing position: " + playerPos);

                    cliffCrossed = true;
                    log(gui, "Cliff crossed successfully!");
                } else {
                    // Already crossed - check if we're beyond the CURRENT cliff
                    Coord dir = dirVectors[lookDir];
                    int distanceBeyond = getDistanceBeyondCliff(playerPos, firstCliff, dir);
                    boolean onMainLine = isOnMainPathLine(playerPos, firstCliff, dir);
                    
                    log(gui, "Distance beyond CURRENT cliff (" + firstCliff + "): " + distanceBeyond + ", onMainLine=" + onMainLine);

                    if (distanceBeyond >= 2 && onMainLine) {
                        stepsBeyondCliff = 2;
                        log(gui, "SUCCESS: Reached 2 cells beyond cliff on main line!");
                    } else if (distanceBeyond >= 1 && onMainLine) {
                        stepsBeyondCliff = 1;
                        log(gui, "Reached 1 cell beyond cliff on main line, need 1 more");
                        moveForward(gui, playerPos);
                        playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                        stepsBeyondCliff = 2;
                    } else {
                        log(gui, "Not beyond cliff yet or off path, moving forward...");
                        moveForward(gui, playerPos);
                        playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                    }
                }
            } else {
                log(gui, "No cliff ahead, moving forward...");
                updateVisualizationNoCliff(playerPos, pathCells, gui);

                if (cliffCrossed) {
                    // Already crossed a cliff, count steps beyond
                    Coord dir = dirVectors[lookDir];
                    int distanceBeyond = getDistanceBeyondCliff(playerPos, firstCliff, dir);
                    boolean onMainLine = isOnMainPathLine(playerPos, firstCliff, dir);
                    
                    log(gui, "Beyond CURRENT cliff (" + firstCliff + ") distance: " + distanceBeyond + ", onMainLine=" + onMainLine);

                    if (distanceBeyond >= 2 && onMainLine) {
                        stepsBeyondCliff = 2;
                        log(gui, "SUCCESS: Already 2+ cells beyond cliff on main line!");
                    } else {
                        moveForward(gui, playerPos);
                        playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                        if (onMainLine) {
                            stepsBeyondCliff = distanceBeyond + 1;
                        }
                        log(gui, "Moving forward, stepsBeyond=" + stepsBeyondCliff);
                    }
                } else {
                    moveForward(gui, playerPos);
                    playerPos = NUtils.player().rc.div(MCache.tilesz).floor();
                }
            }
        }
        
        log(gui, "Loop finished: iterations=" + iteration + ", stepsBeyond=" + stepsBeyondCliff + ", noProgress=" + noProgressCount);
        return playerPos;
    }
    
    /**
     * Calculate how many cells beyond the cliff we are
     * @param currentPos Current player position
     * @param cliffPos Position of the first cliff cell
     * @param dir Direction of travel
     * @return Number of cells beyond the cliff (negative = before cliff)
     */
    private int getDistanceBeyondCliff(Coord currentPos, Coord cliffPos, Coord dir) {
        // Project current position onto direction vector
        Coord diff = currentPos.sub(cliffPos);
        
        // Calculate dot product with direction
        int distance = (diff.x * dir.x + diff.y * dir.y);
        
        // Positive = beyond cliff, negative = before cliff
        return distance;
    }
    
    /**
     * Check if player is on the main path line (not off to the side)
     * @param currentPos Current player position
     * @param cliffPos Position of the first cliff cell
     * @param dir Direction of travel
     * @return true if player is on the main path line
     */
    private boolean isOnMainPathLine(Coord currentPos, Coord cliffPos, Coord dir) {
        Coord diff = currentPos.sub(cliffPos);
        
        // For West/East movement (dir.x != 0), check if Y coordinates match
        if (dir.x != 0) {
            return diff.y == 0;
        }
        // For North/South movement (dir.y != 0), check if X coordinates match
        if (dir.y != 0) {
            return diff.x == 0;
        }
        
        return false;
    }
    
    /**
     * Execute crossing for single cliff cell
     * Spam click target cell until character reaches it
     */
    private void executeSingleCliffCrossing(NGameUI gui, CliffAnalysis analysis, Coord playerPos) throws InterruptedException {
        Coord targetCell = analysis.targetCell;
        if (targetCell == null) {
            throw new RuntimeException("No target cell for single cliff crossing");
        }

        Coord2d targetWorld = targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        
        log(gui, "SINGLE_CLIFF - target: " + targetCell);

        int maxClicks = 15;
        int clickCount = 0;
        Coord lastPos = NUtils.player().rc.div(MCache.tilesz).floor();
        
        while (clickCount < maxClicks) {
            // Check if we reached target
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(targetCell)) {
                log(gui, "Reached target cell " + targetCell + " after " + (clickCount + 1) + " clicks!");
                break;
            }
            
            // Click target
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, targetWorld.floor(OCache.posres), 1, 0);
            clickCount++;
            log(gui, "Click " + clickCount + " at " + targetCell + ", pos=" + currentPos);
            
            // Wait for position change
            boolean moved = waitForPositionChange(gui, 3000);
            
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No movement, clicking again...");
            }
            
            Thread.sleep(100);
        }
        
        if (clickCount >= maxClicks) {
            log(gui, "Max clicks reached for single cliff");
        }
        
        log(gui, "Single cliff crossing complete");
    }

    /**
     * Execute crossing for BLOCKED double cliff (both row 2 cells are cliffs)
     * Uses spam-click approach until character reaches target
     */
    private void executeBlockedCrossing(NGameUI gui, CliffAnalysis analysis, Coord playerPos) throws InterruptedException {
        if (analysis.cliffCells.isEmpty()) {
            throw new RuntimeException("No cliff cells for blocked crossing");
        }

        Coord firstCliff = analysis.cliffCells.get(0);
        Coord dir = dirVectors[lookDir];

        // Step 1: Spam-click to reach cell BEFORE the cliff (approach edge)
        Coord approachCell = firstCliff.sub(dir);
        Coord2d approachWorld = approachCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));

        log(gui, "BLOCKED crossing - Step 1: Approach edge at " + approachCell);
        
        int approachClicks = 0;
        Coord lastPos = NUtils.player().rc.div(MCache.tilesz).floor();
        
        while (approachClicks < 20) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(approachCell)) {
                log(gui, "Reached approach cell " + approachCell);
                break;
            }
            
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, approachWorld.floor(OCache.posres), 1, 0);
            approachClicks++;
            
            boolean moved = waitForPositionChange(gui, 2000);
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No movement, click " + (approachClicks + 1));
            }
            
            Thread.sleep(100);
        }
        
        log(gui, "At edge, position: " + NUtils.player().rc.div(MCache.tilesz).floor());
        Thread.sleep(300);

        // Step 2: Spam click target cell until character reaches it
        Coord targetCell = analysis.targetCell;
        if (targetCell == null) {
            targetCell = firstCliff.add(dir.mul(2));
        }
        Coord2d targetWorld = targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));

        log(gui, "BLOCKED crossing - Step 2: Spam clicking target " + targetCell);

        int maxClicks = 20;
        int clickCount = 0;
        lastPos = NUtils.player().rc.div(MCache.tilesz).floor();

        while (clickCount < maxClicks) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(targetCell)) {
                log(gui, "Reached target cell " + targetCell + " after " + (clickCount + 1) + " clicks!");
                break;
            }

            NUtils.getGameUI().map.wdgmsg("click", Coord.z, targetWorld.floor(OCache.posres), 1, 0);
            clickCount++;
            log(gui, "Click " + clickCount + " at " + targetCell + ", pos=" + currentPos);

            boolean moved = waitForPositionChange(gui, 3000);

            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }

            if (!moved) {
                log(gui, "No movement detected, clicking again...");
            }

            Thread.sleep(100);
        }

        if (clickCount >= maxClicks) {
            log(gui, "Max clicks reached, final position: " + NUtils.player().rc.div(MCache.tilesz).floor());
        }

        log(gui, "Blocked crossing complete");
    }
    
    /**
     * Wait for player position to change
     * @param gui Game UI
     * @param timeout Max time to wait in ms
     * @return true if position changed, false if timeout
     */
    private boolean waitForPositionChange(NGameUI gui, long timeout) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) return false;
        
        Coord2d startPos = player.rc;
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(100);
            if (!player.rc.equals(startPos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Execute crossing for double cliff cells using CORNER_APPROACH strategy
     * 1. Move along main line to FIRST cliff cell (it's still on upper level!)
     * 2. Move to corner of that cell (shift towards free cell)
     * 3. Spam click free cell until character reaches it
     */
    private void executeCornerApproachCrossing(NGameUI gui, CliffAnalysis analysis, Coord playerPos) throws InterruptedException {
        if (analysis.neighborCells.isEmpty()) {
            throw new RuntimeException("No free neighbor cells for corner approach");
        }

        Coord freeCell = analysis.neighborCells.get(0);
        Coord firstCliff = analysis.cliffCells.get(0);
        Coord dir = dirVectors[lookDir];
        
        log(gui, "CORNER_APPROACH - free cell: " + freeCell + ", first cliff: " + firstCliff);

        // Step 1: Click on EDGE of first cliff cell (not center!) to hug the edge
        double edgeX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x / 2;
        double edgeY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y / 2;
        
        // Adjust edge position based on direction - click on the edge closest to player
        if (dir.x < 0) { // West - click EAST edge
            edgeX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x * 0.75;
        } else if (dir.x > 0) { // East - click WEST edge
            edgeX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x * 0.25;
        }
        if (dir.y < 0) { // South - click NORTH edge
            edgeY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y * 0.75;
        } else if (dir.y > 0) { // North - click SOUTH edge
            edgeY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y * 0.25;
        }
        
        Coord2d edgeWorld = new Coord2d(edgeX, edgeY);
        log(gui, "Step 1: Clicking EDGE of first cliff " + firstCliff + " at " + edgeWorld);
        
        int edgeClicks = 0;
        Coord lastPos = NUtils.player().rc.div(MCache.tilesz).floor();
        while (edgeClicks < 15) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (currentPos.dist(firstCliff) <= 1) {
                log(gui, "Close to cliff (" + currentPos + "), proceeding to step 2");
                break;
            }
            
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, edgeWorld.floor(OCache.posres), 1, 0);
            edgeClicks++;
            log(gui, "Edge click " + edgeClicks + ", pos=" + currentPos);
            
            boolean moved = waitForPositionChange(gui, 2000);
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No movement, click " + (edgeClicks + 1));
            }
            
            Thread.sleep(100);
        }

        // Step 2: Shift to corner - click on the corner between cliff cell and free cell
        log(gui, "Step 2: Shifting to corner (from " + firstCliff + " towards " + freeCell + ")");
        
        // Calculate corner world position - corner of cliff cell closest to free cell
        double cornerX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x / 2;
        double cornerY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y / 2;
        
        // Shift corner towards free cell
        if (freeCell.y < firstCliff.y) {
            cornerY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y * 0.25;
        } else if (freeCell.y > firstCliff.y) {
            cornerY = firstCliff.y * MCache.tilesz.y + MCache.tilesz.y * 0.75;
        }
        
        if (freeCell.x < firstCliff.x) {
            cornerX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x * 0.25;
        } else if (freeCell.x > firstCliff.x) {
            cornerX = firstCliff.x * MCache.tilesz.x + MCache.tilesz.x * 0.75;
        }
        
        Coord2d cornerWorld = new Coord2d(cornerX, cornerY);
        log(gui, "Corner world position: " + cornerWorld);

        int cornerClicks = 0;
        while (cornerClicks < 10) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();

            // Check if we're close enough to free cell (adjacent)
            if (currentPos.dist(freeCell) <= 1) {
                log(gui, "Close enough to free cell (" + currentPos + " dist " + currentPos.dist(freeCell) + "), proceeding to step 3");
                break;
            }

            NUtils.getGameUI().map.wdgmsg("click", Coord.z, cornerWorld.floor(OCache.posres), 1, 0);
            cornerClicks++;

            boolean moved = waitForPositionChange(gui, 2000);
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();

            if (!newPos.equals(lastPos)) {
                log(gui, "Shifted from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No shift, click " + (cornerClicks + 1));
            }
            
            Thread.sleep(100);
        }
        
        // Step 3: Spam click free cell until character reaches it
        Coord2d freeWorld = freeCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        log(gui, "Step 3: Spam clicking free cell " + freeCell + " to jump down");
        
        int maxClicks = 15;
        int clickCount = 0;
        lastPos = NUtils.player().rc.div(MCache.tilesz).floor();
        
        while (clickCount < maxClicks) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(freeCell)) {
                log(gui, "Reached free cell " + freeCell + " after " + (clickCount + 1) + " clicks - JUMPED DOWN!");
                break;
            }
            
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, freeWorld.floor(OCache.posres), 1, 0);
            clickCount++;
            log(gui, "Jump click " + clickCount + ", pos=" + currentPos);
            
            boolean moved = waitForPositionChange(gui, 3000);
            
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No movement yet, clicking again...");
            }
            
            Thread.sleep(100);
        }
        
        if (clickCount >= maxClicks) {
            log(gui, "Max clicks reached for corner approach");
        }
        
        Coord finalPos = NUtils.player().rc.div(MCache.tilesz).floor();
        log(gui, "Corner approach complete, final position: " + finalPos);

        // After jumping down, move FORWARD (not back to main line which is the cliff!)
        // From freeCell (-904, -921), move forward to (-905, -921) along lower level
        Coord nextCell = freeCell.add(dir); // Move forward from free cell
        
        log(gui, "Moving forward from free cell: " + finalPos + " -> " + nextCell);

        Coord2d nextWorld = nextCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        int moveClicks = 0;

        while (moveClicks < 10) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(nextCell)) {
                log(gui, "Reached next cell " + nextCell);
                break;
            }

            NUtils.getGameUI().map.wdgmsg("click", Coord.z, nextWorld.floor(OCache.posres), 1, 0);
            moveClicks++;
            log(gui, "Move click " + moveClicks + " at " + nextCell + ", pos=" + currentPos);

            waitForPositionChange(gui, 2000);
            Thread.sleep(100);
        }
    }

    /**
     * Execute retry crossing if position verification fails
     */
    private void executeRetryCrossing(NGameUI gui, CliffAnalysis analysis) throws InterruptedException {
        log(gui, "Retrying crossing...");

        if (analysis.targetCell != null) {
            Coord2d targetWorld = analysis.targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
            
            int maxClicks = 10;
            int clickCount = 0;
            
            while (clickCount < maxClicks) {
                Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
                if (currentPos.equals(analysis.targetCell)) {
                    log(gui, "Reached target after " + (clickCount + 1) + " clicks!");
                    break;
                }
                
                NUtils.getGameUI().map.wdgmsg("click", Coord.z, targetWorld.floor(OCache.posres), 1, 0);
                clickCount++;
                log(gui, "Retry click " + clickCount + " at " + analysis.targetCell);
                
                waitForPositionChange(gui, 3000);
                Thread.sleep(100);
            }
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
     * Simple forward movement with spam-click
     */
    private void moveForward(NGameUI gui, Coord playerPos) throws InterruptedException {
        Coord dir = dirVectors[lookDir];
        Coord targetCell = playerPos.add(dir);
        Coord2d targetWorld = targetCell.mul(MCache.tilesz).add(MCache.tilesz.div(2));
        
        log(gui, "Moving to: " + targetCell);
        
        int maxClicks = 10;
        int clickCount = 0;
        Coord lastPos = playerPos;
        
        while (clickCount < maxClicks) {
            Coord currentPos = NUtils.player().rc.div(MCache.tilesz).floor();
            if (currentPos.equals(targetCell)) {
                log(gui, "Reached target " + targetCell);
                break;
            }
            
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, targetWorld.floor(OCache.posres), 1, 0);
            clickCount++;
            
            boolean moved = waitForPositionChange(gui, 2000);
            Coord newPos = NUtils.player().rc.div(MCache.tilesz).floor();
            
            if (!newPos.equals(lastPos)) {
                log(gui, "Moved from " + lastPos + " to " + newPos);
                lastPos = newPos;
            }
            
            if (!moved) {
                log(gui, "No movement, click " + (clickCount + 1));
            }
            
            Thread.sleep(100);
        }
        
        if (clickCount >= maxClicks) {
            log(gui, "Max clicks reached for moveForward");
        }
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
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
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
            log(gui, "Checking row 2 cells for double cliff: " + row2[0] + ", " + row2[1]);
            
            Coord freeCell = findFreeCell(row2, gui);

            if (freeCell != null) {
                log(gui, "Found free cell in row 2: " + freeCell);
                analysis.neighborCells.add(freeCell);
                analysis.targetCell = freeCell;
                analysis.strategy = "CORNER_APPROACH";
            } else {
                log(gui, "No free cell found in row 2 - both are cliffs!");
                // Both row 2 cells are cliffs - need different strategy
                // Try to find any passable neighbor
                analysis.strategy = "BLOCKED";
                analysis.targetCell = firstCliff.add(dir.mul(2)); // Fallback target
            }
        } else {
            analysis.cliffType = "SINGLE_CLIFF";

            // Target is cell after cliff
            Coord target = firstCliff.add(dir.mul(2));
            analysis.targetCell = target;
            analysis.strategy = "DIRECT_CLICK";
        }

        log(gui, "Cliff analysis complete: type=" + analysis.cliffType + ", strategy=" + analysis.strategy + ", target=" + analysis.targetCell);
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
        log(gui, "Checking cells for free path: " + cells[0] + ", " + cells[1]);
        for (Coord cell : cells) {
            boolean isCliff = isCliffCell(cell, gui);
            log(gui, "  Cell " + cell + " is " + (isCliff ? "CLIFF" : "FREE"));
            if (!isCliff) {
                return cell;
            }
        }
        log(gui, "  No free cells found!");
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
