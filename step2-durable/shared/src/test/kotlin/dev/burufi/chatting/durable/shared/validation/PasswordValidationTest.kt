package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode
import dev.burufi.chatting.durable.shared.TestCharacters.E_ACUTE
import dev.burufi.chatting.durable.shared.TestCharacters.ROCKET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class PasswordValidationTest {
    @ParameterizedTest
    @MethodSource("accepted")
    fun `an accepted password comes back byte-for-byte, since a digest re-derives nothing`(raw: String) {
        assertEquals(Validated.Valid(raw), Password.validate(raw))
    }

    @ParameterizedTest
    @MethodSource("rejected")
    fun `a refused password is refused as invalid`(raw: String) {
        val result = Password.validate(raw)
        assertTrue(result is Validated.Invalid, "the password was accepted")
        assertEquals(ErrorCode.INVALID_PASSWORD, (result as Validated.Invalid).code)
    }

    @ParameterizedTest
    @MethodSource("rejected")
    fun `a refusal never quotes the password it refuses`(raw: String) {
        if (raw.isEmpty()) return
        val result = Password.validate(raw) as Validated.Invalid
        assertFalse(
            result.reason.contains(raw),
            "the reason carries the password out to whoever renders it: ${result.reason}",
        )
    }

    @Test
    fun `a password equal to the username is refused, in any casing`() {
        val username = "alice-in-wonderland"
        assertTrue(Password.validate(username, username) is Validated.Invalid)
        assertTrue(Password.validate(username.uppercase(), username) is Validated.Invalid)
        assertTrue(
            Password.validate(username, null) is Validated.Valid,
            "with no username to compare against there is nothing to refuse it for",
        )
    }

    @Test
    fun `a blocklisted password is refused in any casing`() {
        val blocked = "correcthorsebatterystaple"
        listOf(blocked, blocked.uppercase(), blocked.replaceFirstChar(Char::uppercase)).forEach { candidate ->
            val result = Password.validate(candidate)
            assertTrue(result is Validated.Invalid, "$candidate was accepted")
            assertTrue(
                (result as Validated.Invalid).reason.contains("common"),
                "too common is actionable where invalid is not: ${result.reason}",
            )
        }
    }

    @Test
    fun `no accepted case is itself blocklisted, which would fail for the wrong reason`() {
        val collisions = accepted().map { it.get()[0] as String }.filter { PasswordBlocklist.contains(it) }
        assertEquals(emptyList<String>(), collisions, "pick filler the shipped list does not already name")
    }

    @Test
    fun `the blocklist resource is actually on the classpath`() {
        assertTrue(
            PasswordBlocklist.entries.size > 100,
            "a blocklist that loaded but refuses nothing looks identical to a working one",
        )
    }

    @Test
    fun `no blocklist entry is unreachable, since the length rule runs first`() {
        val unreachable = PasswordBlocklist.entries.filter { it.length < Password.MIN_CHARS }
        assertEquals(emptyList<String>(), unreachable, "these entries can never be consulted")
    }

    @Test
    fun `every blocklist entry is lowercase, which is what the case-insensitive check assumes`() {
        assertEquals(emptyList<String>(), PasswordBlocklist.entries.filter { it != it.lowercase() })
    }

    @Test
    fun `the byte cap cannot be reached within the character cap, so it is defence in depth`() {
        // Three bytes is the most one UTF-16 code unit can cost; a surrogate pair costs four
        // across two units, so it is cheaper per unit rather than dearer.
        val worstCase = Password.MAX_CHARS * 3
        assertTrue(
            worstCase < Password.MAX_BYTES,
            "$worstCase bytes is the worst a ${Password.MAX_CHARS}-character password can cost, " +
                "so the ${Password.MAX_BYTES}-byte rule guards a case the character rule already caught",
        )
    }

    companion object {
        /** U+4E2D, three UTF-8 bytes to one code unit, which is the worst case for the byte cap. */
        private val CJK = Char(0x4E2D)

        @JvmStatic
        fun accepted(): List<Arguments> =
            listOf(
                case("at the shortest length", "ab".repeat(Password.MIN_CHARS).take(Password.MIN_CHARS)),
                case("at the longest length", "a".repeat(Password.MAX_CHARS)),
                case("spaces are a character like any other", "correct battery staple"),
                case("punctuation with no class to satisfy", "!@#\$%^&*()_+{}"),
                case("unicode, unnormalized", "pa${E_ACUTE}ssword-long$ROCKET"),
                case("a leading and trailing space survives, since nothing is trimmed", "  spaced out  "),
                case(
                    "the most bytes a password can carry, which is still under the byte cap",
                    CJK.toString().repeat(Password.MAX_CHARS),
                ),
            )

        @JvmStatic
        fun rejected(): List<Arguments> =
            listOf(
                case("empty", ""),
                case("one short of the minimum", "a".repeat(Password.MIN_CHARS - 1)),
                case("one past the maximum", "a".repeat(Password.MAX_CHARS + 1)),
                case("a surrogate pair straddling the character cap", ROCKET.repeat(Password.MAX_CHARS / 2 + 1)),
                case("blocklisted", "password123456"),
                case("blocklisted with padding that a length rule would not catch", "administrator123"),
            )

        private fun case(
            label: String,
            raw: String,
        ): Arguments = argumentSet(label, raw)
    }
}
