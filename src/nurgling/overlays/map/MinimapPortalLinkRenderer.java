package nurgling.overlays.map;

import haven.*;
import nurgling.widgets.NMiniMap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders visual indicators for linked portal markers on the minimap.
 * Shows IN/OUT direction overlays on markers that have a linkUid.
 * - UP arrow for OUT markers (departing from current layer)
 * - DOWN arrow for IN markers (arriving on current layer)
 */
public class MinimapPortalLinkRenderer {

    // Log levels for structured logging (matches SLF4J levels)
    private static final int DEBUG = 0;
    private static final int INFO = 1;
    private static final int WARN = 2;
    private static final int ERROR = 3;

    // Current log level threshold
    private static final int LOG_LEVEL = DEBUG;

    // Direction indicator colors
    private static final Color OUT_COLOR = new Color(255, 200, 0, 200);   // Orange/Yellow for OUT (departing)
    private static final Color IN_COLOR = new Color(0, 200, 255, 200);    // Cyan for IN (arriving)

    // Cache for direction indicator textures
    private static final Map<DirectionKey, Tex> indicatorCache = new HashMap<>();

    // Statistics for logging
    private static int markersRendered = 0;
    private static int indicatorsCreated = 0;

    private static class DirectionKey {
        final boolean isOut;
        final int size;

        DirectionKey(boolean isOut, int size) {
            this.isOut = isOut;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DirectionKey that = (DirectionKey) o;
            return isOut == that.isOut && size == that.size;
        }

        @Override
        public int hashCode() {
            return 31 * (isOut ? 1 : 0) + size;
        }
    }

    /**
     * Structured logging method with log levels.
     * <p>
     * This method prints messages with appropriate log level prefixes.
     * In production, this can be replaced with SLF4J logging.
     * </p>
     *
     * @param level the log level (DEBUG, INFO, WARN, ERROR)
     * @param format the format string
     * @param args the arguments to be formatted
     */
    private static void dlog(int level, String format, Object... args) {
        if (level < LOG_LEVEL) {
            return;
        }
        String levelStr;
        switch (level) {
            case DEBUG: levelStr = "DEBUG"; break;
            case INFO:  levelStr = "INFO";  break;
            case WARN:  levelStr = "WARN";  break;
            case ERROR: levelStr = "ERROR"; break;
            default:    levelStr = "UNKNOWN"; break;
        }
        System.out.printf("[MinimapPortalLinkRenderer] [%s] " + format + "%n", levelStr, args);
    }

    /**
     * Main rendering method called from NMiniMap.drawparts()
     * Renders direction indicators on linked portal markers.
     *
     * @param map The minimap instance
     * @param g   Graphics context
     */
    public static void renderPortalLinkIndicators(NMiniMap map, GOut g) {
        dlog(DEBUG, "renderPortalLinkIndicators called");

        if (map.ui == null || map.ui.gui == null || map.ui.gui.map == null) {
            dlog(DEBUG, "Skipping render: map UI components not available");
            return;
        }

        if (map.dloc == null) {
            dlog(DEBUG, "Skipping render: display location not available");
            return;
        }

        try {
            Coord hsz = map.sz.div(2);

            // Get the display grid array and extent using public getters
            MiniMap.DisplayGrid[] display = map.getDisplay();
            Area dgext = map.getDgext();

            if (display == null || dgext == null) {
                dlog(DEBUG, "Skipping render: display grid or extent not available");
                return;
            }

            int gridCount = 0;
            int markerCount = 0;
            int linkedMarkerCount = 0;

            // Iterate through all display grids
            for (Coord gc : dgext) {
                MiniMap.DisplayGrid disp = display[dgext.ri(gc)];
                if (disp == null) {
                    continue;
                }
                gridCount++;

                // Process markers in this grid
                for (MiniMap.DisplayMarker mark : disp.markers(false)) {
                    if (mark == null || mark.m == null) {
                        continue;
                    }
                    markerCount++;

                    // Check if this is a PMarker with linkUid
                    if (mark.m instanceof MapFile.PMarker) {
                        MapFile.PMarker pmark = (MapFile.PMarker) mark.m;

                        // Only render indicator if marker has a linkUid
                        if (pmark.linkUid != null && pmark.direction != null) {
                            linkedMarkerCount++;
                            dlog(DEBUG, "Rendering indicator for linked marker: UID=%s, direction=%s",
                                 pmark.linkUid, pmark.direction);

                            // Calculate screen position for the marker
                            Coord screenPos = mark.m.tc.sub(map.dloc.tc).div(map.scalef()).add(hsz);

                            // Determine if this is an OUT marker (arrow up) or IN marker (arrow down)
                            boolean isOut = pmark.direction == nurgling.markers.Direction.OUT;
                            String directionStr = isOut ? "OUT" : "IN";

                            // Get or create the direction indicator texture
                            Tex indicatorTex = getDirectionIndicator(isOut, 12);

                            if (indicatorTex != null) {
                                // Draw the indicator overlay slightly offset from the marker center
                                Coord indicatorSize = indicatorTex.sz();
                                Coord indicatorPos = screenPos.sub(indicatorSize.div(2));

                                // Draw the direction indicator
                                g.image(indicatorTex, indicatorPos);
                                markersRendered++;

                                dlog(DEBUG, "Rendered %s indicator at position %s", directionStr, indicatorPos);
                            } else {
                                dlog(WARN, "Failed to get direction indicator texture for %s", directionStr);
                            }
                        }
                    }
                }
            }

            dlog(DEBUG, "Render complete: grids=%d, markers=%d, linked=%d, rendered=%d",
                 gridCount, markerCount, linkedMarkerCount, markersRendered);

        } catch (Exception e) {
            // Log errors to prevent disrupting minimap rendering
            dlog(ERROR, "Error during portal link rendering: %s", e.getMessage());
            dlog(DEBUG, "Stack trace: %s", getStackTraceAsString(e));
        }
    }

    /**
     * Helper method to convert stack trace to string for logging.
     */
    private static String getStackTraceAsString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get or create a direction indicator texture.
     *
     * @param isOut true for OUT (arrow up), false for IN (arrow down)
     * @param size  size of the indicator in pixels
     * @return Tex containing the direction indicator
     */
    private static Tex getDirectionIndicator(boolean isOut, int size) {
        DirectionKey key = new DirectionKey(isOut, size);

        Tex cached = indicatorCache.get(key);
        if (cached != null) {
            dlog(DEBUG, "Cache hit for %s indicator (size=%d)", isOut ? "OUT" : "IN", size);
            return cached;
        }

        dlog(DEBUG, "Cache miss for %s indicator (size=%d), creating new", isOut ? "OUT" : "IN", size);

        // Generate new indicator
        BufferedImage img = renderDirectionIndicator(isOut, size);
        Tex tex = new TexI(img);

        indicatorCache.put(key, tex);
        indicatorsCreated++;
        dlog(INFO, "Created new %s indicator texture (size=%d), total created: %d",
             isOut ? "OUT" : "IN", size, indicatorsCreated);
        return tex;
    }

    /**
     * Render a direction indicator image.
     * Creates a simple arrow pointing up (OUT) or down (IN).
     *
     * @param isOut true for OUT arrow (up), false for IN arrow (down)
     * @param size  size of the indicator in pixels
     * @return BufferedImage containing the rendered arrow
     */
    private static BufferedImage renderDirectionIndicator(boolean isOut, int size) {
        WritableRaster buf = PUtils.imgraster(new Coord(size, size));

        // Clear to transparent
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                buf.setSample(x, y, 0, 0);    // R
                buf.setSample(x, y, 1, 0);    // G
                buf.setSample(x, y, 2, 0);    // B
                buf.setSample(x, y, 3, 0);    // A
            }
        }

        Color color = isOut ? OUT_COLOR : IN_COLOR;

        // Draw arrow shape
        // For OUT (up): triangle pointing up with a stem
        // For IN (down): triangle pointing down with a stem
        int centerX = size / 2;
        int arrowHeadSize = size / 3;
        int stemWidth = Math.max(2, size / 6);

        if (isOut) {
            // OUT arrow (pointing up)
            // Draw arrow head (triangle)
            for (int y = 1; y < size / 2; y++) {
                int halfWidth = arrowHeadSize - (y - 1);
                if (halfWidth < 1) halfWidth = 1;
                for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
                    if (x >= 0 && x < size) {
                        buf.setSample(x, y, 0, color.getRed());
                        buf.setSample(x, y, 1, color.getGreen());
                        buf.setSample(x, y, 2, color.getBlue());
                        buf.setSample(x, y, 3, color.getAlpha());
                    }
                }
            }

            // Draw stem
            for (int y = size / 2; y < size - 1; y++) {
                for (int x = centerX - stemWidth / 2; x <= centerX + stemWidth / 2; x++) {
                    if (x >= 0 && x < size) {
                        buf.setSample(x, y, 0, color.getRed());
                        buf.setSample(x, y, 1, color.getGreen());
                        buf.setSample(x, y, 2, color.getBlue());
                        buf.setSample(x, y, 3, color.getAlpha());
                    }
                }
            }
        } else {
            // IN arrow (pointing down)
            // Draw stem at top
            for (int y = 1; y < size / 2; y++) {
                for (int x = centerX - stemWidth / 2; x <= centerX + stemWidth / 2; x++) {
                    if (x >= 0 && x < size) {
                        buf.setSample(x, y, 0, color.getRed());
                        buf.setSample(x, y, 1, color.getGreen());
                        buf.setSample(x, y, 2, color.getBlue());
                        buf.setSample(x, y, 3, color.getAlpha());
                    }
                }
            }

            // Draw arrow head (triangle) at bottom
            for (int y = size / 2; y < size - 1; y++) {
                int halfWidth = (y - size / 2) + 1;
                if (halfWidth > arrowHeadSize) halfWidth = arrowHeadSize;
                for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
                    if (x >= 0 && x < size) {
                        buf.setSample(x, y, 0, color.getRed());
                        buf.setSample(x, y, 1, color.getGreen());
                        buf.setSample(x, y, 2, color.getBlue());
                        buf.setSample(x, y, 3, color.getAlpha());
                    }
                }
            }
        }

        return PUtils.rasterimg(buf);
    }

    /**
     * Clear the indicator texture cache.
     * Call when needed to free resources.
     */
    public static void clearCache() {
        indicatorCache.clear();
    }
}
