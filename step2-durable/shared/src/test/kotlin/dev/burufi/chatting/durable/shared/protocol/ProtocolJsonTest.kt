package dev.burufi.chatting.durable.shared.protocol

import dev.burufi.chatting.durable.shared.ErrorCode
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtocolJsonTest {
    @Test
    fun `the server refuses a client frame carrying an unknown key`() {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(ProtocolJson.CLIENT_FRAME, CLIENT_FRAME_WITH_UNKNOWN_KEY)
        }
    }

    @Test
    fun `the client ignores a server frame field it does not know`() {
        val frame = ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.SERVER_FRAME, SERVER_FRAME_WITH_UNKNOWN_KEY)
        assertEquals(
            ServerFrame.UserOnline(userId = 1, username = "alice"),
            frame,
            "a newer server adds a server frame field and an older client keeps working",
        )
    }

    @Test
    fun `the server refuses a server frame field it does not know`() {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(ProtocolJson.SERVER_FRAME, SERVER_FRAME_WITH_UNKNOWN_KEY)
        }
    }

    @Test
    fun `the client accepts what the server refuses`() {
        val frame = ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.CLIENT_FRAME, CLIENT_FRAME_WITH_UNKNOWN_KEY)
        assertEquals(
            ClientFrame.Send(clientMsgId = "c-1", to = "alice", body = "hi"),
            frame,
            "the asymmetry is these two configurations and nothing else",
        )
    }

    @Test
    fun `the two instances encode identically`() {
        val clientFrame: ClientFrame = ClientFrame.Send(clientMsgId = "c-1", to = "alice", body = "hi")
        val serverFrame: ServerFrame = ServerFrame.Error(ErrorCode.BODY_TOO_LARGE, "9014 bytes, limit is 8192")
        assertEquals(
            ProtocolJson.STRICT.encodeToString(ProtocolJson.CLIENT_FRAME, clientFrame),
            ProtocolJson.TOLERANT.encodeToString(ProtocolJson.CLIENT_FRAME, clientFrame),
        )
        assertEquals(
            ProtocolJson.STRICT.encodeToString(ProtocolJson.SERVER_FRAME, serverFrame),
            ProtocolJson.TOLERANT.encodeToString(ProtocolJson.SERVER_FRAME, serverFrame),
        )
    }

    @Test
    fun `neither instance accepts malformed JSON`() {
        val unquoted = """{type: send, to: alice, body: hi}"""
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(ProtocolJson.CLIENT_FRAME, unquoted)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.CLIENT_FRAME, unquoted)
        }
    }

    private companion object {
        const val CLIENT_FRAME_WITH_UNKNOWN_KEY =
            """{"type":"send","client_msg_id":"c-1","to":"alice","body":"hi","reply_to":7}"""

        const val SERVER_FRAME_WITH_UNKNOWN_KEY =
            """{"type":"user_online","user_id":1,"username":"alice","status":"away"}"""
    }
}
