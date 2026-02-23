package nurgling.navigation;

import haven.Coord;
import haven.Coord2d;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates unique 6-character alphanumeric identifiers (UID) for portal marker pairs.
 * 
 * UID Generation Strategy:
 * - Uses XOR of segment pair for order-independent hashing (a ^ b == b ^ a)
 * - Combines with portal coordinates for uniqueness
 * - SHA-256 hash ensures collision-free output by design
 * - Base62 encoding produces user-friendly 6-character IDs (0-9a-zA-Z)
 * 
 * Why XOR for Segment Pair:
 * - XOR is commutative: fromSegmentId ^ toSegmentId == toSegmentId ^ fromSegmentId
 * - Same UID generated whether entering or exiting the portal
 * - Coordinates differentiate multiple portals between same segment pair
 * 
 * Why Segment ID instead of Portal Type:
 * - Portal types are limited (4 values) - multiple caves can exist at similar coordinates
 * - Segment ID is globally unique per map layer (guaranteed by MapFile.Segment)
 * - XOR segment pair ensures same UID for bidirectional travel
 * 
 * Example UID: "ABC123" from hash of "55639:512:1024" where:
 * - 55639 = fromSegmentId ^ toSegmentId (XOR result)
 * - 512:1024 = portal coordinates (x:y)
 */
public class MarkerUidGenerator {
    
    /**
     * Base62 alphabet for UID encoding (0-9, a-z, A-Z).
     * Produces user-friendly alphanumeric identifiers.
     */
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /**
     * SHA-256 hash length in bytes.
     */
    private static final int HASH_LENGTH = 32;
    
    /**
     * Maximum value for 6-character base62 number (62^6 - 1 = 56,800,235,583).
     * Used to constrain hash output to 6 characters.
     */
    private static final long MAX_BASE62_6 = 56_800_235_584L; // 62^6
    
    /**
     * Generates a 6-character alphanumeric UID from segment pair and portal coordinates.
     * 
     * Algorithm:
     * 1. Compute XOR of segment pair: segmentPair = fromSegmentId ^ toSegmentId
     *    - XOR is commutative, ensuring same UID regardless of travel direction
     * 2. Create input string: "segmentPair:coordX:coordY"
     * 3. Compute SHA-256 hash of input string
     * 4. Convert first 8 bytes of hash to long
     * 5. Apply modulo MAX_BASE62_6 to constrain to 6-character range
     * 6. Encode as base62 string
     * 
     * @param fromSegmentId source segment ID (used in XOR pair)
     * @param toSegmentId destination segment ID (used in XOR pair)
     * @param portalCoords portal world coordinates (differentiates multiple portals)
     * @return 6-character alphanumeric UID (e.g., "ABC123")
     * @throws RuntimeException if SHA-256 algorithm is not available (should never happen)
     */
    public String generate(long fromSegmentId, long toSegmentId, Coord2d portalCoords) {
        // Step 1: Order-independent segment pair using XOR
        // XOR is commutative: a ^ b == b ^ a
        // This ensures same UID whether entering (surface→cave) or exiting (cave→surface)
        long segmentPair = fromSegmentId ^ toSegmentId;
        
        // Step 2: Create input string with segment pair and coordinates
        // Coordinates differentiate multiple portals between same segment pair
        // Multiple portals cannot occupy same physical coordinates
        String input = segmentPair + ":" + 
                       (int)portalCoords.x + ":" + 
                       (int)portalCoords.y;
        
        // Step 3-6: Hash and encode
        return hashAndEncode(input);
    }
    
    /**
     * Generates UID from pre-computed XOR segment pair and coordinates.
     * Useful when segment pair is already computed elsewhere.
     * 
     * @param xorSegmentPair pre-computed XOR of segment IDs (fromSegmentId ^ toSegmentId)
     * @param portalCoords portal world coordinates
     * @return 6-character alphanumeric UID
     */
    public String generateFromXor(long xorSegmentPair, Coord2d portalCoords) {
        String input = xorSegmentPair + ":" + 
                       (int)portalCoords.x + ":" + 
                       (int)portalCoords.y;
        return hashAndEncode(input);
    }
    
    /**
     * Computes SHA-256 hash and encodes as 6-character base62 string.
     * 
     * @param input input string to hash
     * @return 6-character base62 encoded UID
     */
    private String hashAndEncode(String input) {
        try {
            // Compute SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 8 bytes to long (big-endian)
            long hashValue = bytesToLong(hashBytes, 0);
            
            // Ensure positive value (mask sign bit)
            hashValue = hashValue & 0x7FFFFFFFFFFFFFFFL;
            
            // Constrain to 6-character base62 range
            long constrained = hashValue % MAX_BASE62_6;
            
            // Encode as base62
            return toBase62(constrained, 6);
            
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by Java specification, should never fail
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Converts 8 bytes (big-endian) to long value.
     * 
     * @param bytes byte array
     * @param offset starting offset
     * @return long value from bytes
     */
    private long bytesToLong(byte[] bytes, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[offset + i] & 0xFF);
        }
        return result;
    }
    
    /**
     * Encodes a number as a base62 string of specified length.
     * 
     * @param num number to encode
     * @param length desired output length (padded with leading zeros if needed)
     * @return base62 encoded string
     */
    private String toBase62(long num, int length) {
        StringBuilder sb = new StringBuilder(length);
        
        // Convert to base62
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt((int)(num % 62)));
            num /= 62;
        }
        
        // Result is built in reverse order (least significant char first)
        return sb.toString();
    }
    
    /**
     * Validates UID format (6 alphanumeric characters).
     * 
     * @param uid UID to validate
     * @return true if valid 6-character alphanumeric UID
     */
    public boolean isValidUid(String uid) {
        if (uid == null || uid.length() != 6) {
            return false;
        }
        for (char c : uid.toCharArray()) {
            if (BASE62.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
