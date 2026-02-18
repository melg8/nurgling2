package nurgling.actions;

import haven.*;
import static haven.OCache.posres;
import nurgling.*;
import static nurgling.actions.PathFinder.pfmdelta;
import nurgling.tasks.*;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

public class GoTo implements Action
{
    final Coord2d targetCoord;
    final boolean avoidCliff;

    public GoTo(Coord2d targetCoord)
    {
        this(targetCoord, false);
    }
    
    public GoTo(Coord2d targetCoord, boolean avoidCliff)
    {
        this.targetCoord = targetCoord;
        this.avoidCliff = avoidCliff;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {

        if(!NParser.checkName(NUtils.getCursorName(), "arw")) {
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres),3, 0);
            NUtils.getUI().core.addTask(new GetCurs("arw"));
        }

        // If avoiding cliffs and target is adjacent, check for cliff blocking
        Coord playerCoord = NUtils.player().rc.floor(posres);
        Coord targetTile = targetCoord.floor(MCache.tilesz);
        Coord playerTile = playerCoord;
        
        if (avoidCliff && playerTile.dist(targetTile) <= 2) {
            // Adjacent tile - check if cliff is blocking direct path
            Coord2d adjustedTarget = adjustForCliff(gui, targetCoord, playerTile, targetTile);
            if (adjustedTarget != null) {
                // Click at adjusted position to move along cliff edge
                gui.map.wdgmsg("click", Coord.z, adjustedTarget.floor(posres), 1, 0);
            } else {
                // Direct path is clear
                gui.map.wdgmsg("click", Coord.z, targetCoord.floor(posres), 1, 0);
            }
        } else {
            gui.map.wdgmsg("click", Coord.z, targetCoord.floor(posres), 1, 0);
        }
        
        Following fl = NUtils.player().getattr(Following.class);
        if( fl!= null )
        {
            Gob gob = null;
            if((gob = Finder.findGob(fl.tgt))!=null) {
                if (NParser.isIt(gob, new NAlias("horse"))) {
                    NUtils.getUI().core.addTask(new IsPoseMov(targetCoord, gob, new NAlias("gfx/kritter/horse/pace", "gfx/kritter/horse/walking", "gfx/kritter/horse/trot", "gfx/kritter/horse/gallop")));
                    NUtils.getUI().core.addTask(new IsNotPose(gob, new NAlias("gfx/kritter/horse/pace", "gfx/kritter/horse/walking", "gfx/kritter/horse/trot", "gfx/kritter/horse/gallop")));
                }
                else if (NParser.isIt(gob, new NAlias("dugout"))) {
                    NUtils.getUI().core.addTask(new IsPoseMov(targetCoord, NUtils.player(), new NAlias("gfx/borka/dugoutrowan")));
                    NUtils.getUI().core.addTask(new IsNotPose(NUtils.player(), new NAlias("gfx/borka/dugoutrowan")));
                }
                else if (NParser.isIt(gob, new NAlias("rowboat"))) {
                    NUtils.getUI().core.addTask(new IsPoseMov(targetCoord, NUtils.player(), new NAlias("gfx/borka/rowing")));
                    NUtils.getUI().core.addTask(new IsNotPose(NUtils.player(), new NAlias("gfx/borka/rowing")));
                }
                else if (NParser.isIt(gob, new NAlias("snekkja"))) {
                    NUtils.getUI().core.addTask(new IsMovingBySpeed(targetCoord, gob));
                    NUtils.getUI().core.addTask(new MovingCompletedBySpeed(gob));
                }
            }
        }
        else {
            NUtils.getUI().core.addTask(new IsMoving(targetCoord));
            NUtils.getUI().core.addTask(new MovingCompleted(targetCoord));
        }
        if(NUtils.getGameUI().map.player().rc.dist(targetCoord) > 2*pfmdelta)
            return Results.FAIL();
        return Results.SUCCESS();
    }
    
    /**
     * Adjust target coordinate to handle cliff edge movement.
     * When moving diagonally near a cliff, the game may not accept the click.
     * Solution: Click further in the same direction or to the side.
     * 
     * @return adjusted target Coord2d, or null if direct path is clear
     */
    private Coord2d adjustForCliff(NGameUI gui, Coord2d targetCoord, Coord playerTile, Coord targetTile) {
        try {
            GameUI gameui = gui.ui.gui;
            Coord dir = targetTile.sub(playerTile);
            
            // Check if this is a diagonal move (both x and y are non-zero)
            if (dir.x != 0 && dir.y != 0) {
                // Diagonal move - check both adjacent tiles for cliffs
                Coord adj1 = playerTile.add(dir.x, 0);  // Horizontal adjacent
                Coord adj2 = playerTile.add(0, dir.y);  // Vertical adjacent
                
                boolean cliff1 = isCliffTile(adj1, gameui);
                boolean cliff2 = isCliffTile(adj2, gameui);
                
                // If one or both sides have cliffs, use alternative click strategy
                if (cliff1 || cliff2) {
                    // Strategy 1: Click further in the same direction (2 tiles away)
                    // This often works because the click path doesn't graze the cliff edge
                    Coord farTarget = playerTile.add(dir.mul(2));
                    Coord2d farCoord = farTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                    return farCoord;
                }
            } else if (dir.x != 0 || dir.y != 0) {
                // Cardinal direction move (straight)
                // Check if moving into a cliff tile
                if (isCliffTile(targetTile, gameui)) {
                    // Try clicking slightly to the side
                    Coord sideDir = dir.x != 0 ? new Coord(0, 1) : new Coord(1, 0);
                    Coord sideTarget = targetTile.add(sideDir);
                    return sideTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                }
            }
            
        } catch (Exception e) {
            // Ignore errors, use direct path
        }
        
        // Direct path is clear
        return null;
    }
    
    /**
     * Try to move by clicking in alternative directions when stuck near cliffs.
     * This is called when the initial movement attempt fails.
     * 
     * Strategy for cliffs:
     * - Cliff edges can cut through tiles diagonally
     * - Clicking directly into a cliff-adjacent tile may not work
     * - Clicking diagonally often works better
     * - May need multiple click attempts with waits for climb animations
     * 
     * @return true if successfully moved with alternative click
     */
    public static boolean tryAlternativeMove(NGameUI gui, Coord2d originalTarget, Coord playerTile) throws InterruptedException {
        try {
            Coord targetTile = originalTarget.floor(MCache.tilesz);
            Coord dir = targetTile.sub(playerTile);
            
            // For diagonal moves near cliffs, try the diagonal click directly
            // This often works when cardinal clicks fail
            if (dir.x != 0 && dir.y != 0) {
                // Try diagonal click with slight offset towards player
                // This helps when cliff cuts through the tile
                Coord2d diagonalTarget = playerTile.add(dir).mul(MCache.tilesz).add(MCache.tilehsz);
                Coord2d offsetTarget = new Coord2d(
                    diagonalTarget.x - (dir.x * 0.3 * MCache.tilesz.x),
                    diagonalTarget.y - (dir.y * 0.3 * MCache.tilesz.y)
                );
                
                gui.map.wdgmsg("click", Coord.z, offsetTarget.floor(OCache.posres), 1, 0);
                Thread.sleep(300); // Wait for climb animation to start
                if (hasMoved(playerTile, gui)) {
                    return true;
                }
                
                // Try clicking further (2 tiles) in same diagonal direction
                Coord farTarget = playerTile.add(dir.mul(2));
                Coord2d farCoord = farTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                gui.map.wdgmsg("click", Coord.z, farCoord.floor(OCache.posres), 1, 0);
                Thread.sleep(300);
                if (hasMoved(playerTile, gui)) {
                    return true;
                }
            }
            
            // For cardinal moves or if diagonal failed, try cardinal directions
            // First try horizontal, then vertical
            if (dir.x != 0) {
                Coord horizTarget = playerTile.add(dir.x, 0);
                Coord2d horizCoord = horizTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                gui.map.wdgmsg("click", Coord.z, horizCoord.floor(OCache.posres), 1, 0);
                Thread.sleep(300);
                if (hasMoved(playerTile, gui)) {
                    // Now click to original target
                    gui.map.wdgmsg("click", Coord.z, originalTarget.floor(OCache.posres), 1, 0);
                    return true;
                }
            }
            
            if (dir.y != 0) {
                Coord vertTarget = playerTile.add(0, dir.y);
                Coord2d vertCoord = vertTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                gui.map.wdgmsg("click", Coord.z, vertCoord.floor(OCache.posres), 1, 0);
                Thread.sleep(300);
                if (hasMoved(playerTile, gui)) {
                    gui.map.wdgmsg("click", Coord.z, originalTarget.floor(OCache.posres), 1, 0);
                    return true;
                }
            }
            
            // Last resort: try all 4 cardinal directions
            Coord[] cardinalDirs = {new Coord(1, 0), new Coord(-1, 0), new Coord(0, 1), new Coord(0, -1)};
            for (Coord cardDir : cardinalDirs) {
                Coord cardTarget = playerTile.add(cardDir);
                Coord2d cardCoord = cardTarget.mul(MCache.tilesz).add(MCache.tilehsz);
                gui.map.wdgmsg("click", Coord.z, cardCoord.floor(OCache.posres), 1, 0);
                Thread.sleep(200);
                if (hasMoved(playerTile, gui)) {
                    // Click to original target after moving
                    gui.map.wdgmsg("click", Coord.z, originalTarget.floor(OCache.posres), 1, 0);
                    return true;
                }
                // Return to original position check
                if (!hasMoved(playerTile, gui)) {
                    // Didn't move, try next direction
                }
            }
            
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
    
    private static boolean hasMoved(Coord originalPos, NGameUI gui) {
        Coord currentPos = NUtils.player().rc.floor(OCache.posres);
        return !currentPos.equals(originalPos);
    }
    
    /**
     * Check if a tile is a cliff or has cliff terrain
     */
    private static boolean isCliffTile(Coord tc, GameUI gui) {
        try {
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tc));
            if (res != null) {
                String name = res.name;
                if (name.contains("cliff") || name.contains("rock") || name.contains("impass")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}
