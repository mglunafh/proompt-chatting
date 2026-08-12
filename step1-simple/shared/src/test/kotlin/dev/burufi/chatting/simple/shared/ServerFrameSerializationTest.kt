package dev.burufi.chatting.simple.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ServerFrameSerializationTest {
    @ParameterizedTest
    @MethodSource("frames")
    fun `a server frame encodes to its wire form`(
        frame: ServerFrame,
        json: String,
    ) {
        assertEquals(json, ProtocolJson.TOLERANT.encodeToString(serializer<ServerFrame>(), frame))
    }

    @ParameterizedTest
    @MethodSource("frames")
    fun `a server frame decodes from its wire form`(
        frame: ServerFrame,
        json: String,
    ) {
        assertEquals(frame, ProtocolJson.TOLERANT.decodeFromString(serializer<ServerFrame>(), json))
    }

    @Test
    fun `an empty roster round-trips`() {
        val frame: ServerFrame = ServerFrame.Roster(emptyList())
        val json = ProtocolJson.TOLERANT.encodeToString(serializer<ServerFrame>(), frame)
        assertEquals("""{"type":"roster","names":[]}""", json)
        assertEquals(frame, ProtocolJson.TOLERANT.decodeFromString(serializer<ServerFrame>(), json))
    }

    @Test
    fun `an absent field is refused rather than defaulted`() {
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ServerFrame>(), """{"type":"user_joined"}""")
        }
    }

    @Test
    fun `a null in a non-nullable field is refused`() {
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(serializer<ServerFrame>(), """{"type":"user_joined","name":null}""")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the hierarchy registers exactly the step 1 server frames`() {
        val subtypes = serializer<ServerFrame>().descriptor.getElementDescriptor(1)
        assertEquals(
            setOf("roster", "user_joined", "user_left", "message", "error"),
            subtypes.elementNames.toSet(),
        )
    }

    companion object {
        @JvmStatic
        fun frames(): List<Array<Any>> =
            listOf(
                arrayOf(
                    ServerFrame.Roster(listOf("alice", "bob")),
                    """{"type":"roster","names":["alice","bob"]}""",
                ),
                arrayOf(
                    ServerFrame.UserJoined("bob"),
                    """{"type":"user_joined","name":"bob"}""",
                ),
                arrayOf(
                    ServerFrame.UserLeft("bob"),
                    """{"type":"user_left","name":"bob"}""",
                ),
                arrayOf(
                    ServerFrame.Message(from = "alice", to = "bob", body = "hi"),
                    """{"type":"message","from":"alice","to":"bob","body":"hi"}""",
                ),
                arrayOf(
                    ServerFrame.Error(ErrorCode.UNKNOWN_RECIPIENT, "bob is not connected"),
                    """{"type":"error","code":"unknown_recipient","reason":"bob is not connected"}""",
                ),
            )
    }
}
