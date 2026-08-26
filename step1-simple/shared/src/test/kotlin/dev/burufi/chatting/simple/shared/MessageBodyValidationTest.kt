package dev.burufi.chatting.simple.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class MessageBodyValidationTest {
    @ParameterizedTest
    @MethodSource("validBodies")
    fun `accepts valid bodies`(body: String) {
        assertEquals(ValidationOutcome.Ok, Validation.messageBody(body))
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    fun `rejects invalid bodies`(
        body: String,
        expectedReason: String,
    ) {
        val outcome = Validation.messageBody(body)
        val invalid = assertInstanceOf(ValidationOutcome.Invalid::class.java, outcome)
        assertEquals(expectedReason, invalid.reason)
    }

    companion object {
        @JvmStatic
        fun validBodies(): List<Arguments> =
            listOf(
                Arguments.of("hello"),
                Arguments.of("hello world"),
                Arguments.of("hello\r\nworld"),
                Arguments.of("hello\nworld"),
                Arguments.of("hello\nworld\n"),
                Arguments.of("hello\tworld"),
                Arguments.of("héllo wörld"),
                Arguments.of("こんにちは"),
                Arguments.of("👋🌍"),
                Arguments.of("a\n".repeat(99)),
                Arguments.of("a".repeat(Caps.MAX_MESSAGE_BYTES)),
            )

        @JvmStatic
        fun invalidBodies(): List<Arguments> =
            listOf(
                Arguments.of("", "body is empty after trimming whitespace"),
                Arguments.of("   ", "body is empty after trimming whitespace"),
                Arguments.of("\t", "body is empty after trimming whitespace"),
                Arguments.of("\n", "body is empty after trimming whitespace"),
                Arguments.of("\n\n\n", "body is empty after trimming whitespace"),
                Arguments.of("hello\rworld", "body has a bare carriage return"),
                Arguments.of("hello\r", "body has a bare carriage return"),
                Arguments.of("\rhello", "body has a bare carriage return"),
                Arguments.of("hello\u0000world", "body contains a forbidden control character"),
                Arguments.of("hello\u0007world", "body contains a forbidden control character"),
                Arguments.of("hello\u001B[31mred", "body contains a forbidden control character"),
                Arguments.of("hello\u007Fworld", "body contains a forbidden control character"),
                Arguments.of("hello\u0085world", "body contains a forbidden control character"),
                Arguments.of("hello\u009B[31mred", "body contains a forbidden control character"),
                Arguments.of("a".repeat(Caps.MAX_MESSAGE_BYTES + 1), "body exceeds the byte cap"),
                Arguments.of("é".repeat(Caps.MAX_MESSAGE_BYTES), "body exceeds the byte cap"),
                Arguments.of("a\n".repeat(100), "body exceeds the line cap"),
            )
    }
}
