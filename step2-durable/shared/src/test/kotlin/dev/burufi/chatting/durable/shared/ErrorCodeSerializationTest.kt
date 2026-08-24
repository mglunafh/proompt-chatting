package dev.burufi.chatting.durable.shared

import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ErrorCodeSerializationTest {
    @ParameterizedTest
    @CsvSource(
        "MALFORMED_FRAME, malformed_frame",
        "UNKNOWN_RECIPIENT, unknown_recipient",
        "BODY_EMPTY, body_empty",
        "BODY_TOO_LARGE, body_too_large",
        "BODY_TOO_MANY_LINES, body_too_many_lines",
        "BODY_INVALID_CHARACTERS, body_invalid_characters",
        "CLIENT_MSG_ID_TOO_LONG, client_msg_id_too_long",
    )
    fun `a code round-trips through its wire name`(
        code: ErrorCode,
        wireName: String,
    ) {
        assertEquals(""""$wireName"""", ProtocolJson.STRICT.encodeToString(serializer<ErrorCode>(), code))
        assertEquals(code, ProtocolJson.STRICT.decodeFromString(serializer<ErrorCode>(), """"$wireName""""))
    }

    @Test
    fun `every code is covered by the cases above`() {
        assertEquals(7, ErrorCode.entries.size, "a code added without a case here would go unchecked")
    }

    @Test
    fun `an unrecognized code fails to decode rather than falling back`() {
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ErrorCode>(), """"no_such_code"""")
        }
    }
}
