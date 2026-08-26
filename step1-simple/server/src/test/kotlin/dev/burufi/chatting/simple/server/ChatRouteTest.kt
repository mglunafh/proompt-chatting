package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
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
                val roster = nextServerFrame()
                assertEquals(ServerFrame.Roster(listOf("alice")), roster)
                send(Frame.Text("""{"type":"send","recipient":"bob","body":"\u001B[31mred"}"""))
                val errorFrame = nextServerFrame()
                assertEquals(ServerFrame.Error("body contains a forbidden control character"), errorFrame)
            }
        }

    @Test
    fun `malformed JSON produces a typed error frame`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                val roster = nextServerFrame()
                assertEquals(ServerFrame.Roster(listOf("alice")), roster)
                send(Frame.Text("not valid json {{{"))
                val errorFrame = nextServerFrame()
                assertEquals(ServerFrame.Error("could not parse frame"), errorFrame)
            }
        }

    @Test
    fun `single client receives Roster as first frame`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                val frame = nextServerFrame()
                assertEquals(ServerFrame.Roster(listOf("alice")), frame)
            }
        }

    @Test
    fun `joiner receives Roster with existing clients and existing clients receive Joined`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }
            val aliceFrames = mutableListOf<ServerFrame>()
            val bobFrames = mutableListOf<ServerFrame>()
            val aliceReady = CompletableDeferred<Unit>()

            coroutineScope {
                val aliceJob =
                    launch {
                        client.webSocket("/chat?name=alice") {
                            aliceFrames += nextServerFrame()
                            aliceReady.complete(Unit)
                            aliceFrames += nextServerFrame()
                        }
                    }

                aliceReady.await()

                val bobJob =
                    launch {
                        client.webSocket("/chat?name=bob") {
                            bobFrames += nextServerFrame()
                        }
                    }

                aliceJob.join()
                bobJob.join()
            }

            assertEquals(
                listOf(
                    ServerFrame.Roster(listOf("alice")),
                    ServerFrame.Joined(name = "bob"),
                ),
                aliceFrames,
            )
            assertEquals(
                listOf(ServerFrame.Roster(listOf("alice", "bob"))),
                bobFrames,
            )
        }

    @Test
    fun `existing client receives Left when another client disconnects`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }
            val aliceFrames = mutableListOf<ServerFrame>()
            val aliceReady = CompletableDeferred<Unit>()
            val bobConnected = CompletableDeferred<Unit>()

            coroutineScope {
                val aliceJob =
                    launch {
                        client.webSocket("/chat?name=alice") {
                            aliceFrames += nextServerFrame()
                            aliceReady.complete(Unit)
                            aliceFrames += nextServerFrame()
                            bobConnected.await()
                            aliceFrames += nextServerFrame()
                        }
                    }

                aliceReady.await()

                val bobJob =
                    launch {
                        client.webSocket("/chat?name=bob") {
                            bobConnected.complete(Unit)
                        }
                    }

                aliceJob.join()
                bobJob.join()
            }

            assertEquals(
                listOf(
                    ServerFrame.Roster(listOf("alice")),
                    ServerFrame.Joined(name = "bob"),
                    ServerFrame.Left(name = "bob"),
                ),
                aliceFrames,
            )
        }

    @Test
    fun `send to connected recipient delivers and echoes to sender`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }
            val aliceFrames = mutableListOf<ServerFrame>()
            val bobFrames = mutableListOf<ServerFrame>()
            val aliceReady = CompletableDeferred<Unit>()

            coroutineScope {
                val aliceJob =
                    launch {
                        client.webSocket("/chat?name=alice") {
                            aliceFrames += nextServerFrame()
                            aliceReady.complete(Unit)
                            aliceFrames += nextServerFrame()
                            send(Frame.Text("""{"type":"send","recipient":"bob","body":"hi bob"}"""))
                            aliceFrames += nextServerFrame()
                        }
                    }

                aliceReady.await()

                val bobJob =
                    launch {
                        client.webSocket("/chat?name=bob") {
                            bobFrames += nextServerFrame()
                            bobFrames += nextServerFrame()
                        }
                    }

                aliceJob.join()
                bobJob.join()
            }

            assertEquals(
                listOf(
                    ServerFrame.Roster(listOf("alice")),
                    ServerFrame.Joined(name = "bob"),
                    ServerFrame.Message(sender = "alice", body = "hi bob"),
                ),
                aliceFrames,
            )
            assertEquals(
                listOf(
                    ServerFrame.Roster(listOf("alice", "bob")),
                    ServerFrame.Message(sender = "alice", body = "hi bob"),
                ),
                bobFrames,
            )
        }

    @Test
    fun `send to disconnected recipient returns Error to sender`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                assertEquals(ServerFrame.Roster(listOf("alice")), nextServerFrame())
                send(Frame.Text("""{"type":"send","recipient":"nobody","body":"hi"}"""))
                assertEquals(
                    ServerFrame.Error("recipient nobody is not connected"),
                    nextServerFrame(),
                )
            }
        }
}

private suspend fun DefaultClientWebSocketSession.nextServerFrame(): ServerFrame {
    val frame = withTimeout(2000) { incoming.receive() }
    check(frame is Frame.Text)
    return Json.decodeFromString(ServerFrame.serializer(), frame.readText())
}

private class FakeSession : Session {
    override suspend fun send(frame: ServerFrame) {}
}
