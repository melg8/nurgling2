package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.GoTo;
import nurgling.actions.Results;
import nurgling.conf.NPrepBlocksProp;
import nurgling.conf.NWorldExplorerProp;
import nurgling.overlays.NWorldExplorerDebugOverlay;
import nurgling.tasks.WaitCheckable;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.NMiniMap;
import nurgling.widgets.DebugLogWindow;

import static haven.Coord.of;

public class WorldExplorer implements Action {
    // Directions: 0=East, 1=North, 2=West, 3=South
    public static Coord[] dirVectors = {of(1, 0), of(0, -1), of(-1, 0), of(0, 1)};

    // For original water exploration mode
    public static Coord[] counterclockwise = {of(1, 0), of(0, 1), of(-1, 0), of(0, -1)};
    public static Coord[][] counternearest = {{of(0, 1),of(1, 1),of(2, 1)}, {of(-1, 0),of(-1, 1),of(-1, 2)}, {of(0, -1),of(-1, -1),of(-2, -1)}, {of(1, 0),of(1, -1),of(1, -2)}};
    public static Coord[] clockwise = {of(1, 0), of(0, -1), of(-1, 0), of(0, 1)};
    public static Coord[][] nearest = {{of(0, -1),of(1, -1),of(2, -1)}, {of(-1, 0),of(-1, -1),of(-1, -2)}, {of(0, 1),of(-1, 1),of(-2, 1)}, {of(1, 0),of(1, 1),of(1, 2)}};
    
    // Thread-local debug log reference
    private static final ThreadLocal<DebugLogWindow> debugLogRef = new ThreadLocal<>();
    
    // Set debug log for current thread
    public static void setDebugLog(DebugLogWindow log) {
        debugLogRef.set(log);
    }
    
    // Get debug log for current thread
    public static DebugLogWindow getDebugLog() {
        return debugLogRef.get();
    }
    
    // Log a message
    public static void log(String message, DebugLogWindow.LogLevel level) {
        DebugLogWindow log = getDebugLog();
        if (log != null) {
            log.addMessage(message, level);
        }
    }

    // Turn left (counter-clockwise): 0->1->2->3->0
    private int turnLeft(int dir) {
        return (dir + 1) % 4;
    }
    
    // Turn right (clockwise): 0->3->2->1->0
    private int turnRight(int dir) {
        return (dir + 3) % 4;
    }
    
    // Get opposite direction
    private int turnBack(int dir) {
        return (dir + 2) % 4;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {

        nurgling.widgets.bots.WorldExplorerWnd w = null;
        NWorldExplorerProp prop = null;
        DebugLogWindow debugLog = null;
        try {
            NUtils.getUI().core.addTask(new WaitCheckable( NUtils.getGameUI().add((w = new nurgling.widgets.bots.WorldExplorerWnd()), UI.scale(200,200))));
            prop = w.prop;
            
            // Create debug log window and set thread-local reference
            debugLog = new DebugLogWindow("WorldExplorer Debug Log");
            // Position window next to the bot window (which is at 200, 200)
            Coord debugPos = UI.scale(650, 200);
            NUtils.getGameUI().add(debugLog, debugPos);
            setDebugLog(debugLog);
            debugLog.addMessage("WorldExplorer started", DebugLogWindow.LogLevel.INFO);
            
            // Also show in game message log
            NUtils.getGameUI().ui.gui.msg("Debug Log opened at " + debugPos);
        }
        catch (InterruptedException e)
        {
            throw e;
        }
        finally {
            if(w!=null)
                w.destroy();
            if(debugLog != null)
                debugLog.destroy();
            setDebugLog(null);
        }
        if(prop == null)
        {
            return Results.ERROR("No config");
        }

        // Shoreline mode - follow boundary between land and water on land
        if(prop.shoreline)
        {
            return runShoreline(gui, prop.clockwise);
        }

        Coord[] dirs = (prop.clockwise)?clockwise:counterclockwise;
        Coord[][] neardirs = (prop.clockwise)?nearest:counternearest;
        String targetTile = "odeep";
        String nearestTile = (prop.deeper)?"odeeper":"owater";

        if(!prop.deeper)
        {
            dirs = (prop.clockwise)?counterclockwise:clockwise;
            neardirs = (prop.clockwise)?counternearest:nearest;
        }

        boolean deepFound = false;

        Coord[] buffer = new Coord[100];
        int counter = 0;
        Coord  pltc = NUtils.player().rc.div(MCache.tilesz).floor();
        boolean isStart = false;
        
        // Shoreline mode - find starting position on land next to water
        if(prop.shoreline)
        {
            for(int j = 0; j<50;j++) {
                for (int i = 0; i < 4; i++) {
                    Coord cand = pltc.add(dirs[i].mul(j));
                    Resource res_beg = NUtils.getGameUI().ui.sess.glob.map.tilesetr(NUtils.getGameUI().ui.sess.glob.map.gettile(cand));
                    if (res_beg != null) {
                        // Check if this is land tile (not water)
                        boolean isLand = !isWaterTile(res_beg.name);
                        if (isLand && hasWaterNeighbor(cand, NUtils.getGameUI())) {
                            new GoTo(cand.mul(MCache.tilesz).add(MCache.tilehsz)).run(gui);
                            isStart = true;
                            break;
                        }
                    }
                }
                if(isStart)
                    break;
            }
        }
        else
        {
            // Original water exploration mode
            for(int j = 0; j<50;j++) {
                for (int i = 0; i < 4; i++) {
                    Coord cand = pltc.add(dirs[i].mul(j));
                    Resource res_beg = NUtils.getGameUI().ui.sess.glob.map.tilesetr(NUtils.getGameUI().ui.sess.glob.map.gettile(cand));
                    if (res_beg != null) {
                        if (res_beg.name.endsWith(targetTile)) {
                            boolean isCorrect = false;
                            for (Coord test : neardirs[i]) {
                                Resource testr = NUtils.getGameUI().ui.sess.glob.map.tilesetr(NUtils.getGameUI().ui.sess.glob.map.gettile(cand.add(test)));
                                if (testr != null && testr.name.endsWith(nearestTile)) {
                                    deepFound = true;
                                    isCorrect = true;
                                }
                            }
                            if (isCorrect) {
                                new GoTo(cand.mul(MCache.tilesz).add(MCache.tilehsz)).run(gui);
                                isStart = true;
                            }
                            if (isStart)
                                break;
                        }
                    }
                }
                if(isStart)
                    break;
            }
        }

        Coord last = null;
        while (true) {
            pltc = NUtils.player().rc.div(MCache.tilesz).floor();
            boolean isFound = false;

            // Original water exploration mode
            for (int i = 0; i < 4; i++) {
                Coord cand = pltc.add(dirs[i]);
                boolean skip = false;
                for (Coord check : buffer) {
                    if (check != null && cand.equals(check.x, check.y)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                Resource res_beg = NUtils.getGameUI().ui.sess.glob.map.tilesetr(NUtils.getGameUI().ui.sess.glob.map.gettile(cand));
                if (res_beg != null) {
                    if (res_beg.name.endsWith(targetTile)) {
                        if (last == null || !cand.equals(last.x, last.y)) {
                            boolean isCorrect = false;
                            for(Coord test : neardirs[i])
                            {
                                Resource testr = NUtils.getGameUI().ui.sess.glob.map.tilesetr(NUtils.getGameUI().ui.sess.glob.map.gettile(pltc.add(test)));
                                if(testr != null && testr.name.endsWith(nearestTile))
                                {
                                    deepFound = true;
                                    isCorrect = true;
                                }
                            }
                            if(!deepFound||isCorrect) {
                                new GoTo(cand.mul(MCache.tilesz).add(MCache.tilehsz)).run(gui);
                                buffer[counter++ % 100] = last;
                                last = pltc;
                                isFound = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!isFound && last != null) {
                new GoTo(last.mul(MCache.tilesz).add(MCache.tilehsz)).run(gui);
                buffer[counter++ % 100] = pltc;
            }
        }
    }

    // Shoreline following using wall-following algorithm
    // Water stays on one side (left if clockwise, right if counter-clockwise)
    private Results runShoreline(NGameUI gui, boolean clockwise) throws InterruptedException {
        GameUI gameui = NUtils.getGameUI();

        // Create debug overlay for visualization
        NWorldExplorerDebugOverlay debugOverlay = new NWorldExplorerDebugOverlay(gameui.ui.sess.glob.map);

        try {
        
        log("Shoreline exploration started", DebugLogWindow.LogLevel.INFO);

        // Find starting position on land next to water
        Coord pltc = NUtils.player().rc.div(MCache.tilesz).floor();
        Coord startTc = null;

        for(int j = 0; j < 50 && startTc == null; j++) {
            for (int i = 0; i < 4 && startTc == null; i++) {
                Coord cand = pltc.add(dirVectors[i].mul(j));
                Resource res = gameui.ui.sess.glob.map.tilesetr(gameui.ui.sess.glob.map.gettile(cand));
                if (res != null && !isWaterTile(res.name) && hasWaterNeighbor(cand, gameui)) {
                    startTc = cand;
                }
            }
        }

        if(startTc == null) {
            log("No shoreline found nearby", DebugLogWindow.LogLevel.ERROR);
            return Results.ERROR("No shoreline found nearby");
        }

        log("Starting shoreline follow at " + startTc, DebugLogWindow.LogLevel.SUCCESS);
        
        // Move to starting position
        new GoTo(startTc.mul(MCache.tilesz).add(MCache.tilehsz)).run(gui);
        
        // Determine initial direction - find direction where water is on the correct side
        int curDir = 0; // Start facing East
        boolean waterOnSide = false;
        
        // Try to find initial direction where water is on the preferred side
        for(int d = 0; d < 4; d++) {
            int waterDir = clockwise ? turnRight(d) : turnLeft(d);
            Coord waterCheck = startTc.add(dirVectors[waterDir]);
            Resource res = gameui.ui.sess.glob.map.tilesetr(gameui.ui.sess.glob.map.gettile(waterCheck));
            if(res != null && isWaterTile(res.name)) {
                curDir = d;
                waterOnSide = true;
                break;
            }
        }
        
        if(!waterOnSide) {
            // Just pick a direction along the shore
            for(int d = 0; d < 4; d++) {
                Coord cand = startTc.add(dirVectors[d]);
                Resource res = gameui.ui.sess.glob.map.tilesetr(gameui.ui.sess.glob.map.gettile(cand));
                if(res != null && !isWaterTile(res.name) && hasWaterNeighbor(cand, gameui)) {
                    curDir = d;
                    break;
                }
            }
        }

        // Buffer to prevent cycling
        Coord[] buffer = new Coord[200];
        int bufIdx = 0;
        int stuckCount = 0;
        Coord lastPos = null;

        // Track cliff jump detection (successful jump down or up)
        Coord jumpFromPos = null;        // Position we jumped from
        Coord jumpToPos = null;          // Position we jumped to
        Coord blockedTarget = null;      // Target that was blocked before the jump
        int jumpTimeout = 0;

        while(true) {
            pltc = NUtils.player().rc.div(MCache.tilesz).floor();

            // Check if we're stuck (same position as before)
            if(lastPos != null && pltc.equals(lastPos)) {
                stuckCount++;
                if(stuckCount > 3) {
                    // Force a turn to break out
                    curDir = turnLeft(curDir);
                    stuckCount = 0;
                }
            } else {
                stuckCount = 0;
            }
            
            // Mark current position as visited
            debugOverlay.markVisited(pltc);
            
            // Process pending overlay operations
            debugOverlay.processPendingOperations();
            
            // Detect successful jump: if we're at a new position that was previously blocked
            // This means we successfully jumped down or up
            if (jumpFromPos != null && !pltc.equals(jumpFromPos) && !pltc.equals(lastPos)) {
                // We moved to a new position - check if this was a jump
                if (blockedTarget != null && pltc.equals(blockedTarget)) {
                    // We reached the target that was previously blocked - this was a jump!
                    // Now mark the jump-from position as temporarily blocked to prevent jumping back
                    gameui.ui.gui.msg("Cliff jump detected! From " + jumpFromPos + " to " + pltc);
                    // Mark jumpFromPos as blocked temporarily - don't try to go back there
                    blockedTarget = jumpFromPos;  // Now prevent going back to jumpFromPos
                    jumpFromPos = pltc;           // Update jump position
                    jumpToPos = null;
                    jumpTimeout = 20;  // Block for ~20 iterations
                } else {
                    // Normal movement, clear jump state
                    jumpFromPos = null;
                    blockedTarget = null;
                    jumpTimeout = 0;
                }
            }
            
            // Clear jump block after timeout
            if (jumpTimeout > 0) {
                jumpTimeout--;
                if (jumpTimeout == 0) {
                    jumpFromPos = null;
                    blockedTarget = null;
                }
            }
            
            lastPos = pltc;
            
            // Wall-following algorithm:
            // 1. Try to turn towards water (left if clockwise, right if counter-clockwise)
            // 2. If blocked, try to go straight
            // 3. If blocked, try to turn away from water
            // 4. If blocked, turn back
            // Obstacles (trees, rocks, cliffs, etc.) are treated as part of the "wall" like water

            boolean moved = false;
            int[] tryOrder;

            if(clockwise) {
                // Water on right: try right, straight, left, back
                tryOrder = new int[] {
                    turnRight(curDir),  // Right (towards water)
                    curDir,              // Straight
                    turnLeft(curDir),   // Left (away from water)
                    turnBack(curDir)    // Back
                };
            } else {
                // Water on left: try left, straight, right, back
                tryOrder = new int[] {
                    turnLeft(curDir),   // Left (towards water)
                    curDir,              // Straight
                    turnRight(curDir),  // Right (away from water)
                    turnBack(curDir)    // Back
                };
            }

            for(int i = 0; i < 4 && !moved; i++) {
                int tryDir = tryOrder[i];
                Coord cand = pltc.add(dirVectors[tryDir]);

                // Check if recently visited
                boolean visited = false;
                for(int b = 0; b < 100 && b < bufIdx; b++) {
                    if(buffer[b] != null && buffer[b].equals(cand)) {
                        visited = true;
                        break;
                    }
                }
                if(visited) continue;
                
                // Check if this target is temporarily blocked due to cliff jump detection
                // This prevents jumping back up/down immediately after jumping down/up
                if (blockedTarget != null && cand.equals(blockedTarget)) {
                    debugOverlay.markWall(cand);
                    continue;  // Skip this target - it's temporarily blocked
                }

                Resource res = gameui.ui.sess.glob.map.tilesetr(gameui.ui.sess.glob.map.gettile(cand));
                if(res != null && !isWaterTile(res.name)) {
                    // Check if tile is blocked by obstacles (trees, rocks, cliffs, etc.)
                    // If blocked, treat it like water - part of the wall to follow
                    if(!isTileBlocked(cand, gameui)) {
                        // Valid land tile without obstacles, move there
                        // Enable cliff avoidance for smarter movement along cliff edges
                        buffer[bufIdx++ % 200] = pltc;
                        Coord2d targetCoord = cand.mul(MCache.tilesz).add(MCache.tilehsz);
                        
                        // Mark target for debugging
                        debugOverlay.markTarget(cand);

                        // Try to move with cliff avoidance
                        GoTo goTo = new GoTo(targetCoord, true);
                        goTo.run(gui);

                        // Check if movement was successful - player must be in the target tile
                        Coord newPlayerTile = NUtils.player().rc.div(MCache.tilesz).floor();
                        
                        if (newPlayerTile.equals(cand)) {
                            // Successfully reached target tile
                            curDir = tryDir;
                            moved = true;
                            debugOverlay.markVisited(cand);
                            log("Moved to " + cand, DebugLogWindow.LogLevel.INFO);

                            // Track if this was a blocked target that we successfully reached (jump detected)
                            if (blockedTarget != null && cand.equals(blockedTarget)) {
                                // We reached a previously blocked target - this was a jump!
                                jumpFromPos = pltc;
                                jumpToPos = cand;
                                log("Jump detected: " + pltc + " -> " + cand, DebugLogWindow.LogLevel.SUCCESS);
                            }
                        } else {
                            // Movement failed - player didn't reach target tile
                            // This could be due to cliff edge blocking - try alternative strategies
                            log("Movement failed to " + cand + ", trying alternative", DebugLogWindow.LogLevel.WARNING);
                            
                            // Track failed target for jump detection
                            if (blockedTarget == null) {
                                blockedTarget = cand;
                                jumpFromPos = pltc;
                            }

                            if (GoTo.tryAlternativeMove(gui, targetCoord, pltc)) {
                                // Alternative move succeeded
                                newPlayerTile = NUtils.player().rc.div(MCache.tilesz).floor();
                                if (newPlayerTile.equals(cand)) {
                                    curDir = tryDir;
                                    moved = true;
                                    debugOverlay.markVisited(cand);

                                    // Track if this was a blocked target that we successfully reached
                                    if (cand.equals(blockedTarget)) {
                                        // Jump detected!
                                        jumpFromPos = pltc;
                                        jumpToPos = cand;
                                    }
                                } else {
                                    // Alternative move didn't reach target - mark as wall
                                    debugOverlay.markWall(cand);
                                }
                            } else {
                                // Alternative move failed - mark as wall
                                debugOverlay.markWall(cand);
                            }
                        }
                    } else {
                        // Tile is blocked by obstacle - mark as wall
                        debugOverlay.markWall(cand);
                    }
                    // If blocked by obstacle, skip and try next direction (treat as wall)
                } else {
                    // Water tile - mark as wall
                    debugOverlay.markWall(cand);
                }
            }
            
            // Process pending overlay operations after each movement attempt
            debugOverlay.processPendingOperations();

            if(!moved) {
                // Completely stuck, try any adjacent land tile that's not blocked
                for(int d = 0; d < 4; d++) {
                    Coord cand = pltc.add(dirVectors[d]);
                    Resource res = gameui.ui.sess.glob.map.tilesetr(gameui.ui.sess.glob.map.gettile(cand));
                    if(res != null && !isWaterTile(res.name) && !isTileBlocked(cand, gameui)) {
                        buffer[bufIdx++ % 200] = pltc;
                        Coord2d targetCoord = cand.mul(MCache.tilesz).add(MCache.tilehsz);
                        
                        // Mark target for debugging
                        debugOverlay.markTarget(cand);

                        // Try alternative movement for stuck situations
                        if (GoTo.tryAlternativeMove(gui, targetCoord, pltc)) {
                            Coord newPlayerTile = NUtils.player().rc.div(MCache.tilesz).floor();
                            if (newPlayerTile.equals(cand)) {
                                curDir = d;
                                moved = true;
                                debugOverlay.markVisited(cand);
                                break;
                            } else {
                                // Didn't reach target - mark as wall
                                debugOverlay.markWall(cand);
                            }
                        } else {
                            // Alternative move failed - mark as wall
                            debugOverlay.markWall(cand);
                        }
                    }
                }
            }
            
            // Process pending overlay operations after fallback movement
            debugOverlay.processPendingOperations();
        }
        } finally {
            // Clear debug overlays when bot stops
            if (debugOverlay != null) {
                debugOverlay.clearAll();
            }
        }
    }

    // Helper method to check if a tile is water
    private boolean isWaterTile(String tileName) {
        return tileName.contains("water") || tileName.contains("deep") || tileName.contains("odeep") || tileName.contains("odeeper");
    }

    // Helper method to check if a tile has water neighbor (any of 4 sides)
    private boolean hasWaterNeighbor(Coord tc, GameUI gui) {
        Coord[] neighbors = {of(1, 0), of(-1, 0), of(0, 1), of(0, -1)};
        for(Coord n : neighbors) {
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tc.add(n)));
            if(res != null && isWaterTile(res.name))
                return true;
        }
        return false;
    }

    // Check if a tile is blocked by obstacles (trees, rocks, cliffs, etc.)
    // Returns true if the tile cannot be walked on due to obstacles
    private boolean isTileBlocked(Coord tc, GameUI gui) {
        // Check for cliffs and other blocking terrain
        Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tc));
        if (res != null) {
            String name = res.name;
            // Cliff tiles block movement
            if (name.contains("cliff") || name.contains("rock") || name.contains("impass")) {
                return true;
            }
        }

        // Check for blocking objects (trees, boulders, claim poles, etc.)
        try {
            synchronized (gui.ui.sess.glob.oc) {
                for (haven.Gob gob : gui.ui.sess.glob.oc) {
                    if (!(gob instanceof haven.OCache.Virtual || gob.attr.isEmpty())) {
                        Coord gobTile = gob.rc.div(MCache.tilesz).floor();
                        if (gobTile.equals(tc) && gob.id != NUtils.playerID()) {
                            // Check if this gob has blocking name
                            if (gob.ngob != null && gob.ngob.name != null) {
                                String gname = gob.ngob.name.toLowerCase();
                                // Trees, rocks, boulders, stumps, logs, bushes, shrubs
                                if (gname.contains("tree") || gname.contains("rock") ||
                                    gname.contains("boulder") || gname.contains("stump") ||
                                    gname.contains("log") || gname.contains("bush") ||
                                    gname.contains("shrub") || gname.contains("cliff")) {
                                    return true;
                                }
                                // Claim poles - blocks movement (both personal and village claims)
                                if (gname.contains("claim") || gname.contains("pclaim")) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If we can't check objects, just rely on tile data
        }

        // Check if tile is inside a claim overlay (personal or village claim)
        // Claims are rendered as colored overlays on the map and block movement
        if (isTileInClaim(tc, gui)) {
            return true;
        }
        
        // Additional check: if any claim pole gob is within 5 tiles, consider it blocked
        // This handles cases where claim data is not yet loaded
        try {
            synchronized (gui.ui.sess.glob.oc) {
                for (haven.Gob gob : gui.ui.sess.glob.oc) {
                    if (!(gob instanceof haven.OCache.Virtual || gob.attr.isEmpty())) {
                        if (gob.id != NUtils.playerID() && gob.ngob != null && gob.ngob.name != null) {
                            String gname = gob.ngob.name.toLowerCase();
                            if (gname.contains("claim") || gname.contains("pclaim")) {
                                Coord gobTile = gob.rc.div(MCache.tilesz).floor();
                                // Check if claim pole is within 5 tiles (claim radius)
                                if (gobTile.dist(tc) <= 5) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    // Check if a tile is inside a claim overlay using MCache data
    // Claims are stored as overlays with tags "cplot" (personal) or "vlg" (village)
    private boolean isTileInClaim(Coord tc, GameUI gui) {
        try {
            // Get all overlay infos for the area around this tile
            haven.Area area = new haven.Area(tc, tc.add(1, 1));
            java.util.Collection<haven.MCache.OverlayInfo> overlayInfos = 
                gui.ui.sess.glob.map.getols(area);
            
            // Check each overlay to see if it's a claim
            for (haven.MCache.OverlayInfo olInfo : overlayInfos) {
                if (olInfo instanceof haven.MCache.ResOverlay) {
                    haven.MCache.ResOverlay resOl = (haven.MCache.ResOverlay) olInfo;
                    
                    // Check if this overlay has claim tags
                    boolean isClaim = false;
                    for (String tag : resOl.tags()) {
                        if (tag.equals("cplot") || tag.equals("vlg")) {
                            isClaim = true;
                            break;
                        }
                    }
                    
                    if (isClaim) {
                        // Check if this specific tile is inside this claim overlay
                        boolean[] buf = new boolean[1];
                        gui.ui.sess.glob.map.getol(olInfo, area, buf);
                        if (buf[0]) {
                            return true; // Tile is inside this claim
                        }
                    }
                }
            }
        } catch (haven.Loading e) {
            // Grid data not loaded yet
        } catch (Exception e) {
            // If we can't access map data, just skip claim check
        }
        
        return false; // Not in any claim
    }
}
