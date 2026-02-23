package nurgling.navigation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logger for portal marker linking events.
 * 
 * Writes structured log entries to files for debugging and troubleshooting.
 * This bypasses Java Logger which may not work reliably in the game environment.
 * 
 * Log files:
 * - logs/portal_transitions.log: Layer transition events
 * - logs/marker_events.log: Marker lifecycle events (create, skip, error)
 * - logs/uid_generation.log: UID generation events
 * 
 * All timestamps are in ISO-8601 format for easy parsing.
 */
public class PortalMarkerLogger {
    
    private static final String LOG_DIR = "logs";
    private static final String TRANSITIONS_LOG = "portal_transitions.log";
    private static final String MARKER_EVENTS_LOG = "marker_events.log";
    private static final String UID_GENERATION_LOG = "uid_generation.log";
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Ensures log directory exists.
     */
    private void ensureLogDir() {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }
    
    /**
     * Gets current timestamp in ISO-8601 format.
     */
    private String timestamp() {
        return DATE_FORMAT.format(new Date());
    }
    
    /**
     * Writes a message to a log file.
     * 
     * @param filename log file name
     * @param message message to write
     */
    private void writeToFile(String filename, String message) {
        try {
            ensureLogDir();
            File logFile = new File(LOG_DIR, filename);
            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + timestamp() + "] " + message + "\n");
            fw.close();
        } catch (IOException e) {
            // Silently ignore logging errors - logging is non-critical
            System.err.println("[PortalMarkerLogger] Failed to write to " + filename + ": " + e.getMessage());
        }
    }
    
    // ========== Transition Logging ==========
    
    /**
     * Logs a layer transition event.
     * 
     * @param fromSegmentId source segment ID
     * @param toSegmentId destination segment ID
     * @param portalCoords portal coordinates
     * @param portalName portal resource name
     */
    public void logTransition(long fromSegmentId, long toSegmentId, haven.Coord2d portalCoords, String portalName) {
        String message = String.format(
            "TRANSITION fromSegment=%d toSegment=%d portalCoords=(%.0f,%.0f) portalName=%s",
            fromSegmentId, toSegmentId,
            portalCoords != null ? portalCoords.x : -1, portalCoords != null ? portalCoords.y : -1,
            portalName != null ? portalName : "null"
        );
        writeToFile(TRANSITIONS_LOG, message);
    }
    
    /**
     * Logs a skipped transition (e.g., cellar excluded, teleport detected).
     * 
     * @param reason reason for skipping
     * @param context additional context
     */
    public void logSkippedTransition(String reason, String context) {
        String message = String.format("SKIPPED reason=%s context=%s", reason, context);
        writeToFile(TRANSITIONS_LOG, message);
    }
    
    // ========== Marker Event Logging ==========
    
    /**
     * Logs successful marker creation.
     * 
     * @param uid unique identifier
     * @param direction IN or OUT
     * @param segmentId segment where marker was created
     * @param coords marker coordinates
     * @param markerName full marker name
     */
    public void logMarkerCreated(String uid, String direction, long segmentId, haven.Coord coords, String markerName) {
        String message = String.format(
            "MARKER_CREATED uid=%s direction=%s segmentId=%d coords=(%d,%d) name=%s",
            uid, direction, segmentId,
            coords != null ? coords.x : -1, coords != null ? coords.y : -1,
            markerName != null ? markerName : "null"
        );
        writeToFile(MARKER_EVENTS_LOG, message);
    }
    
    /**
     * Logs skipped marker creation (deduplication).
     * 
     * @param uid unique identifier
     * @param reason reason for skipping
     */
    public void logMarkerSkipped(String uid, String reason) {
        String message = String.format("MARKER_SKIPPED uid=%s reason=%s", uid, reason);
        writeToFile(MARKER_EVENTS_LOG, message);
    }
    
    /**
     * Logs marker creation error.
     * 
     * @param uid unique identifier (may be null if generation failed)
     * @param error error message
     */
    public void logMarkerError(String uid, String error) {
        String message = String.format("MARKER_ERROR uid=%s error=%s", 
            uid != null ? uid : "null", error);
        writeToFile(MARKER_EVENTS_LOG, message);
    }
    
    /**
     * Logs bulk marking operation start.
     * 
     * @param segmentId segment being scanned
     * @param portalCount number of portals found
     */
    public void logBulkMarkingStart(long segmentId, int portalCount) {
        String message = String.format("BULK_MARKING_START segmentId=%d portalsFound=%d", segmentId, portalCount);
        writeToFile(MARKER_EVENTS_LOG, message);
    }
    
    /**
     * Logs bulk marking operation complete.
     * 
     * @param segmentId segment that was processed
     * @param markedCount number of markers created
     * @param skippedCount number of markers skipped (already existed)
     */
    public void logBulkMarkingComplete(long segmentId, int markedCount, int skippedCount) {
        String message = String.format("BULK_MARKING_COMPLETE segmentId=%d marked=%d skipped=%d", 
            segmentId, markedCount, skippedCount);
        writeToFile(MARKER_EVENTS_LOG, message);
    }
    
    // ========== UID Generation Logging ==========
    
    /**
     * Logs UID generation event.
     * 
     * @param xorSegmentPair XOR of segment IDs
     * @param coords portal coordinates
     * @param uid generated UID
     */
    public void logUidGeneration(long xorSegmentPair, haven.Coord2d coords, String uid) {
        String message = String.format(
            "UID_GENERATED xorSegmentPair=%d coords=(%.0f,%.0f) uid=%s",
            xorSegmentPair,
            coords != null ? coords.x : -1, coords != null ? coords.y : -1,
            uid != null ? uid : "null"
        );
        writeToFile(UID_GENERATION_LOG, message);
    }
    
    /**
     * Logs UID generation error.
     * 
     * @param input input string that failed to hash
     * @param error error message
     */
    public void logUidError(String input, String error) {
        String message = String.format("UID_ERROR input=%s error=%s", 
            input != null ? input : "null", error);
        writeToFile(UID_GENERATION_LOG, message);
    }
}
