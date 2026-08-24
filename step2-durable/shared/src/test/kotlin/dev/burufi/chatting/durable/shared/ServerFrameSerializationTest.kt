package dev.burufi.chatting.durable.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Instant

class ServerFrameSerializationTest {
    @ParameterizedTest
    @MethodSource("wireForms")
    fun `a frame encodes to its wire form`(
        frame: ServerFrame,
        json: String,
    ) {
        assertEquals(json, encode(frame))
    }

    @ParameterizedTest
    @MethodSource("wireForms")
    fun `a frame decodes from its wire form`(
        frame: ServerFrame,
        json: String,
    ) {
        assertEquals(frame, decode(json))
    }

    @Test
    fun `an empty snapshot is a present but empty list`() {
        val frame: ServerFrame = ServerFrame.PresenceSnapshot(users = emptyList())
        assertEquals("""{"type":"presence_snapshot","users":[]}""", encode(frame))
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `an error without a client_msg_id still carries the key`() {
        val frame: ServerFrame = ServerFrame.Error(ErrorCode.MALFORMED_FRAME, "unknown key")
        assertEquals(
            """{"type":"error","code":"malformed_frame","reason":"unknown key","client_msg_id":null}""",
            encode(frame),
            "encodeDefaults holds the frame shape steady whether or not the refused frame carried a key",
        )
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `a delivered body is reproduced verbatim`() {
        val body = "first\n\tquote \" backslash \\ rocket ${TestCharacters.ROCKET} combining ${TestCharacters.E_ACUTE}"
        val frame: ServerFrame =
            ServerFrame.Message(id = 42, conversationId = 7, senderId = 1, body = body, createdAt = AT)
        assertEquals(frame, roundTrip(frame))
    }

    @Test
    fun `sub-second precision survives, so a stamp still compares equal to the row it came from`() {
        val precise = Instant.parse("2026-08-24T10:15:30.123456Z")
        val frame: ServerFrame =
            ServerFrame.Message(id = 42, conversationId = 7, senderId = 1, body = "hi", createdAt = precise)
        assertEquals(
            """{"type":"message","id":42,"conversation_id":7,"sender_id":1,"body":"hi",""" +
                """"created_at":"2026-08-24T10:15:30.123456Z"}""",
            encode(frame),
            "timestamptz holds microseconds, and truncating here would make an ack disagree with its own row",
        )
        assertEquals(frame, roundTrip(frame))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"type":"user_online","user_id":1}""",
            """{"type":"user_offline","user_id":1}""",
            """{"type":"heartbeat"}""",
            """{"type":"error","code":"no_such_code","reason":"x"}""",
            """{"type":"bogus","seq":1}""",
            """{"seq":1}""",
        ],
    )
    fun `an undecodable frame is refused by both instances`(payload: String) {
        assertThrows<SerializationException> {
            ProtocolJson.STRICT.decodeFromString(ProtocolJson.SERVER_FRAME, payload)
        }
        assertThrows<SerializationException> {
            ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.SERVER_FRAME, payload)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the hierarchy registers exactly the step 2 server frames`() {
        val subtypes = serializer<ServerFrame>().descriptor.getElementDescriptor(1)
        assertEquals(
            setOf("presence_snapshot", "user_online", "user_offline", "ack", "message", "heartbeat", "error"),
            subtypes.elementNames.toSet(),
        )
    }

    private fun encode(frame: ServerFrame) = ProtocolJson.STRICT.encodeToString(ProtocolJson.SERVER_FRAME, frame)

    private fun decode(payload: String) = ProtocolJson.STRICT.decodeFromString(ProtocolJson.SERVER_FRAME, payload)

    private fun roundTrip(frame: ServerFrame): ServerFrame = decode(encode(frame))

    private companion object {
        const val AT_ISO = "2026-08-24T10:15:30Z"
        val AT: Instant = Instant.parse(AT_ISO)

        @JvmStatic
        fun wireForms() =
            listOf(
                arrayOf(
                    ServerFrame.PresenceSnapshot(listOf(PresenceEntry(1, "alice"), PresenceEntry(2, "bob"))),
                    """{"type":"presence_snapshot","users":[{"user_id":1,"username":"alice"},""" +
                        """{"user_id":2,"username":"bob"}]}""",
                ),
                arrayOf(
                    ServerFrame.UserOnline(userId = 1, username = "alice"),
                    """{"type":"user_online","user_id":1,"username":"alice"}""",
                ),
                arrayOf(
                    ServerFrame.UserOffline(userId = 1, lastSeenAt = AT),
                    """{"type":"user_offline","user_id":1,"last_seen_at":"$AT_ISO"}""",
                ),
                arrayOf(
                    ServerFrame.Ack(clientMsgId = "c-1", messageId = 42, conversationId = 7, createdAt = AT),
                    """{"type":"ack","client_msg_id":"c-1","message_id":42,"conversation_id":7,"created_at":"$AT_ISO"}""",
                ),
                arrayOf(
                    ServerFrame.Message(id = 42, conversationId = 7, senderId = 1, body = "hi", createdAt = AT),
                    """{"type":"message","id":42,"conversation_id":7,"sender_id":1,"body":"hi","created_at":"$AT_ISO"}""",
                ),
                arrayOf(
                    ServerFrame.Heartbeat(seq = 7),
                    """{"type":"heartbeat","seq":7}""",
                ),
                arrayOf(
                    ServerFrame.Error(ErrorCode.BODY_TOO_LARGE, "9014 bytes, limit is 8192", "c-1"),
                    """{"type":"error","code":"body_too_large","reason":"9014 bytes, limit is 8192",""" +
                        """"client_msg_id":"c-1"}""",
                ),
            )
    }
}
