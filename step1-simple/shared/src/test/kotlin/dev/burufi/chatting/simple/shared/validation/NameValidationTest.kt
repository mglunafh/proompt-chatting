package dev.burufi.chatting.simple.shared.validation

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.Validated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class NameValidationTest {
    @ParameterizedTest
    @MethodSource("accepted")
    fun `an accepted name comes back unchanged`(raw: String) {
        val result = ClientName.of(raw)
        assertTrue(result is Validated.Valid, "the name was refused")
        assertEquals(raw, (result as Validated.Valid).value.value)
    }

    @ParameterizedTest
    @MethodSource("rejected")
    fun `a refused name is refused as invalid`(raw: String) {
        val result = ClientName.of(raw)
        assertTrue(result is Validated.Invalid, "the name was accepted")
        assertEquals(ErrorCode.INVALID_NAME, (result as Validated.Invalid).code)
    }

    @Test
    fun `every reserved name would otherwise pass, and is refused anyway`() {
        val pattern = Regex(ClientName.PATTERN)
        ClientName.RESERVED.forEach { name ->
            assertTrue(pattern.matches(name), "$name does not reach the reserved check")
            val result = ClientName.of(name)
            assertTrue(result is Validated.Invalid, "$name was accepted")
            assertEquals(ErrorCode.INVALID_NAME, (result as Validated.Invalid).code)
            assertTrue(result.reason.contains("reserved"), result.reason)
        }
    }

    @Test
    fun `a refusal never quotes a name that failed the pattern`() {
        val result = ClientName.of("ali${ESC}ce") as Validated.Invalid
        assertFalse(result.reason.contains(ESC), "the reason carries the escape itself: ${result.reason}")
    }

    @Test
    fun `the pattern holds its documented form`() {
        // The one copy the client, the server and a later database constraint share.
        val documented = "^(?=[a-z0-9_-]{3,32}" + DOLLAR + ")[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*" + DOLLAR
        assertEquals(documented, ClientName.PATTERN)
    }

    companion object {
        /** Built from code points, so nothing invisible is carried in this source. */
        private val ESC = Char(0x1B)
        private val CYRILLIC_A = Char(0x430)
        private val DOLLAR = Char(0x24)

        @JvmStatic
        fun accepted(): List<Arguments> =
            listOf(
                case("plain", "alice"),
                case("at the shortest length", "abc"),
                case("at the longest length", "a".repeat(32)),
                case("digits after the first letter", "user1"),
                case("a dash separator", "a-b"),
                case("an underscore separator", "a_b_c"),
                case("separators between digit runs", "a1-b2_c3"),
            )

        @JvmStatic
        fun rejected(): List<Arguments> =
            listOf(
                case("empty", ""),
                case("one short of the minimum", "ab"),
                case("one past the maximum", "a".repeat(33)),
                case("uppercase, which would make alice and Alice two clients", "Alice"),
                case("starting with a digit", "1alice"),
                case("starting with a separator", "_alice"),
                case("ending with a separator", "alice_"),
                case("ending with a dash", "alice-"),
                case("doubled separators, so a__b and a_b cannot both exist", "a__b"),
                case("a space, which no command line could address", "a b"),
                case("a leading dash, which Clikt would read as a flag", "-alice"),
                case("a dot", "alice.bob"),
                case("punctuation", "ali!ce"),
                case("a Cyrillic lookalike", "${CYRILLIC_A}lice"),
                case("an escape sequence", "ali${ESC}ce"),
            )

        private fun case(
            label: String,
            raw: String,
        ): Arguments = argumentSet(label, raw)
    }
}
