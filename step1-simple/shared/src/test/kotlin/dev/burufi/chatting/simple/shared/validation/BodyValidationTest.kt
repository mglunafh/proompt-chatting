package dev.burufi.chatting.simple.shared.validation

import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.Limits
import dev.burufi.chatting.simple.shared.Validated
import dev.burufi.chatting.simple.shared.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class BodyValidationTest {
    @ParameterizedTest
    @MethodSource("accepted")
    fun `an accepted body comes back in its normalized form`(
        raw: String,
        expected: String,
    ) {
        assertEquals(Validated.Valid(expected), Validation.validateBody(raw))
    }

    @ParameterizedTest
    @MethodSource("rejected")
    fun `a refused body names the rule it broke`(
        raw: String,
        code: ErrorCode,
    ) {
        val result = Validation.validateBody(raw)
        assertTrue(result is Validated.Invalid, "the body was accepted")
        assertEquals(code, (result as Validated.Invalid).code)
    }

    @Test
    fun `a refusal never quotes the character it refuses`() {
        val result = Validation.validateBody("hello$ESC[2Jworld") as Validated.Invalid
        assertFalse(result.reason.contains(ESC), "the reason carries the escape itself: ${result.reason}")
        assertTrue(result.reason.contains("U+001B"), result.reason)
    }

    @Test
    fun `a cap refusal names both sides of the limit it broke`() {
        val tooLarge = Validation.validateBody("a".repeat(Limits.MAX_BODY_BYTES + 1)) as Validated.Invalid
        assertEquals("${Limits.MAX_BODY_BYTES + 1} bytes, limit is ${Limits.MAX_BODY_BYTES}", tooLarge.reason)

        val tooManyLines = Validation.validateBody("a\n".repeat(Limits.MAX_BODY_LINES) + "a") as Validated.Invalid
        assertEquals("${Limits.MAX_BODY_LINES + 1} lines, limit is ${Limits.MAX_BODY_LINES}", tooManyLines.reason)
    }

    @Test
    fun `every body rule has a case on both sides of it`() {
        val declared = ErrorCode.entries.filter { it.name.startsWith("BODY_") }.toSet()
        val covered = rejected().map { it.get()[1] as ErrorCode }.toSet()
        assertEquals(declared, covered, "a BODY_ code exists with no rule producing it")
        assertTrue(accepted().isNotEmpty())
    }

    companion object {
        private val ESC = Char(0x1B)
        private val CSI = Char(0x9B)
        private val NUL = Char(0x00)
        private val DEL = Char(0x7F)
        private val HIGH_SURROGATE = Char(0xD83D)
        private val LOW_SURROGATE = Char(0xDE80)
        private val BIDI_OVERRIDE = Char(0x202E)
        private val ZERO_WIDTH = Char(0x200B)

        /** Two UTF-8 bytes to one character, which is what the cap cases turn on. */
        private val E_ACUTE = Char(0xE9)

        /** U+1F680, the pair whose halves are refused one at a time below. */
        private val ROCKET = "$HIGH_SURROGATE$LOW_SURROGATE"

        @JvmStatic
        fun accepted(): List<Arguments> =
            listOf(
                case("plain text", "hi", "hi"),
                case("tab and newline are the two controls kept", "a\tb\nc", "a\tb\nc"),
                case("crlf collapses to lf", "a\r\nb", "a\nb"),
                case("a trailing crlf collapses too", "a\r\n", "a\n"),
                case("a trailing newline is not emptiness", "a\n", "a\n"),
                case("punctuation is verbatim", """quote " backslash \ $E_ACUTE""", """quote " backslash \ $E_ACUTE"""),
                case("a surrogate pair survives whole", "a${ROCKET}b", "a${ROCKET}b"),
                case("a bidi override is the client's to render, not a rule here", "a${BIDI_OVERRIDE}b", "a${BIDI_OVERRIDE}b"),
                case("a zero-width character is allowed in a body", "a${ZERO_WIDTH}b", "a${ZERO_WIDTH}b"),
                case("at the byte cap", "a".repeat(Limits.MAX_BODY_BYTES), "a".repeat(Limits.MAX_BODY_BYTES)),
                case(
                    "a multi-byte character reaching the byte cap",
                    "a".repeat(Limits.MAX_BODY_BYTES - 2) + E_ACUTE,
                    "a".repeat(Limits.MAX_BODY_BYTES - 2) + E_ACUTE,
                ),
                case(
                    "at the line cap",
                    "a\n".repeat(Limits.MAX_BODY_LINES - 1) + "a",
                    "a\n".repeat(Limits.MAX_BODY_LINES - 1) + "a",
                ),
            )

        @JvmStatic
        fun rejected(): List<Arguments> =
            listOf(
                case("empty", "", ErrorCode.BODY_EMPTY),
                case("whitespace only", "  \t\n ", ErrorCode.BODY_EMPTY),
                case("one byte past the cap", "a".repeat(Limits.MAX_BODY_BYTES + 1), ErrorCode.BODY_TOO_LARGE),
                case(
                    "a multi-byte character straddling the cap, so bytes are counted and not characters",
                    "a".repeat(Limits.MAX_BODY_BYTES - 1) + E_ACUTE,
                    ErrorCode.BODY_TOO_LARGE,
                ),
                case("one line past the cap", "a\n".repeat(Limits.MAX_BODY_LINES) + "a", ErrorCode.BODY_TOO_MANY_LINES),
                case("escape", "a${ESC}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case("csi on its own, which needs no escape byte", "a${CSI}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case("nul", "a${NUL}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case("delete", "a${DEL}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case("a bare carriage return, the overwrite primitive", "a\rb", ErrorCode.BODY_INVALID_CHARACTERS),
                case("a trailing carriage return, which no collapse covers", "a\r", ErrorCode.BODY_INVALID_CHARACTERS),
                case("an unpaired high surrogate", "a${HIGH_SURROGATE}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case("an unpaired low surrogate", "a${LOW_SURROGATE}b", ErrorCode.BODY_INVALID_CHARACTERS),
                case(
                    "the byte cap is measured before emptiness",
                    " ".repeat(Limits.MAX_BODY_BYTES + 1),
                    ErrorCode.BODY_TOO_LARGE,
                ),
                case("the scan runs before the line cap", "$ESC" + "\n".repeat(200), ErrorCode.BODY_INVALID_CHARACTERS),
            )

        private fun case(
            label: String, // display name
            raw: String,
            expected: String,
        ): Arguments = argumentSet(label, raw, expected)

        private fun case(
            label: String, // display name
            raw: String,
            code: ErrorCode,
        ): Arguments = argumentSet(label, raw, code)
    }
}
