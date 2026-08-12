package dev.burufi.chatting.simple.shared

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
    private val send: ClientFrame = ClientFrame.Send(to = "alice", body = "hi")
    private val sendJson = """{"type":"send","to":"alice","body":"hi"}"""

    @Test
    fun `send encodes to its wire form`() {
        assertEquals(sendJson, ProtocolJson.STRICT.encodeToString(serializer<ClientFrame>(), send))
    }

    @Test
    fun `send decodes from its wire form`() {
        assertEquals(send, ProtocolJson.STRICT.decodeFromString(serializer<ClientFrame>(), sendJson))
    }

    @Test
    fun `a body is reproduced verbatim`() {
        val body = "first\n\tquote \" backslash \\ rocket 🚀 combining é"
        val frame: ClientFrame = ClientFrame.Send(to = "alice", body = body)
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `a body at the byte cap round-trips`() {
        val frame: ClientFrame = ClientFrame.Send(to = "alice", body = "a".repeat(Limits.MAX_BODY_BYTES))
        assertEquals(frame, roundTrip(frame))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"type":"send","to":"alice"}""",
            """{"type":"bogus","to":"alice","body":"hi"}""",
            """{"to":"alice","body":"hi"}""",
            "\"send\"",
        ],
    )
    fun `an undecodable frame is refused by both instances`(payload: String) {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(serializer<ClientFrame>(), payload)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ClientFrame>(), payload)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the hierarchy registers exactly the step 1 client frames`() {
        val subtypes = serializer<ClientFrame>().descriptor.getElementDescriptor(1)
        assertEquals(setOf("send"), subtypes.elementNames.toSet())
    }

    private fun roundTrip(frame: ClientFrame): ClientFrame =
        ProtocolJson.STRICT.decodeFromString(
            serializer<ClientFrame>(),
            ProtocolJson.STRICT.encodeToString(serializer<ClientFrame>(), frame),
        )
}
