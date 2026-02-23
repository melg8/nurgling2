package nurgling.navigation;

import haven.*;
import nurgling.NUtils;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main service for creating and managing linked portal markers across map layers.
 * 
 * Responsibilities:
 * - Detect layer transitions (surface ↔ underground, underground ↔ underground)
 * - Generate unique identifiers (UID) for marker pairs
 * - Create paired markers with IN/OUT direction indicators
 * - Prevent duplicate marker creation (deduplication)
 * - Log all operations for debugging (Constitution Principle VI)
 * 
 * Integration:
 * - Called by PortalMarkerTracker after successful portal traversal
 * - Uses MapFile API for marker creation
 * - Markers automatically appear in all UI contexts (MapWnd, MiniMap, NMiniMap)
 * 
 * Marker Name Format:
 * - Cave: "Cave [UID] IN" (surface), "Cave [UID] OUT" (underground)
 * - Minehole/Ladder: "Hole [UID] IN" (upper), "Hole [UID] OUT" (lower)
 * 
 * UID Generation:
 * - 6-character alphanumeric (0-9a-zA-Z)
 * - Generated from XOR segment pair + portal coordinates
 * - Same UID whether entering or exiting portal
 * - Collision-free by design (SHA-256 hash)
 * 
 * Logging (Constitution Principle VI):
 * - logs/portal_transitions.log: transition events
 * - logs/marker_events.log: marker lifecycle events
 * - logs/uid_generation.log: UID generation events
 */
public class PortalMarkerLinker {
    
    /**
     * MapFile instance for marker creation and lookup.
     * Retrieved dynamically from game UI when needed.
     */
    private MapFile mapFile;
    
    /**
     * UID generator for creating unique identifiers.
     */
    private final MarkerUidGenerator uidGenerator;
    
    /**
     * Logger for structured event logging.
     */
    private final PortalMarkerLogger logger;
    
    /**
     * Debug logger for tracing execution flow.
     */
    private static final DebugLogger debugLog = new DebugLogger("logs/portal_marker_linker_debug.log");
    
    /**
     * Simple file-based debug logger.
     */
    private static class DebugLogger {
        private final String filename;
        
        DebugLogger(String filename) {
            this.filename = filename;
            clear(); // Clear log on startup
        }
        
        private void clear() {
            try {
                java.io.File logDir = new java.io.File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(filename));
                fw.write("=== Portal Marker Linker Debug Log (started " + new java.util.Date() + ") ===\n");
                fw.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        void log(String message) {
            try {
                java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(filename), true);
                fw.write("[" + java.time.LocalTime.now().toString().substring(0,12) + "] " + message + "\n");
                fw.close();
            } catch (Exception e) {
                // Ignore logging errors
            }
        }
    }
    
    /**
     * Regex pattern for matching linked marker names.
     * Matches: "[Name] [UID] [IN/OUT]" where UID is 6 alphanumeric chars.
     * Example: "Cave ABC123 IN", "Hole XYZ789 OUT"
     */
    private static final Pattern LINKED_MARKER_PATTERN = 
        Pattern.compile("^.+\\s([0-9a-zA-Z]{6})\\s(IN|OUT)$");
    
    /**
     * Default marker color for portal markers (white).
     */
    private static final Color DEFAULT_MARKER_COLOR = Color.WHITE;
    
    /**
     * Creates a new PortalMarkerLinker instance.
     */
    public PortalMarkerLinker() {
        this.uidGenerator = new MarkerUidGenerator();
        this.logger = new PortalMarkerLogger();
    }
    
    /**
     * Gets the MapFile instance, retrieving it dynamically from game UI.
     * 
     * @return MapFile instance or null if not available
     */
    private MapFile getMapFile() {
        if (mapFile == null) {
            try {
                GameUI gui = NUtils.getGameUI();
                if (gui != null && gui.mapfile != null) {
                    mapFile = gui.mapfile.file;
                }
            } catch (Exception e) {
                // MapFile not available yet
                logger.logMarkerError("GET_MAPFILE_FAILED", "error=" + e.getMessage());
            }
        }
        return mapFile;
    }
    
    /**
     * Creates linked markers for a detected layer transition.
     * 
     * Process:
     * 1. Generate UID from segment pair and coordinates
     * 2. Check for existing markers with same UID (deduplication)
     * 3. Determine direction (IN/OUT) based on segment transition
     * 4. Create entrance marker on source segment
     * 5. Create exit marker on destination segment
     * 6. Log all operations
     * 
     * @param transition detected layer transition
     * @return PortalMarkerLink representing the created (or existing) link
     */
    public PortalMarkerLink linkPortalMarkers(LayerTransition transition) {
        debugLog.log("[linkPortalMarkers] START: " + transition);
        
        // Step 1: Generate UID from segment pair and coordinates
        String uid = uidGenerator.generate(
            transition.fromSegmentId,
            transition.toSegmentId,
            transition.portalCoordinates
        );

        long xorSegment = transition.getXorSegmentPair();
        debugLog.log("[linkPortalMarkers] UID generated: " + uid + " (xorSegment=" + xorSegment + ")");
        logger.logUidGeneration(xorSegment, transition.portalCoordinates, uid);

        // Step 2: Check for existing markers with same UID
        Optional<PortalMarkerLink> existing = findExistingLink(
            transition.toSegmentId,
            uid
        );

        if (existing.isPresent()) {
            debugLog.log("[linkPortalMarkers] Link already exists - skipping");
            logger.logMarkerSkipped(uid, "already_exists");
            return existing.get();
        }

        // Step 3: Determine direction
        String direction = PortalName.getDirection(
            transition.fromSegmentId,
            transition.toSegmentId,
            transition.portalName
        );
        debugLog.log("[linkPortalMarkers] Direction: " + direction);

        // Step 4: Get marker name prefix and icon
        String namePrefix = PortalName.getNamePrefix(transition.portalName);
        String iconName = PortalName.getIconName(transition.portalName, direction);
        debugLog.log("[linkPortalMarkers] namePrefix=" + namePrefix + ", icon=" + iconName);

        // Step 5: Create entrance marker (IN direction)
        // Entrance is on the fromSegment (where player was before transition)
        String entranceName = namePrefix + " " + uid + " IN";
        Coord entranceCoords = computeMarkerCoordinates(
            transition.portalCoordinates,
            transition.fromSegmentId
        );
        debugLog.log("[linkPortalMarkers] Creating entrance marker: " + entranceName + " at " + entranceCoords);

        long entranceMarkerId = createMarker(
            transition.fromSegmentId,
            entranceCoords,
            entranceName,
            DEFAULT_MARKER_COLOR
        );
        debugLog.log("[linkPortalMarkers] Entrance marker ID: " + entranceMarkerId);

        logger.logMarkerCreated(uid, "IN", transition.fromSegmentId, entranceCoords, entranceName);

        // Step 6: Create exit marker (OUT direction)
        // Exit is on the toSegment (where player appeared after transition)
        String exitName = namePrefix + " " + uid + " OUT";
        Coord exitCoords = computeMarkerCoordinates(
            transition.portalCoordinates,
            transition.toSegmentId
        );
        debugLog.log("[linkPortalMarkers] Creating exit marker: " + exitName + " at " + exitCoords);

        long exitMarkerId = createMarker(
            transition.toSegmentId,
            exitCoords,
            exitName,
            DEFAULT_MARKER_COLOR
        );
        debugLog.log("[linkPortalMarkers] Exit marker ID: " + exitMarkerId);

        logger.logMarkerCreated(uid, "OUT", transition.toSegmentId, exitCoords, exitName);

        // Step 7: Create and return link
        PortalMarkerLink link = new PortalMarkerLink(
            uid,
            transition.portalName,
            entranceMarkerId,
            transition.fromSegmentId,
            entranceCoords,
            exitMarkerId,
            transition.toSegmentId,
            exitCoords
        );
        
        debugLog.log("[linkPortalMarkers] Link created: " + link);

        return link;
    }
    
    /**
     * Finds an existing link with the given UID on a segment.
     * 
     * @param segmentId segment to search
     * @param uid UID to look for
     * @return Optional containing existing link if found
     */
    private Optional<PortalMarkerLink> findExistingLink(long segmentId, String uid) {
        try {
            MapFile file = getMapFile();
            if (file == null || file.markers == null) {
                return Optional.empty();
            }

            // Search for markers with matching UID on this segment
            List<MapFile.Marker> markers = file.markers.stream()
                .filter(m -> m.seg == segmentId)
                .filter(m -> m.nm != null)
                .filter(m -> {
                    String extractedUid = extractUidFromName(m.nm);
                    return uid.equals(extractedUid);
                })
                .collect(Collectors.toList());

            if (!markers.isEmpty()) {
                // Found existing markers with this UID
                // For SMarker, use oid as ID; for PMarker, use hash
                long entranceId = markers.get(0) instanceof MapFile.SMarker 
                    ? ((MapFile.SMarker)markers.get(0)).oid 
                    : markers.get(0).hashCode();
                long exitId = markers.size() > 1 
                    ? (markers.get(1) instanceof MapFile.SMarker 
                        ? ((MapFile.SMarker)markers.get(1)).oid 
                        : markers.get(1).hashCode())
                    : -1;
                    
                return Optional.of(new PortalMarkerLink(
                    uid,
                    "unknown",
                    entranceId, segmentId, markers.get(0).tc,
                    exitId, segmentId, 
                    markers.size() > 1 ? markers.get(1).tc : null
                ));
            }
        } catch (Exception e) {
            logger.logMarkerError("FIND_LINK_ERROR", 
                "segment=" + segmentId + ", uid=" + uid + ", error=" + e.getMessage());
        }

        return Optional.empty();
    }
    
    /**
     * Creates a marker on the specified segment.
     * Creates SMarker (system marker) instead of PMarker (player marker).
     * 
     * @param segmentId segment ID where marker should be created
     * @param coords tile coordinates for marker
     * @param name marker name
     * @param color marker color (ignored for SMarker)
     * @return internal marker ID, or -1 if creation failed
     */
    private long createMarker(long segmentId, Coord coords, String name, Color color) {
        try {
            MapFile file = getMapFile();
            if (file == null) {
                throw new RuntimeException("MapFile not available");
            }

            // Create SMarker (system marker) like the vanilla cave passage system
            // Use cave icon resource for the marker
            String iconResource = PortalName.getIconName(null, "IN");
            Resource.Saved res = new Resource.Saved(Resource.remote(), iconResource, 0);

            // Create SMarker with oid=0 (new marker)
            MapFile.SMarker marker = new MapFile.SMarker(segmentId, coords, name, 0, res);

            // Add marker to MapFile
            if (file.markers != null) {
                file.add(marker);
            }

            // Return pseudo-ID (segment XOR coordinates)
            return segmentId ^ (coords.x * 31 + coords.y);

        } catch (Exception e) {
            logger.logMarkerError("CREATE_MARKER_FAILED",
                "segment=" + segmentId + ", coords=(" + coords.x + "," + coords.y + 
                "), name=\"" + name + "\", error=" + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Computes marker tile coordinates from portal world coordinates.
     * 
     * For initial implementation, uses portal coordinates directly.
     * Future enhancement: Apply relative position transformation.
     *
     * @param portalCoords portal world coordinates
     * @param segmentId target segment ID (for future transformation)
     * @return tile coordinates for marker
     */
    private Coord computeMarkerCoordinates(Coord2d portalCoords, long segmentId) {
        GameUI gui = NUtils.getGameUI();
        if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return portalCoords.floor(MCache.tilesz);
        }

        MCache mcache = gui.map.glob.map;
        MapFile file = getMapFile();
        if (file == null) {
            return portalCoords.floor(MCache.tilesz);
        }

        // Get tile coordinates
        Coord tc = portalCoords.floor(MCache.tilesz);

        // Get grid cell coordinates
        Coord gc = tc.div(MCache.cmaps);

        // Get the grid to access its info
        MCache.Grid grid = mcache.getgridt(tc);
        if (grid == null) {
            return tc;
        }

        // Get GridInfo from MapFile to get segment-local origin
        MapFile.GridInfo info = file.gridinfo.get(grid.id);
        if (info == null) {
            // Fallback: use tile coordinates directly
            return tc;
        }

        // Compute segment-local coordinates using vanilla formula
        // sc = tc + (info.sc - gc) * cmaps
        Coord sc = tc.add(info.sc.sub(gc).mul(MCache.cmaps));

        return sc;
    }
    
    /**
     * Checks if a marker name follows the linked marker format.
     * 
     * @param name marker name to check
     * @return true if name matches "[Name] [UID] [IN/OUT]" format
     */
    public static boolean isLinkedMarkerName(String name) {
        if (name == null) {
            return false;
        }
        return LINKED_MARKER_PATTERN.matcher(name).matches();
    }
    
    /**
     * Extracts UID from a linked marker name.
     * 
     * @param name marker name
     * @return 6-character UID, or null if not a linked marker
     */
    public static String extractUidFromName(String name) {
        if (name == null) {
            return null;
        }
        Matcher matcher = LINKED_MARKER_PATTERN.matcher(name);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Extracts direction (IN/OUT) from a linked marker name.
     *
     * @param name marker name
     * @return "IN" or "OUT", or null if not a linked marker
     */
    public static String extractDirectionFromName(String name) {
        if (name == null) {
            return null;
        }
        Matcher matcher = LINKED_MARKER_PATTERN.matcher(name);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return null;
    }
    
    // ========== Bulk Marking Support ==========
    
    /**
     * Finds all portals on a segment that should be marked.
     * 
     * @param segmentId segment to search
     * @return list of portal coordinates and names
     */
    public List<PortalInfo> findAllPortalsOnSegment(long segmentId) {
        java.util.List<PortalInfo> portals = new java.util.ArrayList<>();
        
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.map == null || gui.map.glob == null) {
                return portals;
            }
            
            Glob glob = gui.map.glob;
            MCache mcache = glob.map;
            
            synchronized (glob.oc) {
                for (Gob gob : glob.oc) {
                    if (gob == null || gob.ngob == null || gob.ngob.name == null) {
                        continue;
                    }
                    
                    String portalName = gob.ngob.name;
                    
                    // Check if this is a portal that should be marked
                    if (!PortalName.shouldMarkPortal(portalName)) {
                        continue;
                    }
                    
                    // Get portal coordinates
                    Coord2d portalCoords = gob.rc;
                    if (portalCoords == null) {
                        continue;
                    }
                    
                    // Get grid for this portal
                    MCache.Grid grid = mcache.getgridt(portalCoords.floor(MCache.tilesz));
                    if (grid == null) {
                        continue;
                    }
                    
                    // Get segment ID for this grid
                    MapFile file = getMapFile();
                    if (file == null) {
                        continue;
                    }
                    
                    MapFile.GridInfo info = file.gridinfo.get(grid.id);
                    if (info == null) {
                        continue;
                    }
                    
                    // Check if this grid belongs to the target segment
                    if (info.seg != segmentId) {
                        continue;
                    }
                    
                    // Add portal to list
                    portals.add(new PortalInfo(portalName, portalCoords));
                }
            }
        } catch (Exception e) {
            logger.logMarkerError("FIND_ALL_PORTALS_FAILED", 
                "segment=" + segmentId + ", error=" + e.getMessage());
        }
        
        return portals;
    }
    
    /**
     * Marks all portals on a segment.
     * 
     * @param segmentId segment to mark
     * @param transitionCoords coordinates of the transition portal (used for UID generation)
     * @return number of markers created
     */
    public int markAllPortalsOnSegment(long segmentId, Coord2d transitionCoords) {
        List<PortalInfo> portals = findAllPortalsOnSegment(segmentId);
        
        if (portals.isEmpty()) {
            return 0;
        }
        
        logger.logBulkMarkingStart(segmentId, portals.size());
        
        int markedCount = 0;
        int skippedCount = 0;
        
        for (PortalInfo portal : portals) {
            try {
                // Create a transition for this portal
                // Use same segment for from/to since we're marking portals on one segment
                // The actual transition will have different segments, but for bulk marking
                // we just need to create markers with unique UIDs
                LayerTransition transition = new LayerTransition(
                    segmentId,
                    segmentId, // Same segment for bulk marking
                    portal.coords,
                    portal.name,
                    null
                );
                
                // Generate UID for this portal
                String uid = uidGenerator.generate(
                    segmentId,
                    segmentId ^ 0xFFFF, // XOR with arbitrary value to get unique UID
                    portal.coords
                );
                
                // Check if markers already exist for this portal
                if (hasMarkersWithUid(segmentId, uid)) {
                    logger.logMarkerSkipped(uid, "already_exists_bulk");
                    skippedCount++;
                    continue;
                }
                
                // Determine direction based on portal type
                String direction = PortalName.getDirection(segmentId, segmentId ^ 0xFFFF, portal.name);
                
                // Get marker name prefix
                String namePrefix = PortalName.getNamePrefix(portal.name);
                
                // Create IN marker
                String inName = namePrefix + " " + uid + " IN";
                Coord inCoords = computeMarkerCoordinates(portal.coords, segmentId);
                createMarker(segmentId, inCoords, inName, DEFAULT_MARKER_COLOR);
                logger.logMarkerCreated(uid, "IN", segmentId, inCoords, inName);
                
                // Create OUT marker
                String outName = namePrefix + " " + uid + " OUT";
                Coord outCoords = computeMarkerCoordinates(portal.coords, segmentId);
                createMarker(segmentId, outCoords, outName, DEFAULT_MARKER_COLOR);
                logger.logMarkerCreated(uid, "OUT", segmentId, outCoords, outName);
                
                markedCount++;
                
            } catch (Exception e) {
                logger.logMarkerError("BULK_MARK_PORTAL_FAILED", 
                    "portal=" + portal.name + ", coords=" + portal.coords + ", error=" + e.getMessage());
            }
        }
        
        logger.logBulkMarkingComplete(segmentId, markedCount, skippedCount);
        return markedCount;
    }
    
    /**
     * Checks if markers with a given UID exist on a segment.
     * 
     * @param segmentId segment to search
     * @param uid UID to look for
     * @return true if markers exist
     */
    private boolean hasMarkersWithUid(long segmentId, String uid) {
        try {
            MapFile file = getMapFile();
            if (file == null || file.markers == null) {
                return false;
            }
            
            return file.markers.stream()
                .filter(m -> m.seg == segmentId)
                .filter(m -> m.nm != null)
                .anyMatch(m -> {
                    String extractedUid = extractUidFromName(m.nm);
                    return uid.equals(extractedUid);
                });
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Information about a portal for bulk marking.
     */
    public static class PortalInfo {
        public final String name;
        public final Coord2d coords;
        
        public PortalInfo(String name, Coord2d coords) {
            this.name = name;
            this.coords = coords;
        }
    }
}
