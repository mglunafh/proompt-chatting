package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ErrorCodeSerializationTest {
    @ParameterizedTest
    @EnumSource(ErrorCode::class)
    fun `a code travels as a snake_case string and round-trips`(code: ErrorCode) {
        val json = ProtocolJson.STRICT.encodeToString(ErrorCode.serializer(), code)
        assertTrue(
            Regex("""^"[a-z][a-z0-9_]*"$""").matches(json),
            "$code encodes as $json",
        )
        assertEquals(code, ProtocolJson.STRICT.decodeFromString(ErrorCode.serializer(), json))
    }

    @Test
    fun `every code holds its wire name`() {
        val expected =
            mapOf(
                ErrorCode.MALFORMED_FRAME to "malformed_frame",
                ErrorCode.INVALID_NAME to "invalid_name",
                ErrorCode.NAME_TAKEN to "name_taken",
                ErrorCode.UNKNOWN_RECIPIENT to "unknown_recipient",
                ErrorCode.BODY_EMPTY to "body_empty",
                ErrorCode.BODY_TOO_LARGE to "body_too_large",
                ErrorCode.BODY_TOO_MANY_LINES to "body_too_many_lines",
                ErrorCode.BODY_INVALID_CHARACTERS to "body_invalid_characters",
            )
        val actual =
            ErrorCode.entries.associateWith {
                ProtocolJson.STRICT.encodeToString(ErrorCode.serializer(), it).trim('"')
            }
        assertEquals(expected, actual)
    }

    @Test
    fun `an unrecognized code is refused by both instances`() {
        val payload = """{"type":"error","code":"from_the_future","reason":"x"}"""
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(serializer<ServerFrame>(), payload)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ServerFrame>(), payload)
        }
    }
}
