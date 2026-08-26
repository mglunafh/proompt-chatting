package dev.burufi.chatting.simple.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class ClientFrameSerializationTest {
    private val json = Json.Default

    @Test
    fun `Send round-trips with type discriminator`() {
        val original = ClientFrame.Send(recipient = "bob", body = "hello")

        val encoded = json.encodeToString(ClientFrame.serializer(), original)
        val decoded = json.decodeFromString(ClientFrame.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `Send encodes with the documented wire shape`() {
        val frame = ClientFrame.Send(recipient = "bob", body = "hello")

        val encoded = json.encodeToString(ClientFrame.serializer(), frame)

        assertEquals(
            """{"type":"send","recipient":"bob","body":"hello"}""",
            encoded,
        )
    }

    @Test
    fun `Send decodes from the documented wire shape`() {
        val encoded = """{"type":"send","recipient":"bob","body":"hello"}"""

        val decoded = json.decodeFromString(ClientFrame.serializer(), encoded)

        assertEquals(ClientFrame.Send("bob", "hello"), decoded)
    }
}
