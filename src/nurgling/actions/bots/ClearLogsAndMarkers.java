package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.navigation.PortalMarkerTracker;

import java.io.File;

/**
 * Clear Logs and Markers - Test utility to delete all logs and map markers.
 * Useful for testing portal marker system.
 */
public class ClearLogsAndMarkers implements Action {
    
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null) return Results.FAIL();
        
        GameUI gameui = NUtils.getGameUI();
        gui.msg("=== Clearing Logs and Markers ===");
        
        // Clear log files
        clearLogs(gui);
        
        // Clear map markers
        clearMarkers(gui, gameui);
        
        // Clear portal marker tracker state
        clearTracker(gui);
        
        gui.msg("=== Clear Complete ===");
        return Results.SUCCESS();
    }
    
    private void clearLogs(NGameUI gui) {
        String[] logFiles = {
            "bin\\logs\\portal_marker_tracker_debug.log",
            "bin\\logs\\portal_marker_linker_debug.log",
            "bin\\logs\\marker_events.log",
            "bin\\logs\\portal_transitions.log",
            "bin\\logs\\uid_generation.log",
            "bin\\logs\\player_position.log"
        };
        
        int deletedCount = 0;
        for (String logFile : logFiles) {
            File file = new File(logFile);
            if (file.exists() && file.delete()) {
                deletedCount++;
                gui.msg("Deleted: " + logFile);
            }
        }
        
        gui.msg("Deleted " + deletedCount + " log files");
    }
    
    private void clearMarkers(NGameUI gui, GameUI gameui) {
        try {
            if (gameui == null || gameui.mapfile == null) {
                gui.msg("MapFile not available");
                return;
            }
            
            MapFile file = gameui.mapfile.file;
            if (file == null) {
                gui.msg("MapFile.file is null");
                return;
            }
            
            if (file.markers == null) {
                gui.msg("No markers to clear");
                return;
            }
            
            int markerCount = file.markers.size();
            file.markers.clear();
            file.smarkers.clear();
            
            gui.msg("Cleared " + markerCount + " map markers");
            
        } catch (Exception e) {
            gui.msg("Error clearing markers: " + e.getMessage());
        }
    }
    
    private void clearTracker(NGameUI gui) {
        try {
            PortalMarkerTracker.clearInstance();
            gui.msg("PortalMarkerTracker state cleared");
        } catch (Exception e) {
            gui.msg("Error clearing tracker: " + e.getMessage());
        }
    }
}
