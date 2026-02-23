package nurgling.navigation;

import haven.*;
import nurgling.NUtils;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;

/**
 * Logs player position with segment, grid, and coordinates every 500ms.
 * Useful for debugging portal marker placement.
 */
public class PlayerPositionLogger {
    private static final long LOG_INTERVAL_MS = 500;
    private static String LOG_FILE = null; // Will be set to absolute path
    
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
            // Initialize LOG_FILE with absolute path
            if (LOG_FILE == null) {
                // Try to find project root by looking for build.xml
                String userDir = System.getProperty("user.dir");
                File buildFile = new File(userDir + "\\build.xml");
                if (!buildFile.exists()) {
                    // We're probably in bin\ directory, go up one level
                    userDir = new File(userDir).getParent();
                }
                LOG_FILE = userDir + "\\bin\\logs\\player_position.log";
                System.out.println("[PlayerPositionLogger] LOG_FILE = " + LOG_FILE);
            }
            
            System.out.println("[PlayerPositionLogger] tick() called");
        
            GameUI gui = NUtils.getGameUI();
            if (gui == null) {
                System.out.println("[PlayerPositionLogger] gui is null");
                return;
            }
            if (gui.map == null) {
                System.out.println("[PlayerPositionLogger] gui.map is null");
                return;
            }
            if (gui.map.glob == null) {
                System.out.println("[PlayerPositionLogger] gui.map.glob is null");
                return;
            }
            if (gui.map.glob.map == null) {
                System.out.println("[PlayerPositionLogger] gui.map.glob.map is null");
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                System.out.println("[PlayerPositionLogger] player is null");
                return;
            }
            if (player.rc == null) {
                System.out.println("[PlayerPositionLogger] player.rc is null");
                return;
            }
            
            System.out.println("[PlayerPositionLogger] Writing to " + LOG_FILE);
            
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
            
            // Ensure log directory exists
            File logDir = new File("bin\\logs");
            if (!logDir.exists()) {
                System.out.println("[PlayerPositionLogger] Creating directory bin\\logs, exists=" + logDir.exists() + ", absolute=" + logDir.getAbsolutePath());
                logDir.mkdirs();
            }
            
            // Write to log
            if (writer == null) {
                System.out.println("[PlayerPositionLogger] Creating writer for " + LOG_FILE + ", absolute=" + new File(LOG_FILE).getAbsolutePath());
                writer = new PrintWriter(new FileWriter(LOG_FILE, true));
                writer.println("=== Player Position Log (started " + new Date() + ") ===");
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            String timestamp = sdf.format(new Date());
            
            String line = String.format("[%s] segment=%d, grid=%d, pos=(%.1f,%.1f), tc=(%d,%d)",
                timestamp, segmentId, gridId, playerRC.x, playerRC.y, tc.x, tc.y);
            
            writer.println(line);
            writer.flush();
            System.out.println("[PlayerPositionLogger] Logged: " + line);
            
        } catch (Exception e) {
            // Log error to console for debugging
            System.out.println("[PlayerPositionLogger] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
