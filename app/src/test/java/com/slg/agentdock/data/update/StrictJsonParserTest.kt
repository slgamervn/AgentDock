package com.slg.agentdock.data.update

import org.junit.Assert.assertThrows
import org.junit.Test

class StrictJsonParserTest {
    @Test
    fun acceptsOnlyJsonWhitespace() {
        StrictJsonParser.parse(" \t\r\n{}", maxChars = 16)
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("\u00A0{}", maxChars = 16)
        }
    }

    @Test
    fun rejectsUnexpectedTokenWithConsistentArgumentError() {
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("@", maxChars = 1)
        }
    }

    @Test
    fun rejectsInvalidEscapeWithConsistentArgumentError() {
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("""{"value":"\x"}""", maxChars = 32)
        }
    }

    @Test
    fun rejectsUnpairedUnicodeSurrogates() {
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("""{"value":"\uD800"}""", maxChars = 32)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("""{"value":"\uDC00"}""", maxChars = 32)
        }
    }

    @Test
    fun acceptsValidUnicodeSurrogatePair() {
        StrictJsonParser.parse("""{"value":"\uD83D\uDE80"}""", maxChars = 32)
    }

    @Test
    fun acceptsMaximumSupportedNestingDepth() {
        val depth = 32
        val nested = "[".repeat(depth) + "0" + "]".repeat(depth)
        StrictJsonParser.parse(nested, maxChars = nested.length)
    }

    @Test
    fun rejectsNestingBeyondMaximumDepth() {
        val depth = 33
        val nested = "[".repeat(depth) + "0" + "]".repeat(depth)
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse(nested, maxChars = nested.length)
        }
    }

    @Test
    fun rejectsNegativeSizeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            StrictJsonParser.parse("{}", maxChars = -1)
        }
    }
}