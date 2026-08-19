package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.TestCharacters.BEL
import dev.burufi.chatting.simple.shared.TestCharacters.BIDI_ISOLATE
import dev.burufi.chatting.simple.shared.TestCharacters.BIDI_OVERRIDE
import dev.burufi.chatting.simple.shared.TestCharacters.BYTE_ORDER_MARK
import dev.burufi.chatting.simple.shared.TestCharacters.CSI
import dev.burufi.chatting.simple.shared.TestCharacters.DEL
import dev.burufi.chatting.simple.shared.TestCharacters.ESC
import dev.burufi.chatting.simple.shared.TestCharacters.HIGH_SURROGATE
import dev.burufi.chatting.simple.shared.TestCharacters.LOW_SURROGATE
import dev.burufi.chatting.simple.shared.TestCharacters.NEL
import dev.burufi.chatting.simple.shared.TestCharacters.NUL
import dev.burufi.chatting.simple.shared.TestCharacters.REPLACEMENT
import dev.burufi.chatting.simple.shared.TestCharacters.ZERO_WIDTH
import dev.burufi.chatting.simple.shared.TestCharacters.ZERO_WIDTH_JOINER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

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
    @MethodSource("lonelySurrogates")
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
        @JvmStatic
        fun lonelySurrogates(): List<Arguments> =
            listOf(
                argumentSet("a high surrogate mid-string", "a${HIGH_SURROGATE}b"),
                argumentSet("a low surrogate mid-string", "a${LOW_SURROGATE}b"),
                argumentSet("a high surrogate alone", HIGH_SURROGATE.toString()),
                argumentSet("a low surrogate alone", LOW_SURROGATE.toString()),
            )

        @JvmStatic
        fun replaced(): List<Arguments> =
            listOf(
                // C0 has a printable twin per character, so what was there stays legible.
                argumentSet("escape", "a${ESC}b", "a␛b"),
                argumentSet("the sequence it opens", "a$ESC[2Jb", "a␛[2Jb"),
                argumentSet("null", "a${NUL}b", "a␀b"),
                argumentSet("bell", "a${BEL}b", "a␇b"),
                argumentSet("carriage return, the overwrite primitive", "a\rb", "a␍b"),
                argumentSet("delete, which sits past the block's run of C0", "a${DEL}b", "a␡b"),
                // C1 has no such twin, and neither does anything below.
                argumentSet("csi on its own, which needs no escape byte", "a${CSI}b", "a${REPLACEMENT}b"),
                argumentSet("a C1 with no picture of its own", "a${NEL}b", "a${REPLACEMENT}b"),
                argumentSet("a bidi override", "a${BIDI_OVERRIDE}b", "a${REPLACEMENT}b"),
                argumentSet("a bidi isolate", "a${BIDI_ISOLATE}b", "a${REPLACEMENT}b"),
                argumentSet("a zero width space", "a${ZERO_WIDTH}b", "a${REPLACEMENT}b"),
                argumentSet("a zero width joiner, emoji sequences included", "a${ZERO_WIDTH_JOINER}b", "a${REPLACEMENT}b"),
                argumentSet("a byte order mark", "a${BYTE_ORDER_MARK}b", "a${REPLACEMENT}b"),
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
