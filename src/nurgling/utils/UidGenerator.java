/*
 * Copyright (C) 2024 Nurgling Project
 *
 * This file is part of the Nurgling mod for Haven & Hearth.
 *
 * This file is subject to the terms of the GNU Lesser General Public License,
 * version 3, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * A copy of the GNU Lesser General Public License is distributed
 * with this source code. If not, see <https://www.gnu.org/licenses/>.
 */

package nurgling.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Utility class for generating unique 6-character alphanumeric UIDs.
 * <p>
 * UIDs are composed of characters from the set: 0-9, a-z, A-Z (62 possible characters).
 * This provides 62^6 ≈ 56.8 billion possible combinations.
 * </p>
 */
public class UidGenerator {

    private static final String ALPHANUMERIC_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int UID_LENGTH = 6;
    private static final Random RANDOM = new SecureRandom();

    /**
     * Generates a unique 6-character alphanumeric UID.
     *
     * @return a random string of length 6 containing characters from [0-9a-zA-Z]
     */
    public static String generateUid() {
        StringBuilder sb = new StringBuilder(UID_LENGTH);
        for (int i = 0; i < UID_LENGTH; i++) {
            int index = RANDOM.nextInt(ALPHANUMERIC_CHARS.length());
            sb.append(ALPHANUMERIC_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private UidGenerator() {
        // Utility class, not meant to be instantiated
    }
}
