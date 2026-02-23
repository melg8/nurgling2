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
        // IMPORTANT: Use surface coordinates for UID generation to ensure same UID for both directions!
        // - For cave transitions: use portalCoordinates (portal gob is on surface for cavein2)
        // - playerPositionAtPortal may be captured AFTER transition (underground)!

        String direction = PortalName.getDirection(
            transition.fromSegmentId,
            transition.toSegmentId,
            transition.portalName
        );

        // Use surface coordinates for UID generation
        Coord2d uidCoords;
        if ("IN".equals(direction)) {
            // Entering cave: use portalCoordinates (portal gob is on surface)
            // playerPositionAtPortal may be underground if captured after transition
            uidCoords = transition.portalCoordinates != null ? 
                transition.portalCoordinates : transition.playerPositionAtPortal;
        } else {
            // Exiting cave: player is on surface at playerPositionAfterTransition
            uidCoords = transition.playerPositionAfterTransition;
        }

        debugLog.log("[linkPortalMarkers] Using UID coords: (" +
            (uidCoords != null ? uidCoords.x + "," + uidCoords.y : "null") + ") from " +
            ("IN".equals(direction) ? "portalCoordinates" : "playerPositionAfterTransition"));
        
        String uid = uidGenerator.generate(
            transition.fromSegmentId,
            transition.toSegmentId,
            uidCoords
        );

        long xorSegment = transition.getXorSegmentPair();
        debugLog.log("[linkPortalMarkers] UID generated: " + uid + " (xorSegment=" + xorSegment + ")");
        logger.logUidGeneration(xorSegment, uidCoords, uid);

        // Step 2: Get MapFile
        MapFile file = getMapFile();
        if (file == null) {
            debugLog.log("[linkPortalMarkers] MapFile not available - throwing Loading");
            throw new haven.Loading("Waiting for map data...");
        }

        // Step 3: Check for existing markers with same UID (searches ALL segments)
        Optional<PortalMarkerLink> existing = findExistingLink(uid);

        if (existing.isPresent()) {
            debugLog.log("[linkPortalMarkers] Link already exists - skipping");
            logger.logMarkerSkipped(uid, "already_exists");
            return existing.get();
        }

        debugLog.log("[linkPortalMarkers] Direction: " + direction);

        // Step 5: Get marker name prefix and icon
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
        // IMPORTANT: Use player positions for marker coordinates!
        // - IN marker (surface): use player position ON SURFACE
        // - OUT marker (underground): use player position UNDERGROUND
        //
        // This ensures computeMarkerCoordinates gets the correct GridInfo for the target segment.
        // Using portalCoordinates can cause segment mismatch because the portal gob may be in
        // a different segment than where the marker should be placed.

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

        // For IN marker on surface:
        // - If direction=IN (entering cave): Use portalCoordinates (portal gob is on surface)
        //   IMPORTANT: playerPositionAtPortal may be captured AFTER transition (underground)!
        // - If direction=OUT (exiting cave): player is on surface at playerPositionAfterTransition
        Coord2d inMarkerPosition;
        if ("OUT".equals(direction)) {
            // Exiting cave: player is on surface after transition
            inMarkerPosition = transition.playerPositionAfterTransition;
        } else {
            // Entering cave: portal gob is on surface, use portal coordinates
            // playerPositionAtPortal may be underground if captured after transition
            inMarkerPosition = transition.portalCoordinates != null ? 
                transition.portalCoordinates : transition.playerPositionAtPortal;
        }
        debugLog.log("[linkPortalMarkers] Creating IN marker on segment=" + inMarkerSegmentId + " at " + inMarkerPosition);

        // Check if IN marker already exists
        boolean inMarkerExists = markerExists(inMarkerSegmentId, entranceName);
        long entranceMarkerId;
        Coord entranceCoords;

        if (inMarkerExists) {
            debugLog.log("[linkPortalMarkers] IN marker already exists - skipping creation");
            try {
                entranceCoords = computeMarkerCoordinates(inMarkerPosition, inMarkerSegmentId, transition.portalGridInfo);
            } catch (haven.Loading e) {
                // Use dummy coords if Loading - marker already exists anyway
                entranceCoords = inMarkerPosition.floor(MCache.tilesz);
            }
            entranceMarkerId = inMarkerSegmentId ^ (entranceCoords.x * 31 + entranceCoords.y);
        } else {
            debugLog.log("[linkPortalMarkers] IN marker does NOT exist, attempting to create...");
            try {
                debugLog.log("[linkPortalMarkers] Calling computeMarkerCoordinates for IN marker...");
                entranceCoords = computeMarkerCoordinates(inMarkerPosition, inMarkerSegmentId, transition.portalGridInfo);
                debugLog.log("[linkPortalMarkers] IN marker coords computed: " + entranceCoords);
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

        // For OUT marker underground:
        // - If direction=IN (entering cave): player is underground at playerPositionAfterTransition
        // - If direction=OUT (exiting cave): Use portalCoordinates (caveout portal is underground)
        //   IMPORTANT: playerPositionAtPortal may be captured AFTER transition (on surface)!
        Coord2d outMarkerPosition;
        if ("OUT".equals(direction)) {
            // Exiting cave: caveout portal gob is underground, use portal coordinates
            // playerPositionAtPortal may be on surface if captured after transition
            outMarkerPosition = transition.portalCoordinates != null ? 
                transition.portalCoordinates : transition.playerPositionAtPortal;
        } else {
            // Entering cave: player is underground after transition
            outMarkerPosition = transition.playerPositionAfterTransition;
        }
        debugLog.log("[linkPortalMarkers] Creating OUT marker on segment=" + outMarkerSegmentId + " at " + outMarkerPosition);

        // Check if OUT marker already exists
        boolean outMarkerExists = markerExists(outMarkerSegmentId, exitName);
        long exitMarkerId;
        Coord exitCoords;

        if (outMarkerExists) {
            debugLog.log("[linkPortalMarkers] OUT marker already exists - skipping creation");
            try {
                exitCoords = computeMarkerCoordinates(outMarkerPosition, outMarkerSegmentId, transition.portalGridInfo);
            } catch (haven.Loading e) {
                // Use dummy coords if Loading - marker already exists anyway
                exitCoords = outMarkerPosition.floor(MCache.tilesz);
            }
            exitMarkerId = outMarkerSegmentId ^ (exitCoords.x * 31 + exitCoords.y);
        } else {
            try {
                exitCoords = computeMarkerCoordinates(outMarkerPosition, outMarkerSegmentId, transition.portalGridInfo);
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
     * Finds an existing link with the given UID.
     * Searches for markers with matching UID across ALL segments.
     *
     * @param uid UID to look for
     * @return Optional containing existing link if BOTH markers exist (IN and OUT)
     */
    private Optional<PortalMarkerLink> findExistingLink(String uid) {
        try {
            MapFile file = getMapFile();
            if (file == null || file.markers == null) {
                return Optional.empty();
            }

            // Search for markers with matching UID across ALL segments
            List<MapFile.Marker> markers = file.markers.stream()
                .filter(m -> m.nm != null)
                .filter(m -> {
                    String extractedUid = extractUidFromName(m.nm);
                    return uid.equals(extractedUid);
                })
                .collect(Collectors.toList());

            // Only return existing link if BOTH markers exist (IN and OUT)
            if (markers.size() >= 2) {
                // Found complete link with both markers
                MapFile.Marker inMarker = markers.stream()
                    .filter(m -> m.nm.endsWith(" IN"))
                    .findFirst()
                    .orElse(markers.get(0));
                MapFile.Marker outMarker = markers.stream()
                    .filter(m -> m.nm.endsWith(" OUT"))
                    .findFirst()
                    .orElse(markers.get(1));

                long entranceId = inMarker instanceof MapFile.SMarker
                    ? ((MapFile.SMarker)inMarker).oid
                    : inMarker.hashCode();
                long exitId = outMarker instanceof MapFile.SMarker
                    ? ((MapFile.SMarker)outMarker).oid
                    : outMarker.hashCode();

                debugLog.log("[findExistingLink] Found complete link with UID " + uid + 
                    ": IN on seg=" + inMarker.seg + " at " + inMarker.tc + 
                    ", OUT on seg=" + outMarker.seg + " at " + outMarker.tc);

                return Optional.of(new PortalMarkerLink(
                    uid,
                    "unknown",
                    entranceId, inMarker.seg, inMarker.tc,
                    exitId, outMarker.seg,
                    outMarker.tc
                ));
            } else if (markers.size() == 1) {
                // Only one marker exists - link is incomplete, don't skip
                debugLog.log("[findExistingLink] Only 1 marker found for UID " + uid + " - link incomplete, will create missing marker");
            }
        } catch (Exception e) {
            logger.logMarkerError("FIND_LINK_ERROR",
                "uid=" + uid + ", error=" + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Finds an existing link with the given UID on a specific segment.
     * Deprecated: use findExistingLink(String uid) instead.
     *
     * @param segmentId segment to search
     * @param uid UID to look for
     * @return Optional containing existing link if found
     */
    @Deprecated
    private Optional<PortalMarkerLink> findExistingLink(long segmentId, String uid) {
        return findExistingLink(uid);
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
     * IMPORTANT: The coordinates must be in the target segment!
     * - For IN marker (surface): use player position on surface
     * - For OUT marker (underground): use player position underground
     *
     * @param coords world coordinates in the target segment
     * @param segmentId target segment ID (for GridInfo lookup)
     * @param portalGridInfo cached GridInfo from portal (can be used for surface markers)
     * @return tile coordinates for marker in segment-local space
     * @throws haven.Loading if GridInfo not available
     */
    private Coord computeMarkerCoordinates(Coord2d coords, long segmentId, haven.MapFile.GridInfo portalGridInfo) throws haven.Loading {
        try {
            debugLog.log("[computeMarkerCoordinates] ENTER: coords=" + coords + ", segmentId=" + segmentId);

            GameUI gui = NUtils.getGameUI();
            debugLog.log("[computeMarkerCoordinates] gui=" + (gui != null ? "not null" : "NULL"));

            if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
                String reason = gui == null ? "gui is null" :
                               gui.map == null ? "gui.map is null" :
                               gui.map.glob == null ? "gui.map.glob is null" :
                               "gui.map.glob.map is null";
                debugLog.log("[computeMarkerCoordinates] Loading: " + reason);
                throw new haven.Loading("GUI not available: " + reason);
            }
            debugLog.log("[computeMarkerCoordinates] gui.map.glob.map OK");

            MCache mcache = gui.map.glob.map;
            MapFile file = getMapFile();
            debugLog.log("[computeMarkerCoordinates] file=" + (file != null ? "not null" : "NULL"));
            if (file == null) {
                debugLog.log("[computeMarkerCoordinates] Loading: MapFile not available");
                throw new haven.Loading("MapFile not available");
            }

            // Get tile coordinates
            Coord tc = coords.floor(MCache.tilesz);
            debugLog.log("[computeMarkerCoordinates] tc=" + tc);

            // Get grid cell coordinates
            Coord gc = tc.div(MCache.cmaps);
            debugLog.log("[computeMarkerCoordinates] gc=" + gc);

            // Try to use cached portal GridInfo first (works for surface markers when underground)
            MapFile.GridInfo info = null;
            if (portalGridInfo != null && portalGridInfo.seg == segmentId) {
                debugLog.log("[computeMarkerCoordinates] Using cached portal GridInfo: seg=" + portalGridInfo.seg + ", sc=" + portalGridInfo.sc);
                info = portalGridInfo;
            } else {
                // Get the grid to access its info
                debugLog.log("[computeMarkerCoordinates] Calling mcache.getgridt(" + tc + ")...");
                MCache.Grid grid = null;
                try {
                    grid = mcache.getgridt(tc);
                } catch (Exception e) {
                    debugLog.log("[computeMarkerCoordinates] getgridt threw exception: " + e.getClass().getName() + ": " + e.getMessage());
                    throw new haven.Loading("getgridt failed: " + e.getMessage());
                }
                debugLog.log("[computeMarkerCoordinates] grid=" + (grid != null ? "id=" + grid.id : "NULL"));
                if (grid == null) {
                    debugLog.log("[computeMarkerCoordinates] Loading: Grid not available for tc=" + tc);
                    throw new haven.Loading("Grid not available for tc=" + tc);
                }

                // Get GridInfo from MapFile to get segment-local origin
                debugLog.log("[computeMarkerCoordinates] Calling file.gridinfo.get(" + grid.id + ")...");
                try {
                    info = file.gridinfo.get(grid.id);
                } catch (Exception e) {
                    debugLog.log("[computeMarkerCoordinates] gridinfo.get threw exception: " + e.getClass().getName() + ": " + e.getMessage());
                    throw new haven.Loading("gridinfo.get failed: " + e.getMessage());
                }
                debugLog.log("[computeMarkerCoordinates] info=" + (info != null ? "seg=" + info.seg : "NULL"));
            }
            
            if (info == null) {
                // GridInfo not loaded yet - throw Loading for retry
                debugLog.log("[computeMarkerCoordinates] GridInfo returned null (segment " + segmentId + ") - throwing Loading");
                throw new haven.Loading("GridInfo not available for segment " + segmentId);
            }

            // Verify that this GridInfo is for the correct segment
            // This is critical for cave transitions where same coordinates exist in both segments
            // info.seg is a long (segment ID), not an object
            if (info.seg != segmentId) {
                debugLog.log("[computeMarkerCoordinates] GridInfo segment mismatch: info.seg=" + info.seg + ", expected segmentId=" + segmentId + " - throwing Loading");
                throw new haven.Loading("GridInfo segment mismatch, expected " + segmentId);
            }

            // Compute segment-local coordinates using vanilla formula:
            // sc = tc + (info.sc - gc) * cmaps
            Coord sc = tc.add(info.sc.sub(gc).mul(MCache.cmaps));
            debugLog.log("[computeMarkerCoordinates] Computed sc=" + sc + " from tc=" + tc + ", info.sc=" + info.sc + ", gc=" + gc + ", segmentId=" + segmentId);
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
     * Computes marker tile coordinates from portal world coordinates (without cached GridInfo).
     * Backward compatibility method.
     *
     * @param coords world coordinates in the target segment
     * @param segmentId target segment ID (for GridInfo lookup)
     * @return tile coordinates for marker in segment-local space
     * @throws haven.Loading if GridInfo not available
     */
    private Coord computeMarkerCoordinates(Coord2d coords, long segmentId) throws haven.Loading {
        return computeMarkerCoordinates(coords, segmentId, null);
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
                debugLog.log("[markerExists] MapFile or markers is null, returning false");
                return false;
            }

            boolean exists = file.markers.stream()
                .filter(m -> m.seg == segmentId)
                .filter(m -> m.nm != null)
                .anyMatch(m -> m.nm.equals(markerName));
            
            debugLog.log("[markerExists] Checking for '" + markerName + "' on segment " + segmentId + ": " + (exists ? "EXISTS" : "NOT FOUND"));
            return exists;
        } catch (Exception e) {
            debugLog.log("[markerExists] ERROR: " + e.getMessage());
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
