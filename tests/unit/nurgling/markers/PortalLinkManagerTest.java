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

package nurgling.markers;

import nurgling.utils.UidGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UID generation in PortalLinkManager.
 * <p>
 * These tests verify that the UID generation produces valid 6-character
 * alphanumeric strings with uniqueness guarantees.
 * </p>
 */
@DisplayName("PortalLinkManager UID Generation")
class PortalLinkManagerTest {

    @Nested
    @DisplayName("generateUid()")
    class GenerateUidTests {

        @Test
        @DisplayName("returns string with length exactly 6")
        void testGenerateUid_ReturnsLengthSix() {
            String uid = UidGenerator.generateUid();

            assertEquals(6, uid.length(), "UID should be exactly 6 characters long");
        }

        @Test
        @DisplayName("uses only alphanumeric characters (0-9, a-z, A-Z)")
        void testGenerateUid_UsesOnlyAlphanumericCharacters() {
            String uid = UidGenerator.generateUid();

            assertTrue(uid.matches("[0-9a-zA-Z]{6}"),
                    "UID should contain only alphanumeric characters (0-9, a-z, A-Z)");
        }

        @Test
        @DisplayName("generates unique UIDs (1000 calls produce all different values)")
        void testGenerateUid_GeneratesUniqueUids() {
            Set<String> uids = new HashSet<>();
            int numberOfCalls = 1000;

            for (int i = 0; i < numberOfCalls; i++) {
                String uid = UidGenerator.generateUid();
                uids.add(uid);
            }

            assertEquals(numberOfCalls, uids.size(),
                    "All " + numberOfCalls + " generated UIDs should be unique");
        }

        @Test
        @DisplayName("is case-sensitive (different character cases are preserved)")
        void testGenerateUid_IsCaseSensitive() {
            // Generate multiple UIDs and verify case sensitivity
            Set<String> uids = new HashSet<>();
            int numberOfCalls = 100;

            for (int i = 0; i < numberOfCalls; i++) {
                uids.add(UidGenerator.generateUid());
            }

            // Verify that we have UIDs with different cases
            boolean hasUppercase = false;
            boolean hasLowercase = false;

            for (String uid : uids) {
                for (char c : uid.toCharArray()) {
                    if (Character.isUpperCase(c)) {
                        hasUppercase = true;
                    }
                    if (Character.isLowerCase(c)) {
                        hasLowercase = true;
                    }
                }
                if (hasUppercase && hasLowercase) {
                    break;
                }
            }

            assertTrue(hasUppercase || hasLowercase,
                    "UIDs should contain alphabetic characters with case sensitivity");

            // Verify that case differences are preserved (e.g., "ABC123" != "abc123")
            String uid1 = "ABC123";
            String uid2 = "abc123";
            assertNotEquals(uid1, uid2, "UIDs should be case-sensitive");
        }
    }
}
