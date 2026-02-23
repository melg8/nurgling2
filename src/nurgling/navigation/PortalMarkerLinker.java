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
                if (gui == null) {
                    return null;
                }
                if (gui.mapfile == null) {
                    return null;
                }
                if (gui.mapfile.file == null) {
                    return null;
                }
                mapFile = gui.mapfile.file;
                debugLog.log("[getMapFile] OK");
            } catch (Exception e) {
                debugLog.log("[getMapFile] ERROR: " + e.getClass().getSimpleName());
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
     * @throws haven.Loading if map data not ready (for retry)
     */
    public PortalMarkerLink linkPortalMarkers(LayerTransition transition) throws haven.Loading {
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

        // Step 2: Get MapFile
        MapFile file = getMapFile();
        if (file == null) {
            debugLog.log("[linkPortalMarkers] MapFile not available - throwing Loading");
            throw new haven.Loading("Waiting for map data...");
        }

        // Step 3: Check for existing markers with same UID
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

        // Step 5 & 6: Create IN and OUT markers
        // IN marker is always on surface (where player enters/exits cave)
        // OUT marker is always underground (where player enters/exits cave)
        // Direction tells us which way player is moving:
        // - "IN" = entering cave (from surface to underground)
        // - "OUT" = exiting cave (from underground to surface)
        //
        // IMPORTANT: Use DIFFERENT coordinates for IN and OUT markers!
        // - IN marker (surface): use player position AFTER transition (where player appeared)
        // - OUT marker (underground): use portal coordinates (where portal is located)
        
        debugLog.log("[linkPortalMarkers] direction=" + direction);
        debugLog.log("[linkPortalMarkers] portalCoordinates=(" + 
            (transition.portalCoordinates != null ? transition.portalCoordinates.x + "," + transition.portalCoordinates.y : "null") + ")");
        debugLog.log("[linkPortalMarkers] playerPositionAtPortal=(" + 
            (transition.playerPositionAtPortal != null ? transition.playerPositionAtPortal.x + "," + transition.playerPositionAtPortal.y : "null") + ")");
        debugLog.log("[linkPortalMarkers] playerPositionAfterTransition=(" + 
            (transition.playerPositionAfterTransition != null ? transition.playerPositionAfterTransition.x + "," + transition.playerPositionAfterTransition.y : "null") + ")");
        
        // Create IN marker (on surface)
        String entranceName = namePrefix + " " + uid + " IN";
        long inMarkerSegmentId = "OUT".equals(direction) ? transition.toSegmentId : transition.fromSegmentId;

        // For IN marker on surface, use player position after transition (where player appeared)
        Coord2d inMarkerPosition = "OUT".equals(direction) ? transition.playerPositionAfterTransition : transition.playerPositionAtPortal;
        debugLog.log("[linkPortalMarkers] Creating IN marker on segment=" + inMarkerSegmentId + " at " + inMarkerPosition);
        
        // Check if IN marker already exists
        boolean inMarkerExists = markerExists(inMarkerSegmentId, entranceName);
        long entranceMarkerId;
        Coord entranceCoords;
        
        if (inMarkerExists) {
            debugLog.log("[linkPortalMarkers] IN marker already exists - skipping creation");
            try {
                entranceCoords = computeMarkerCoordinates(inMarkerPosition, inMarkerSegmentId);
            } catch (haven.Loading e) {
                // Use dummy coords if Loading - marker already exists anyway
                entranceCoords = inMarkerPosition.floor(MCache.tilesz);
            }
            entranceMarkerId = inMarkerSegmentId ^ (entranceCoords.x * 31 + entranceCoords.y);
        } else {
            try {
                entranceCoords = computeMarkerCoordinates(inMarkerPosition, inMarkerSegmentId);
            } catch (haven.Loading e) {
                debugLog.log("[linkPortalMarkers] IN marker Loading: " + e.getMessage());
                throw e; // Rethrow for retry
            }
            debugLog.log("[linkPortalMarkers] IN marker coords: " + entranceCoords);

            entranceMarkerId = createMarker(
                inMarkerSegmentId,
                entranceCoords,
                entranceName,
                DEFAULT_MARKER_COLOR
            );
        }
        debugLog.log("[linkPortalMarkers] Entrance marker ID: " + entranceMarkerId);

        logger.logMarkerCreated(uid, "IN", inMarkerSegmentId, entranceCoords, entranceName);

        // Create OUT marker (underground)
        String exitName = namePrefix + " " + uid + " OUT";
        long outMarkerSegmentId = "OUT".equals(direction) ? transition.fromSegmentId : transition.toSegmentId;

        // For OUT marker underground, use portal coordinates (where portal is located)
        Coord2d outMarkerPosition = transition.portalCoordinates;
        debugLog.log("[linkPortalMarkers] Creating OUT marker on segment=" + outMarkerSegmentId + " at " + outMarkerPosition);
        
        // Check if OUT marker already exists
        boolean outMarkerExists = markerExists(outMarkerSegmentId, exitName);
        long exitMarkerId;
        Coord exitCoords;
        
        if (outMarkerExists) {
            debugLog.log("[linkPortalMarkers] OUT marker already exists - skipping creation");
            try {
                exitCoords = computeMarkerCoordinates(outMarkerPosition, outMarkerSegmentId);
            } catch (haven.Loading e) {
                // Use dummy coords if Loading - marker already exists anyway
                exitCoords = outMarkerPosition.floor(MCache.tilesz);
            }
            exitMarkerId = outMarkerSegmentId ^ (exitCoords.x * 31 + exitCoords.y);
        } else {
            try {
                exitCoords = computeMarkerCoordinates(outMarkerPosition, outMarkerSegmentId);
            } catch (haven.Loading e) {
                debugLog.log("[linkPortalMarkers] OUT marker Loading: " + e.getMessage());
                throw e; // Rethrow for retry
            }
            debugLog.log("[linkPortalMarkers] OUT marker coords: " + exitCoords);

            exitMarkerId = createMarker(
                outMarkerSegmentId,
                exitCoords,
                exitName,
                DEFAULT_MARKER_COLOR
            );
        }
        debugLog.log("[linkPortalMarkers] Exit marker ID: " + exitMarkerId);

        logger.logMarkerCreated(uid, "OUT", outMarkerSegmentId, exitCoords, exitName);

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

            // Only return existing link if BOTH markers exist (IN and OUT)
            if (markers.size() >= 2) {
                // Found complete link with both markers
                long entranceId = markers.get(0) instanceof MapFile.SMarker
                    ? ((MapFile.SMarker)markers.get(0)).oid
                    : markers.get(0).hashCode();
                long exitId = markers.get(1) instanceof MapFile.SMarker
                    ? ((MapFile.SMarker)markers.get(1)).oid
                    : markers.get(1).hashCode();

                return Optional.of(new PortalMarkerLink(
                    uid,
                    "unknown",
                    entranceId, segmentId, markers.get(0).tc,
                    exitId, segmentId,
                    markers.get(1).tc
                ));
            } else if (markers.size() == 1) {
                // Only one marker exists - link is incomplete, don't skip
                debugLog.log("[findExistingLink] Only 1 marker found for UID " + uid + " - link incomplete, will create missing marker");
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
                debugLog.log("[createMarker] MapFile is null - throwing Loading");
                throw new haven.Loading("MapFile not available");
            }

            debugLog.log("[createMarker] " + name + " segment=" + segmentId + " at " + coords);
            debugLog.log("[createMarker] file.markers=" + (file.markers != null ? "not null" : "null") + ", size=" + (file.markers != null ? file.markers.size() : "N/A"));

            // Create SMarker (system marker)
            String iconResource = PortalName.getIconName(null, "IN");
            Resource.Saved res = new Resource.Saved(Resource.remote(), iconResource, 0);

            MapFile.SMarker marker = new MapFile.SMarker(segmentId, coords, name, 0, res);
            debugLog.log("[createMarker] SMarker created: oid=0, res=" + iconResource);

            // Add marker - this may throw Loading if map data not ready
            debugLog.log("[createMarker] Calling file.add()...");
            file.add(marker);
            debugLog.log("[createMarker] Marker added successfully: " + name);
            return segmentId ^ (coords.x * 31 + coords.y);

        } catch (haven.Loading e) {
            // Map data not ready - rethrow to trigger retry
            debugLog.log("[createMarker] Loading: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            debugLog.log("[createMarker] ERROR: " + e.getMessage());
            e.printStackTrace();
            logger.logMarkerError("CREATE_MARKER_FAILED",
                "segment=" + segmentId + ", coords=(" + coords.x + "," + coords.y +
                "), name=\"" + name + "\", error=" + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Computes marker tile coordinates from portal world coordinates.
     *
     * Uses same formula as vanilla MiniMap.markobjs():
     * sc = tc + (info.sc - gc) * cmaps
     *
     * Throws Loading exception if GridInfo not available - caller should retry.
     *
     * @param portalCoords portal world coordinates
     * @param segmentId target segment ID (for GridInfo lookup)
     * @return tile coordinates for marker in segment-local space
     * @throws haven.Loading if GridInfo not available
     */
    private Coord computeMarkerCoordinates(Coord2d portalCoords, long segmentId) throws haven.Loading {
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
                throw new haven.Loading("GUI not available");
            }

            MCache mcache = gui.map.glob.map;
            MapFile file = getMapFile();
            if (file == null) {
                throw new haven.Loading("MapFile not available");
            }

            // Get tile coordinates
            Coord tc = portalCoords.floor(MCache.tilesz);

            // Get grid cell coordinates
            Coord gc = tc.div(MCache.cmaps);

            // Get the grid to access its info
            MCache.Grid grid = mcache.getgridt(tc);
            if (grid == null) {
                throw new haven.Loading("Grid not available for tc=" + tc);
            }

            // Get GridInfo from MapFile to get segment-local origin
            MapFile.GridInfo info = file.gridinfo.get(grid.id);
            if (info == null) {
                // GridInfo not loaded yet - throw Loading for retry
                debugLog.log("[computeMarkerCoordinates] GridInfo returned null for grid " + grid.id + " (segment " + segmentId + ") - throwing Loading");
                throw new haven.Loading("GridInfo not available for grid " + grid.id);
            }

            // Compute segment-local coordinates using vanilla formula:
            // sc = tc + (info.sc - gc) * cmaps
            Coord sc = tc.add(info.sc.sub(gc).mul(MCache.cmaps));
            debugLog.log("[computeMarkerCoordinates] Computed sc=" + sc + " from tc=" + tc + ", info.sc=" + info.sc + ", gc=" + gc);
            return sc;
        } catch (haven.Loading e) {
            // Re-throw Loading exceptions
            throw e;
        } catch (Exception e) {
            debugLog.log("[computeMarkerCoordinates] ERROR: " + e.getMessage());
            throw new haven.Loading("computeMarkerCoordinates failed: " + e.getMessage());
        }
    }
    
    /**
     * Pre-loads GridInfo for a segment to ensure it's available.
     *
     * @param file MapFile to load from
     * @param segmentId segment ID to find
     * @return Loading exception if not available, null if loaded
     */
    private haven.Loading preloadGridInfo(MapFile file, long segmentId) {
        try {
            // GridInfo is keyed by grid ID, not segment ID
            // We need to iterate through known grids to find ones for this segment
            // For now, just return null - GridInfo will be loaded on demand in computeMarkerCoordinates
            // The retry mechanism will handle cases where it's not available yet
            debugLog.log("[preloadGridInfo] Skipping preload for segment " + segmentId + " - will load on demand");
            return null;
        } catch (Exception e) {
            return new haven.Loading("preloadGridInfo failed: " + e.getMessage());
        }
    }

    /**
     * Checks if a marker with the given name exists on the specified segment.
     *
     * @param segmentId segment to search
     * @param markerName marker name to find
     * @return true if marker exists
     */
    private boolean markerExists(long segmentId, String markerName) {
        try {
            MapFile file = getMapFile();
            if (file == null || file.markers == null) {
                return false;
            }

            return file.markers.stream()
                .filter(m -> m.seg == segmentId)
                .filter(m -> m.nm != null)
                .anyMatch(m -> m.nm.equals(markerName));
        } catch (Exception e) {
            return false;
        }
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
                    portal.coords, // playerPositionAtPortal (same as portal for bulk marking)
                    portal.coords  // playerPositionAfterTransition (same as portal for bulk marking)
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
