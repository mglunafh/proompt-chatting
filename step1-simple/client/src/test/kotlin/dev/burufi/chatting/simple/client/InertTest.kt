package dev.burufi.chatting.simple.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class InertTest {
    @ParameterizedTest
    @MethodSource("replaced")
    fun `a character that would drive the terminal is shown instead`(
        raw: String,
        expected: String,
    ) {
        assertEquals(expected, inert(raw))
    }

    @ParameterizedTest
    @MethodSource("kept")
    fun `text that says nothing to the terminal is left alone`(text: String) {
        assertEquals(text, inert(text))
    }

    @Test
    fun `an astral character survives whole`() {
        // The pair is one code point. A walk over Char would see two lone surrogates
        // and replace both, which is what this pins.
        assertEquals("a😀b", inert("a😀b"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["a\uD83Db", "a\uDE00b", "\uD83D", "\uDE00"])
    fun `a surrogate with no partner is replaced`(raw: String) {
        assertFalse(inert(raw).any { it.isSurrogate() }, "a lone surrogate reached the terminal")
    }

    @Test
    fun `nothing across C0 or C1 survives as a control character`() {
        for (code in 0x00..0x9F) {
            val survived =
                inert("a${code.toChar()}b").any { char ->
                    char != '\n' && char != '\t' && (char.code < 0x20 || char.code == 0x7F || char.code in 0x80..0x9F)
                }
            assertFalse(survived, "U+%04X survived as a control character".format(code))
        }
    }

    @Test
    fun `a replacement is visible rather than a silent removal`() {
        // What is dropped rather than replaced reads as innocent text afterwards.
        for (code in 0x00..0x9F) {
            assertTrue(inert(code.toChar().toString()).isNotEmpty(), "U+%04X was dropped".format(code))
        }
    }

    companion object {
        private const val REPLACEMENT = "�"

        @JvmStatic
        fun replaced(): List<Arguments> =
            listOf(
                // C0 has a printable twin per character, so what was there stays legible.
                argumentSet("escape", "a\u001Bb", "a␛b"),
                argumentSet("the sequence it opens", "a\u001B[2Jb", "a␛[2Jb"),
                argumentSet("null", "a\u0000b", "a␀b"),
                argumentSet("bell", "a\u0007b", "a␇b"),
                argumentSet("carriage return, the overwrite primitive", "a\rb", "a␍b"),
                argumentSet("delete, which sits past the block's run of C0", "a\u007Fb", "a␡b"),
                // C1 has no such twin, and neither does anything below.
                argumentSet("csi on its own, which needs no escape byte", "a\u009Bb", "a${REPLACEMENT}b"),
                argumentSet("a C1 with no picture of its own", "a\u0085b", "a${REPLACEMENT}b"),
                argumentSet("a bidi override", "a\u202Eb", "a${REPLACEMENT}b"),
                argumentSet("a bidi isolate", "a\u2066b", "a${REPLACEMENT}b"),
                argumentSet("a zero width space", "a\u200Bb", "a${REPLACEMENT}b"),
                argumentSet("a zero width joiner, emoji sequences included", "a\u200Db", "a${REPLACEMENT}b"),
                argumentSet("a byte order mark", "a\uFEFFb", "a${REPLACEMENT}b"),
            )

        @JvmStatic
        fun kept(): List<Arguments> =
            listOf(
                argumentSet("plain text", "hello there"),
                argumentSet("punctuation and symbols", "a+b=c! (100%) ~ <ok>"),
                argumentSet("the two control characters a body may carry", "a\tb\nc"),
                argumentSet("accented text", "élève naïve"),
                argumentSet("CJK", "你好世界"),
                argumentSet("right to left text, which is legitimate", "שלום"),
                argumentSet("a combining mark", "é"),
                argumentSet("nothing at all", ""),
            )
    }
}
