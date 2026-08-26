package dev.burufi.chatting.simple.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class NameValidationTest {
    @ParameterizedTest
    @MethodSource("validNames")
    fun `accepts valid names`(name: String) {
        assertEquals(ValidationOutcome.Ok, Validation.name(name))
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    fun `rejects invalid names`(
        name: String,
        expectedReason: String,
    ) {
        val outcome = Validation.name(name)
        val invalid = assertInstanceOf(ValidationOutcome.Invalid::class.java, outcome)
        assertEquals(expectedReason, invalid.reason)
    }

    companion object {
        @JvmStatic
        fun validNames(): List<Arguments> =
            listOf(
                Arguments.of("alice"),
                Arguments.of("bob"),
                Arguments.of("héllo"),
                Arguments.of("alice\nbob"),
                Arguments.of("alice\tbob"),
                Arguments.of("a".repeat(Caps.MAX_MESSAGE_BYTES)),
            )

        @JvmStatic
        fun invalidNames(): List<Arguments> =
            listOf(
                Arguments.of("alice\rworld", "name contains a forbidden control character"),
                Arguments.of("alice\u0000bob", "name contains a forbidden control character"),
                Arguments.of("alice\u0007bob", "name contains a forbidden control character"),
                Arguments.of("alice\u001Bbob", "name contains a forbidden control character"),
                Arguments.of("alice\u007Fbob", "name contains a forbidden control character"),
                Arguments.of("alice\u0085bob", "name contains a forbidden control character"),
                Arguments.of("alice\u009Bbob", "name contains a forbidden control character"),
                Arguments.of("a".repeat(Caps.MAX_MESSAGE_BYTES + 1), "name exceeds the byte cap"),
            )
    }
}
