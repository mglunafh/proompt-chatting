package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtocolJsonTest {
    @Test
    fun `the server refuses a client frame carrying an unknown key`() {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(serializer<ClientFrame>(), FRAME_WITH_UNKNOWN_KEY)
        }
    }

    @Test
    fun `the client ignores a server frame field it does not know`() {
        val frame =
            ProtocolJson.TOLERANT.decodeFromString(
                serializer<ServerFrame>(),
                """{"type":"user_joined","name":"bob","joined_at":"2026-08-12T00:00:00Z"}""",
            )
        assertEquals(ServerFrame.UserJoined("bob"), frame)
    }

    @Test
    fun `the server refuses a server frame field it does not know`() {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(
                serializer<ServerFrame>(),
                """{"type":"user_joined","name":"bob","joined_at":"2026-08-12T00:00:00Z"}""",
            )
        }
    }

    @Test
    fun `the two instances encode identically`() {
        val clientFrame: ClientFrame = ClientFrame.Send(to = "alice", body = "hi")
        val serverFrame: ServerFrame = ServerFrame.Error(ErrorCode.BODY_TOO_LARGE, "9014 bytes, limit is 8192")
        assertEquals(
            ProtocolJson.STRICT.encodeToString(serializer<ClientFrame>(), clientFrame),
            ProtocolJson.TOLERANT.encodeToString(serializer<ClientFrame>(), clientFrame),
        )
        assertEquals(
            ProtocolJson.STRICT.encodeToString(serializer<ServerFrame>(), serverFrame),
            ProtocolJson.TOLERANT.encodeToString(serializer<ServerFrame>(), serverFrame),
        )
    }

    @Test
    fun `neither instance accepts malformed JSON`() {
        val unquoted = """{type: send, to: alice, body: hi}"""
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(serializer<ClientFrame>(), unquoted)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ClientFrame>(), unquoted)
        }
    }

    @Test
    fun `the client accepts what the server refuses`() {
        val frame = ProtocolJson.TOLERANT.decodeFromString(serializer<ClientFrame>(), FRAME_WITH_UNKNOWN_KEY)
        assertEquals(ClientFrame.Send(to = "alice", body = "hi"), frame)
    }

    private companion object {
        const val FRAME_WITH_UNKNOWN_KEY = """{"type":"send","to":"alice","body":"hi","client_msg_id":"7"}"""
    }
}
