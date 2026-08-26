package dev.burufi.chatting.simple.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class ServerFrameSerializationTest {
    private val json = Json.Default

    @Test
    fun `Roster round-trips`() {
        val original = ServerFrame.Roster(names = listOf("alice", "bob"))

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Roster with empty list round-trips`() {
        val original = ServerFrame.Roster(names = emptyList())

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Message round-trips`() {
        val original = ServerFrame.Message(sender = "alice", body = "hi bob")

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Joined round-trips`() {
        val original = ServerFrame.Joined(name = "alice")

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Left round-trips`() {
        val original = ServerFrame.Left(name = "alice")

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Error round-trips`() {
        val original = ServerFrame.Error(reason = "recipient not connected")

        val encoded = json.encodeToString(ServerFrame.serializer(), original)
        val decoded = json.decodeFromString(ServerFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `unknown type discriminator is rejected`() {
        val encoded = """{"type":"bogus","name":"alice"}"""

        assertThrows(JsonDecodingException::class.java) {
            json.decodeFromString(ServerFrame.serializer(), encoded)
        }
    }
}
