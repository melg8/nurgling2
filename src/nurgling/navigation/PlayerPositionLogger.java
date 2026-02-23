package nurgling.navigation;

import haven.*;
import nurgling.NUtils;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logs player position with segment, grid, and coordinates every 500ms.
 * Useful for debugging portal marker placement.
 */
public class PlayerPositionLogger {
    private static final long LOG_INTERVAL_MS = 500;
    private static final String LOG_FILE = "bin\\logs\\player_position.log";
    
    private long lastLogTime = 0;
    private PrintWriter writer = null;
    
    private Long lastSegmentId = null;
    private Long lastGridId = null;
    private Coord2d lastPosition = null;
    
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastLogTime < LOG_INTERVAL_MS) {
            return;
        }
        lastLogTime = now;
        
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null || player.rc == null) {
                return;
            }
            
            MCache mcache = gui.map.glob.map;
            Coord2d playerRC = player.rc;
            
            // Get grid
            MCache.Grid grid = mcache.getgridt(playerRC.floor(MCache.tilesz));
            long gridId = (grid != null) ? grid.id : -1;
            
            // Get segment
            long segmentId = -1;
            if (gui.mapfile != null && gui.mapfile.view != null && gui.mapfile.view.sessloc != null) {
                segmentId = gui.mapfile.view.sessloc.seg.id;
            }
            
            // Get tile coordinates
            Coord tc = playerRC.floor(MCache.tilesz);
            
            // Update last position (always log, don't skip)
            lastSegmentId = segmentId;
            lastGridId = gridId;
            lastPosition = playerRC;
            
            // Write to log
            if (writer == null) {
                writer = new PrintWriter(new FileWriter(LOG_FILE, true));
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            String timestamp = sdf.format(new Date());
            
            String line = String.format("[%s] segment=%d, grid=%d, pos=(%.1f,%.1f), tc=(%d,%d)%n",
                timestamp, segmentId, gridId, playerRC.x, playerRC.y, tc.x, tc.y);
            
            writer.write(line);
            writer.flush();
            
        } catch (Exception e) {
            // Ignore logging errors
        }
    }
    
    public void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
