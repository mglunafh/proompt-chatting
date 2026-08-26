package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class ChatRouteTest {
    @Test
    fun `valid name keeps the connection open`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                val closed = withTimeoutOrNull(100) { closeReason.await() }
                assertNull(closed)
            }
        }

    @Test
    fun `missing name closes the connection with VIOLATED_POLICY`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat") {
                val reason = withTimeout(1000) { closeReason.await() }
                assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
            }
        }

    @Test
    fun `invalid name closes the connection with VIOLATED_POLICY`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice\u0007bob") {
                val reason = withTimeout(1000) { closeReason.await() }
                assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
            }
        }

    @Test
    fun `duplicate name refuses the second connection`() =
        testApplication {
            val registry = ConnectionRegistry()
            registry.register("alice", FakeSession())
            application { chatModule(registry) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                val reason = withTimeout(1000) { closeReason.await() }
                assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
            }
        }

    @Test
    fun `invalid client frame produces a typed error frame`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                send(Frame.Text("""{"type":"send","recipient":"bob","body":"\u001B[31mred"}"""))
                val frame = withTimeout(1000) { incoming.receive() }
                assertInstanceOf(Frame.Text::class.java, frame)
                val text = (frame as Frame.Text).readText()
                val serverFrame = Json.decodeFromString(ServerFrame.serializer(), text)
                assertInstanceOf(ServerFrame.Error::class.java, serverFrame)
            }
        }

    @Test
    fun `malformed JSON produces a typed error frame`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                send(Frame.Text("not valid json {{{"))
                val frame = withTimeout(1000) { incoming.receive() }
                assertInstanceOf(Frame.Text::class.java, frame)
                val text = (frame as Frame.Text).readText()
                val serverFrame = Json.decodeFromString(ServerFrame.serializer(), text)
                assertInstanceOf(ServerFrame.Error::class.java, serverFrame)
            }
        }
}

private class FakeSession : Session {
    override suspend fun send(frame: ServerFrame) {}
}
