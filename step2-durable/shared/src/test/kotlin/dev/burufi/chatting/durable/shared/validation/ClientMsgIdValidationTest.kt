package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode
import dev.burufi.chatting.durable.shared.Limits
import dev.burufi.chatting.durable.shared.TestCharacters.E_ACUTE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class ClientMsgIdValidationTest {
    @ParameterizedTest
    @MethodSource("accepted")
    fun `an accepted key comes back unchanged`(raw: String) {
        assertEquals(Validated.Valid(raw), Validation.validateClientMsgId(raw))
    }

    @ParameterizedTest
    @MethodSource("rejected")
    fun `a refused key names the cap it broke`(raw: String) {
        val result = Validation.validateClientMsgId(raw)
        assertTrue(result is Validated.Invalid, "the key was accepted")
        assertEquals(ErrorCode.CLIENT_MSG_ID_TOO_LONG, (result as Validated.Invalid).code)
    }

    companion object {
        @JvmStatic
        fun accepted(): List<Arguments> =
            listOf(
                case("a uuid, which is what a client will send", "3f2b1c4e-8a90-4d6f-9b21-7c5e0a1d2f34"),
                case("a single character", "1"),
                case("at the byte cap", "c".repeat(Limits.MAX_CLIENT_MSG_ID_BYTES)),
                case(
                    "a multi-byte key reaching the byte cap",
                    "c".repeat(Limits.MAX_CLIENT_MSG_ID_BYTES - 2) + E_ACUTE,
                ),
            )

        @JvmStatic
        fun rejected(): List<Arguments> =
            listOf(
                case("empty, since every blank key from one sender is the same key", ""),
                case("blank, which the unique index would treat as a value", "   "),
                case("one byte past the cap", "c".repeat(Limits.MAX_CLIENT_MSG_ID_BYTES + 1)),
                case(
                    "a multi-byte character straddling the cap, so bytes are counted and not characters",
                    "c".repeat(Limits.MAX_CLIENT_MSG_ID_BYTES - 1) + E_ACUTE,
                ),
            )

        private fun case(
            label: String,
            raw: String,
        ): Arguments = argumentSet(label, raw)
    }
}
