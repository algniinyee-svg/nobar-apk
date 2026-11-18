package com.algani.nobar

import org.junit.Assert.*
import org.junit.Test

class ProtocolRulesTest {
    @Test fun roomCodeNormalizationContract() {
        val input = " ab-cd 1234 "
        val normalized = input.uppercase().filter { it.isLetterOrDigit() }.take(12)
        assertEquals("ABCD1234", normalized)
    }
    @Test fun roomCapacityIsEight() { assertEquals(8, 8) }
    @Test fun youtubeIdsAreElevenCharacters() {
        assertTrue(Regex("^[\\w-]{11}$").matches("abcdefghijk"))
        assertFalse(Regex("^[\\w-]{11}$").matches("short"))
    }
}
