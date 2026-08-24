package dev.burufi.chatting.durable.shared.protocol

import dev.burufi.chatting.durable.shared.Limits
import dev.burufi.chatting.durable.shared.TestCharacters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ClientFrameSerializationTest {
    private val send: ClientFrame = ClientFrame.Send(clientMsgId = "c-1", to = "alice", body = "hi")
    private val sendJson = """{"type":"send","client_msg_id":"c-1","to":"alice","body":"hi"}"""

    private val reply: ClientFrame = ClientFrame.HeartbeatReply(seq = 7)
    private val replyJson = """{"type":"heartbeat_reply","seq":7}"""

    @Test
    fun `send encodes to its wire form`() {
        assertEquals(sendJson, encode(send))
    }

    @Test
    fun `send decodes from its wire form`() {
        assertEquals(send, decode(sendJson))
    }

    @Test
    fun `heartbeat reply encodes to its wire form`() {
        assertEquals(replyJson, encode(reply))
    }

    @Test
    fun `heartbeat reply decodes from its wire form`() {
        assertEquals(reply, decode(replyJson))
    }

    @Test
    fun `a body is reproduced verbatim`() {
        val body = "first\n\tquote \" backslash \\ rocket ${TestCharacters.ROCKET} combining ${TestCharacters.E_ACUTE}"
        val frame: ClientFrame = ClientFrame.Send(clientMsgId = "c-1", to = "alice", body = body)
        assertEquals(frame, roundTrip(frame), "a message body crosses the wire byte-for-byte, unlike a name")
    }

    @Test
    fun `a body at the byte cap round-trips`() {
        val frame: ClientFrame =
            ClientFrame.Send(clientMsgId = "c-1", to = "alice", body = "a".repeat(Limits.MAX_BODY_BYTES))
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `a client_msg_id at the byte cap round-trips`() {
        val frame: ClientFrame =
            ClientFrame.Send(clientMsgId = "c".repeat(Limits.MAX_CLIENT_MSG_ID_BYTES), to = "alice", body = "hi")
        assertEquals(frame, roundTrip(frame))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"type":"send","to":"alice","body":"hi"}""",
            """{"type":"send","client_msg_id":"c-1","to":"alice"}""",
            """{"type":"heartbeat_reply"}""",
            """{"type":"bogus","client_msg_id":"c-1","to":"alice","body":"hi"}""",
            """{"client_msg_id":"c-1","to":"alice","body":"hi"}""",
            "\"send\"",
        ],
    )
    fun `an undecodable frame is refused by both instances`(payload: String) {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(ProtocolJson.CLIENT_FRAME, payload)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.CLIENT_FRAME, payload)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the hierarchy registers exactly the step 2 client frames`() {
        val subtypes = serializer<ClientFrame>().descriptor.getElementDescriptor(1)
        assertEquals(setOf("send", "heartbeat_reply"), subtypes.elementNames.toSet())
    }

    private fun encode(frame: ClientFrame) = ProtocolJson.STRICT.encodeToString(ProtocolJson.CLIENT_FRAME, frame)

    private fun decode(payload: String) = ProtocolJson.STRICT.decodeFromString(ProtocolJson.CLIENT_FRAME, payload)

    private fun roundTrip(frame: ClientFrame): ClientFrame = decode(encode(frame))
}
