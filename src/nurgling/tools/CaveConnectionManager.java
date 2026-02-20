package nurgling.tools;

import haven.*;
import nurgling.NUtils;
import org.json.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cave connections between surface and underground maps.
 * Automatically links cave entrances/exits and renames them with shared identifiers.
 */
public class CaveConnectionManager {
    private static final String SAVE_FILE = "cave_connections.json";
    private static final int ID_LENGTH = 6;
    private static final String ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    private static CaveConnectionManager instance;
    private final Map<String, CaveConnection> connections = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private boolean isDirty = false;
    private long lastSaveTime = 0;
    
    /**
     * Represents a connection between two cave passages (surface and underground)
     */
    public static class CaveConnection {
        public final String id;           // Unique 6-char identifier
        public final long surfaceGobId;   // Gob ID on surface map
        public final long undergroundGobId; // Gob ID on underground map
        public final long surfaceSegId;   // Segment ID on surface
        public final long undergroundSegId; // Segment ID underground
        public final haven.Coord surfaceTc; // Tile coordinates on surface
        public final haven.Coord undergroundTc; // Tile coordinates underground
        public final long createdTime;
        
        public CaveConnection(String id, long surfaceGobId, long undergroundGobId, 
                              long surfaceSegId, long undergroundSegId,
                              haven.Coord surfaceTc, haven.Coord undergroundTc) {
            this.id = id;
            this.surfaceGobId = surfaceGobId;
            this.undergroundGobId = undergroundGobId;
            this.surfaceSegId = surfaceSegId;
            this.undergroundSegId = undergroundSegId;
            this.surfaceTc = surfaceTc;
            this.undergroundTc = undergroundTc;
            this.createdTime = System.currentTimeMillis();
        }
    }

    // Track last segment for cave connection detection
    private Long lastSegmentId = null;
    private Boolean lastWasUnderground = null;
    private haven.MapFile.SMarker lastCaveMarker = null;  // Last cave marker used
    private long lastTransitionTime = 0;
    
    // Track known underground segment IDs
    private final Set<Long> knownUndergroundSegments = new HashSet<>();
    
    // Track transition direction (first transition = going underground)
    private boolean expectUnderground = true;

    private CaveConnectionManager() {
        load();
        // Don't rename existing markers - keep their names until we detect actual usage
    }
    
    public static CaveConnectionManager getInstance() {
        if (instance == null) {
            instance = new CaveConnectionManager();
        }
        return instance;
    }
    
    /**
     * Generate a random 6-character ID
     */
    public String generateId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }
    
    /**
     * Check if player is currently underground (in a cave)
     * Uses segment ID tracking - once we detect a transition, we remember which segment is underground
     */
    public boolean isPlayerUnderground() {
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.mmap == null || gui.mmap.sessloc == null) {
                return false;
            }
            
            long segId = gui.mmap.sessloc.seg.id;
            
            // If we've previously detected this segment as underground, return true
            if (knownUndergroundSegments.contains(segId)) {
                System.out.println("[CaveConnection] isPlayerUnderground: segId=" + segId + " (known underground) = true");
                return true;
            }
            
            // If we have a known surface segment and this is different, it might be underground
            if (lastSegmentId != null && lastWasUnderground != null && !lastWasUnderground) {
                // Last segment was surface, this one is different - could be underground
                if (segId != lastSegmentId) {
                    System.out.println("[CaveConnection] isPlayerUnderground: segId=" + segId + " (different from surface " + lastSegmentId + ") = true");
                    return true;
                }
            }
            
            System.out.println("[CaveConnection] isPlayerUnderground: segId=" + segId + " = false");
            return false;
            
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error checking underground: " + e);
            return false;
        }
    }
    
    /**
     * Mark a segment as underground
     */
    public void markSegmentAsUnderground(long segId) {
        if (knownUndergroundSegments.add(segId)) {
            System.out.println("[CaveConnection] Marked segment " + segId + " as underground");
        }
    }
    
    /**
     * Mark a segment as surface
     */
    public void markSegmentAsSurface(long segId) {
        knownUndergroundSegments.remove(segId);
        System.out.println("[CaveConnection] Marked segment " + segId + " as surface");
    }
    
    /**
     * Get the current map file
     */
    private haven.MapFile getMapFile() {
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui != null && gui.mapfile != null) {
                return gui.mapfile.file;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Get current segment ID
     */
    private Long getCurrentSegmentId() {
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui != null && gui.mmap != null && gui.mmap.sessloc != null) {
                return gui.mmap.sessloc.seg.id;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Find an unconnected cave passage marker on the current map
     * Returns the marker if found, null otherwise
     */
    public haven.MapFile.SMarker findUnconnectedCaveMarker(boolean underground) {
        try {
            haven.MapFile file = getMapFile();
            if (file == null) return null;
            
            Long currentSegId = getCurrentSegmentId();
            if (currentSegId == null) return null;
            
            synchronized (file.lock) {
                for (haven.MapFile.Marker marker : file.markers) {
                    if (!(marker instanceof haven.MapFile.SMarker)) continue;
                    
                    haven.MapFile.SMarker sm = (haven.MapFile.SMarker) marker;
                    
                    // Check if this is a cave marker
                    if (!isCaveMarker(sm)) continue;
                    
                    // Check if it's on current segment
                    if (sm.seg != currentSegId) continue;
                    
                    // Check if already connected
                    if (isMarkerConnected(sm)) continue;
                    
                    return sm;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Check if a marker is a cave passage marker
     */
    private boolean isCaveMarker(haven.MapFile.SMarker marker) {
        if (marker == null || marker.res == null) return false;
        String resName = marker.res.name;
        return resName.equals("gfx/hud/mmap/cave");
    }
    
    /**
     * Check if a marker is already connected to another cave
     * A marker is considered connected if its name matches the pattern "Cave XXXXXX In" or "Cave XXXXXX Out"
     */
    private boolean isMarkerConnected(haven.MapFile.SMarker marker) {
        if (marker == null || marker.nm == null) return false;
        
        // Check if marker name matches the pattern "Cave XXXXXX In" or "Cave XXXXXX Out"
        // where XXXXXX is a 6-character alphanumeric ID
        return marker.nm.matches("Cave [A-Za-z0-9]{6} (In|Out)");
    }
    
    /**
     * Get connection for a specific marker
     */
    public CaveConnection getConnectionForMarker(haven.MapFile.SMarker marker) {
        for (CaveConnection conn : connections.values()) {
            if (conn.surfaceGobId == marker.oid || conn.undergroundGobId == marker.oid) {
                return conn;
            }
        }
        return null;
    }
    
    /**
     * Create a new cave connection and rename markers
     */
    public void createConnection(haven.MapFile.SMarker surfaceMarker, haven.MapFile.SMarker undergroundMarker) {
        try {
            String id = generateId();
            CaveConnection conn = new CaveConnection(
                id,
                surfaceMarker.oid,
                undergroundMarker.oid,
                surfaceMarker.seg,
                undergroundMarker.seg,
                surfaceMarker.tc,
                undergroundMarker.tc
            );

            connections.put(id, conn);
            isDirty = true;
            
            // Rename markers
            renameMarker(surfaceMarker, "Cave " + id + " In");
            renameMarker(undergroundMarker, "Cave " + id + " Out");
            
            System.out.println("[CaveConnection] Created connection: " + id);
            System.out.println("[CaveConnection]   Surface: " + surfaceMarker.nm + " at " + surfaceMarker.tc);
            System.out.println("[CaveConnection]   Underground: " + undergroundMarker.nm + " at " + undergroundMarker.tc);
            
            save();
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error creating connection: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Rename a marker
     */
    private void renameMarker(haven.MapFile.SMarker marker, String newName) {
        try {
            haven.MapFile file = getMapFile();
            if (file == null) return;
            
            synchronized (file.lock) {
                if (!marker.nm.equals(newName)) {
                    marker.nm = newName;
                    file.update(marker);
                    System.out.println("[CaveConnection] Renamed marker to: " + newName);
                }
            }
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error renaming marker: " + e);
        }
    }
    
    /**
     * Check for new cave connections when player changes maps
     */
    public void checkForNewConnections() {
        try {
            haven.MapFile file = getMapFile();
            if (file == null) return;
            
            boolean playerUnderground = isPlayerUnderground();
            Long currentSegId = getCurrentSegmentId();
            if (currentSegId == null) return;
            
            // Find unconnected cave markers on current map
            haven.MapFile.SMarker unconnectedMarker = findUnconnectedCaveMarker(playerUnderground);
            if (unconnectedMarker == null) return;
            
            // If player is underground, look for matching surface marker
            // If player is on surface, look for matching underground marker
            // This is a simplified heuristic - in practice you'd need more sophisticated matching
            
            System.out.println("[CaveConnection] Found unconnected cave marker: " + unconnectedMarker.nm);
            System.out.println("[CaveConnection]   Player underground: " + playerUnderground);
            System.out.println("[CaveConnection]   Segment: " + currentSegId);
            
            // For now, just mark it as a potential connection
            // Full implementation would need to track player movement between maps
            
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error checking connections: " + e);
        }
    }
    
    /**
     * Called when player transitions between levels (surface <-> underground)
     * @param fromSegId Segment ID player came FROM
     * @param toSegId Segment ID player went TO
     * @param wasUnderground Ignored - we determine direction automatically
     * @param playerPos Player position at moment of transition (for finding correct cave markers)
     */
    public void onLevelTransition(long fromSegId, long toSegId, boolean wasUnderground, haven.Coord playerPos) {
        logCave("[CaveConnection] >>> onLevelTransition() called: from=" + fromSegId + " to=" + toSegId);
        logCave("[CaveConnection] Player transition position: " + playerPos);
        try {
            logCave("[CaveConnection] Level transition: fromSegId=" + fromSegId + " -> toSegId=" + toSegId);

            // Determine direction based on known segments
            boolean fromIsUnderground = knownUndergroundSegments.contains(fromSegId);
            boolean toIsUnderground = knownUndergroundSegments.contains(toSegId);
            logCave("[CaveConnection] fromIsUnderground=" + fromIsUnderground + ", toIsUnderground=" + toIsUnderground);

            boolean goingUnderground;
            if (fromIsUnderground && !toIsUnderground) {
                goingUnderground = false;  // Coming FROM underground = going to surface
                logCave("[CaveConnection] Direction: Underground -> Surface (known)");
            } else if (!fromIsUnderground && toIsUnderground) {
                goingUnderground = true;   // Going TO known underground segment
                logCave("[CaveConnection] Direction: Surface -> Underground (known)");
            } else {
                // Unknown segments - use alternating pattern as fallback
                goingUnderground = expectUnderground;
                logCave("[CaveConnection] Direction: " + (goingUnderground ? "Surface -> Underground" : "Underground -> Surface") + " (guessed, expectUnderground=" + expectUnderground + ")");
            }

            // Mark segments appropriately
            if (goingUnderground) {
                markSegmentAsUnderground(toSegId);
                markSegmentAsSurface(fromSegId);
            } else {
                markSegmentAsUnderground(fromSegId);
                markSegmentAsSurface(toSegId);
            }

            // Don't toggle expectUnderground if we used known segments
            if (!fromIsUnderground && !toIsUnderground) {
                expectUnderground = !expectUnderground;
            }

            // Find the UNCONNECTED cave marker closest to player's transition position on the previous segment
            logCave("[CaveConnection] Searching for UNCONNECTED marker on segment " + fromSegId);
            haven.MapFile.SMarker markerOnFromSeg = findNearestUnconnectedCaveMarker(fromSegId, playerPos);
            logCave("[CaveConnection] Found unconnected marker on fromSeg: " + (markerOnFromSeg != null ? markerOnFromSeg.nm + " at " + markerOnFromSeg.tc : "null"));

            if (markerOnFromSeg != null) {
                logCave("[CaveConnection] Found unconnected cave marker on previous segment: " + markerOnFromSeg.nm + " at " + markerOnFromSeg.tc);

                // Wait for the map to load and markers to be created
                logCave("[CaveConnection] Waiting for markers to be created on new segment...");
                try { Thread.sleep(2000); } catch(Exception e) {}

                // Find MATCHING cave marker on the new segment (based on coordinates, not player position)
                logCave("[CaveConnection] Searching for MATCHING marker on segment " + toSegId);
                haven.MapFile.SMarker markerOnToSeg = findMatchingCaveMarker(toSegId, markerOnFromSeg);
                logCave("[CaveConnection] Found matching marker on toSeg: " + (markerOnToSeg != null ? markerOnToSeg.nm + " at " + markerOnToSeg.tc : "null"));

                if (markerOnToSeg != null) {
                    logCave("[CaveConnection] Found cave marker on new segment: " + markerOnToSeg.nm + " at " + markerOnToSeg.tc);

                    // Create connection between these two markers
                    logCave("[CaveConnection] Creating connection...");
                    createConnectionWithNaming(markerOnFromSeg, markerOnToSeg, !goingUnderground);
                } else {
                    logCave("[CaveConnection] No matching cave marker found on segment " + toSegId);
                    logCave("[CaveConnection] Will retry on next transition");
                }
            } else {
                logCave("[CaveConnection] No cave marker found on segment " + fromSegId);
            }

        } catch (Exception e) {
            logCave("[CaveConnection] Error on level transition: " + e);
            e.printStackTrace();
        }
    }
    
    private void logCave(String msg) {
        System.out.println(msg);
        try {
            java.io.PrintWriter log = new java.io.PrintWriter(new java.io.FileWriter("nurgling_markers.log", true));
            log.println(msg);
            log.close();
        } catch(Exception e) {}
    }
    
    /**
     * Find the cave marker closest to player's current position on a specific segment
     */
    private haven.MapFile.SMarker findNearestCaveMarker(long segmentId) {
        try {
            GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.mmap == null || gui.mmap.sessloc == null) return null;
            
            haven.MapFile file = getMapFile();
            if (file == null) return null;
            
            // Player's current position in tile coordinates (segment-relative)
            Coord playerPos = gui.mmap.sessloc.tc;
            
            haven.MapFile.SMarker nearest = null;
            double nearestDist = Double.MAX_VALUE;
            
            synchronized (file.lock) {
                for (haven.MapFile.Marker m : file.markers) {
                    if (!(m instanceof haven.MapFile.SMarker)) continue;
                    haven.MapFile.SMarker sm = (haven.MapFile.SMarker) m;
                    
                    // Check if this is a cave marker on the target segment
                    if (sm.seg == segmentId && isCaveMarker(sm)) {
                        double dist = sm.tc.dist(playerPos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = sm;
                        }
                    }
                }
            }
            
            if (nearest != null) {
                System.out.println("[CaveConnection] Nearest cave marker: " + nearest.nm + " at dist=" + nearestDist);
            }
            
            return nearest;
            
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error finding nearest marker: " + e);
            return null;
        }
    }
    
    /**
     * Find nearest unconnected cave marker on a segment
     * Returns the marker closest to the specified position
     * @param segmentId Segment to search on
     * @param pos Position to search near (in segment-relative coordinates)
     */
    private haven.MapFile.SMarker findNearestUnconnectedCaveMarker(long segmentId, haven.Coord pos) {
        try {
            haven.MapFile file = getMapFile();
            if (file == null) {
                logCave("[CaveConnection] MapFile is null");
                return null;
            }
            
            logCave("[CaveConnection] Searching for unconnected markers on segment " + segmentId + " near " + pos);
            
            haven.MapFile.SMarker nearest = null;
            double nearestDist = Double.MAX_VALUE;
            
            synchronized (file.lock) {
                for (haven.MapFile.Marker m : file.markers) {
                    if (!(m instanceof haven.MapFile.SMarker)) continue;
                    haven.MapFile.SMarker sm = (haven.MapFile.SMarker) m;
                    
                    // Check if this is a cave marker on the target segment and not connected
                    if (sm.seg == segmentId && isCaveMarker(sm) && !isMarkerConnected(sm)) {
                        double dist = sm.tc.dist(pos);
                        logCave("[CaveConnection]   Checking marker at " + sm.tc + ", dist=" + dist + ", name=" + sm.nm);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = sm;
                        }
                    }
                }
            }
            
            if (nearest != null) {
                logCave("[CaveConnection] Nearest unconnected cave marker: " + nearest.nm + " at " + nearest.tc + ", dist=" + nearestDist);
            } else {
                logCave("[CaveConnection] No unconnected markers found on segment " + segmentId);
            }
            
            return nearest;
            
        } catch (Exception e) {
            logCave("[CaveConnection] Error finding nearest unconnected marker: " + e);
            return null;
        }
    }
    
    /**
     * Find the matching unconnected cave marker on another segment
     * Uses the fact that connected caves have similar coordinates (caves go vertically)
     * @param segmentId Segment to search on
     * @param referenceMarker The marker on the other segment to match with
     */
    private haven.MapFile.SMarker findMatchingCaveMarker(long segmentId, haven.MapFile.SMarker referenceMarker) {
        try {
            haven.MapFile file = getMapFile();
            if (file == null) {
                logCave("[CaveConnection] MapFile is null");
                return null;
            }
            
            logCave("[CaveConnection] Searching for matching marker on segment " + segmentId + " for reference " + referenceMarker.tc);
            
            haven.MapFile.SMarker best = null;
            double bestScore = Double.MAX_VALUE;
            
            synchronized (file.lock) {
                for (haven.MapFile.Marker m : file.markers) {
                    if (!(m instanceof haven.MapFile.SMarker)) continue;
                    haven.MapFile.SMarker sm = (haven.MapFile.SMarker) m;
                    
                    // Check if this is a cave marker on the target segment and not connected
                    if (sm.seg == segmentId && isCaveMarker(sm) && !isMarkerConnected(sm)) {
                        // Score based on how close the coordinates are (caves go vertically)
                        // Prefer same X coordinate, then closest Y
                        double dx = Math.abs(sm.tc.x - referenceMarker.tc.x);
                        double dy = Math.abs(sm.tc.y - referenceMarker.tc.y);
                        
                        // Weight X coordinate more heavily (caves are usually vertical)
                        double score = dx * 2.0 + dy;
                        
                        logCave("[CaveConnection]   Checking marker at " + sm.tc + ", dx=" + dx + ", dy=" + dy + ", score=" + score);
                        
                        if (score < bestScore) {
                            bestScore = score;
                            best = sm;
                        }
                    }
                }
            }
            
            if (best != null) {
                logCave("[CaveConnection] Best matching marker: " + best.nm + " at " + best.tc + ", score=" + bestScore);
            } else {
                logCave("[CaveConnection] No matching markers found on segment " + segmentId);
            }
            
            return best;
            
        } catch (Exception e) {
            logCave("[CaveConnection] Error finding matching marker: " + e);
            return null;
        }
    }
    
    /**
     * Create a connection between two cave markers with proper In/Out naming
     * @param fromMarker Marker on the segment player came FROM
     * @param toMarker Marker on the segment player went TO
     * @param fromWasUnderground true if the 'from' segment was underground
     */
    private void createConnectionWithNaming(haven.MapFile.SMarker fromMarker,
                                            haven.MapFile.SMarker toMarker,
                                            boolean fromWasUnderground) {
        try {
            String id = generateId();

            // Determine which is In and which is Out
            // In = entrance from surface TO underground (surface marker)
            // Out = exit from underground TO surface (underground marker)
            haven.MapFile.SMarker inMarker = fromWasUnderground ? toMarker : fromMarker;  // Surface marker
            haven.MapFile.SMarker outMarker = fromWasUnderground ? fromMarker : toMarker; // Underground marker

            CaveConnection conn = new CaveConnection(
                id,
                inMarker.oid,
                outMarker.oid,
                inMarker.seg,
                outMarker.seg,
                inMarker.tc,
                outMarker.tc
            );

            connections.put(id, conn);
            isDirty = true;

            // Rename markers
            renameMarker(inMarker, "Cave " + id + " In");
            renameMarker(outMarker, "Cave " + id + " Out");

            logCave("[CaveConnection] *** Created connection: " + id + " ***");
            logCave("[CaveConnection]   Cave " + id + " In (surface): " + inMarker.nm + " at " + inMarker.tc + " on seg " + inMarker.seg);
            logCave("[CaveConnection]   Cave " + id + " Out (underground): " + outMarker.nm + " at " + outMarker.tc + " on seg " + outMarker.seg);

            // Save immediately after creating each connection
            save();
        } catch (Exception e) {
            logCave("[CaveConnection] Error creating connection: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Called when a cave marker is created
     */
    public void onCaveMarkerCreated(haven.MapFile.SMarker marker) {
        try {
            System.out.println("[CaveConnection] Cave marker created: " + marker.nm + " at " + marker.tc);
            
            // Check if this marker should be connected to another
            checkForNewConnections();
            
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error on marker created: " + e);
        }
    }
    
    /**
     * Called when player uses a cave passage (transitions between maps)
     */
    public void onCavePassageUsed(haven.MapFile.SMarker marker) {
        try {
            boolean wasUnderground = isPlayerUnderground();
            
            // Wait a moment for the map transition to complete
            Thread.sleep(500);
            
            boolean isUnderground = isPlayerUnderground();
            
            // If player changed levels, this is a real cave connection
            if (wasUnderground != isUnderground) {
                System.out.println("[CaveConnection] Player transitioned between levels!");
                System.out.println("[CaveConnection]   From: " + (wasUnderground ? "underground" : "surface"));
                System.out.println("[CaveConnection]   To: " + (isUnderground ? "underground" : "surface"));
                System.out.println("[CaveConnection]   Marker: " + marker.nm);
                
                // Store this as a potential connection point
                // Full implementation would match with the other end
            }
            
        } catch (Exception e) {
            System.err.println("[CaveConnection] Error on passage use: " + e);
        }
    }
    
    /**
     * Load connections from file
     */
    @SuppressWarnings("unchecked")
    public void load() {
        try {
            File file = new File(SAVE_FILE);
            if (!file.exists()) return;

            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject json = new JSONObject(content);

            JSONArray arr = json.optJSONArray("connections");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    // Load coordinates if available (new format)
                    haven.Coord surfaceTc = null;
                    haven.Coord undergroundTc = null;
                    if (obj.has("surfaceTcX")) {
                        surfaceTc = new haven.Coord(obj.getInt("surfaceTcX"), obj.getInt("surfaceTcY"));
                    }
                    if (obj.has("undergroundTcX")) {
                        undergroundTc = new haven.Coord(obj.getInt("undergroundTcX"), obj.getInt("undergroundTcY"));
                    }
                    
                    CaveConnection conn = new CaveConnection(
                        obj.getString("id"),
                        obj.getLong("surfaceGobId"),
                        obj.getLong("undergroundGobId"),
                        obj.getLong("surfaceSegId"),
                        obj.getLong("undergroundSegId"),
                        surfaceTc,
                        undergroundTc
                    );
                    connections.put(conn.id, conn);
                }
            }

            logCave("[CaveConnection] Loaded " + connections.size() + " connections");

        } catch (Exception e) {
            logCave("[CaveConnection] Error loading: " + e);
        }
    }

    /**
     * Save connections to file
     */
    public void save() {
        if (!isDirty) return;

        try {
            JSONArray arr = new JSONArray();
            for (CaveConnection conn : connections.values()) {
                JSONObject obj = new JSONObject();
                obj.put("id", conn.id);
                obj.put("surfaceGobId", conn.surfaceGobId);
                obj.put("undergroundGobId", conn.undergroundGobId);
                obj.put("surfaceSegId", conn.surfaceSegId);
                obj.put("undergroundSegId", conn.undergroundSegId);
                obj.put("createdTime", conn.createdTime);
                // Save coordinates
                if (conn.surfaceTc != null) {
                    obj.put("surfaceTcX", conn.surfaceTc.x);
                    obj.put("surfaceTcY", conn.surfaceTc.y);
                }
                if (conn.undergroundTc != null) {
                    obj.put("undergroundTcX", conn.undergroundTc.x);
                    obj.put("undergroundTcY", conn.undergroundTc.y);
                }
                arr.put(obj);
            }

            JSONObject json = new JSONObject();
            json.put("connections", arr);

            Files.write(Paths.get(SAVE_FILE), json.toString(2).getBytes());

            isDirty = false;
            lastSaveTime = System.currentTimeMillis();

            logCave("[CaveConnection] Saved " + connections.size() + " connections");

        } catch (Exception e) {
            logCave("[CaveConnection] Error saving: " + e);
        }
    }
    
    /**
     * Get all connections
     */
    public Collection<CaveConnection> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }
    
    /**
     * Remove a connection
     */
    public void removeConnection(String id) {
        connections.remove(id);
        isDirty = true;
        save();
    }
}
