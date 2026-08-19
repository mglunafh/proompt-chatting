package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.Endpoint
import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.Limits
import dev.burufi.chatting.simple.shared.ProtocolJson
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.TestCharacters.ESC
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The endpoint over a real socket. Nothing here reaches into the registry: what a
 * client can observe from the outside is the whole contract.
 */
class ChatEndpointTest {
    @Test
    fun `a name that is not a name is refused`() =
        chat { client ->
            client.webSocket(url("AB")) {
                expectError(ErrorCode.INVALID_NAME)
                expectClosed()
            }
        }

    @Test
    fun `a missing name is refused`() =
        chat { client ->
            client.webSocket(Endpoint.PATH) {
                expectError(ErrorCode.INVALID_NAME)
                expectClosed()
            }
        }

    @Test
    fun `a reserved name is refused`() =
        chat { client ->
            client.webSocket(url("admin")) {
                expectError(ErrorCode.INVALID_NAME)
                expectClosed()
            }
        }

    @Test
    fun `the first frame after the upgrade is the roster`() =
        chat { client ->
            client.webSocket(url("alice")) { expectRoster() }
        }

    @Test
    fun `a later client's roster names who is already here, sorted`() =
        chat { client ->
            val carol = client.join("carol")
            carol.expectRoster()
            client.join("alice").expectRoster("carol")
            client.join("bob").expectRoster("alice", "carol")
            client.join("dave").expectRoster("alice", "bob", "carol")
        }

    @Test
    fun `a join reaches everyone already here`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            client.join("bob").expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))
        }

    @Test
    fun `a client is not told about its own join`() =
        chat { client ->
            client.join("alice").expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            bob.expectSilence()
        }

    @Test
    fun `a leave reaches everyone still here`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            bob.close()
            alice.expectFrame(ServerFrame.UserLeft("bob"))
        }

    @Test
    fun `a name already connected is refused`() =
        chat { client ->
            client.join("alice").expectRoster()

            client.webSocket(url("alice")) {
                expectError(ErrorCode.NAME_TAKEN)
                expectClosed()
            }
        }

    @Test
    fun `a refused duplicate is announced to nobody`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            client.webSocket(url("alice")) { expectError(ErrorCode.NAME_TAKEN) }
            alice.expectSilence()
        }

    @Test
    fun `a name is free again once its client leaves`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            // Alice seeing the leave is the server saying the unregister is done, which
            // is what makes reclaiming the name deterministic rather than a race
            // against cleanup.
            bob.close()
            alice.expectFrame(ServerFrame.UserLeft("bob"))

            client.join("bob").expectRoster("alice")
        }

    @Test
    fun `a client that drops without closing still frees its name`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            // No close handshake: the socket dies rather than being shut down
            // politely, which is what a killed terminal looks like from here.
            bob.cancel()
            alice.expectFrame(ServerFrame.UserLeft("bob"))

            client.join("bob").expectRoster("alice")
        }

    @Test
    fun `a message reaches the recipient and comes back to the sender`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            alice.sendTo("bob", "hi")
            val message = ServerFrame.Message("alice", "bob", "hi")
            bob.expectFrame(message)
            alice.expectFrame(message)
        }

    @Test
    fun `a message goes to its recipient and to nobody else`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")

            val carol = client.join("carol")
            carol.expectRoster("alice", "bob")
            bob.expectFrame(ServerFrame.UserJoined("carol"))

            alice.sendTo("bob", "hi")
            bob.expectFrame(ServerFrame.Message("alice", "bob", "hi"))
            carol.expectSilence()
        }

    @Test
    fun `a send to oneself arrives once`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                sendTo("alice", "note")

                // Delivery and echo are the same client here, and comparing `from` with
                // its own name cannot tell them apart.
                expectFrame(ServerFrame.Message("alice", "alice", "note"))
                expectSilence()
            }
        }

    @Test
    fun `a recipient that never connected is refused`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                sendTo("dave", "hi")
                expectError(ErrorCode.UNKNOWN_RECIPIENT)

                // A second refusal is what proves the first was not a close.
                sendTo("dave", "hi")
                expectError(ErrorCode.UNKNOWN_RECIPIENT)
            }
        }

    @Test
    fun `a recipient that has left is refused`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            // Waiting on the leave is what makes this the departed case rather than a
            // race against cleanup.
            bob.close()
            alice.expectFrame(ServerFrame.UserLeft("bob"))

            alice.sendTo("bob", "hi")
            alice.expectError(ErrorCode.UNKNOWN_RECIPIENT)
        }

    @Test
    fun `a body that breaks a rule is refused and the connection survives`() =
        chat { client ->
            val alice = client.join("alice")
            alice.expectRoster()

            val bob = client.join("bob")
            bob.expectRoster("alice")
            alice.expectFrame(ServerFrame.UserJoined("bob"))

            alice.sendTo("bob", "a".repeat(Limits.MAX_BODY_BYTES + 1))
            alice.expectError(ErrorCode.BODY_TOO_LARGE)

            alice.sendTo("bob", "clear$ESC[2J")
            alice.expectError(ErrorCode.BODY_INVALID_CHARACTERS)

            // Bob's first frame being the third send is what proves the refused two were
            // answered rather than delivered, and that neither closed anything.
            alice.sendTo("bob", "hi")
            bob.expectFrame(ServerFrame.Message("alice", "bob", "hi"))
        }

    @Test
    fun `Sanity check - no delta ever arrives before the roster`() =
        chat { client ->
            repeat(ROUNDS) { round ->
                coroutineScope {
                    val firsts =
                        (0 until RACERS)
                            .map { racer ->
                                async {
                                    var first: ServerFrame? = null
                                    client.webSocket(url("r${round}x$racer")) { first = nextFrame() }
                                    first
                                }
                            }.awaitAll()

                    firsts.forEach { assertTrue(it is ServerFrame.Roster, "first frame was $it, not a roster") }
                }
            }
        }

    @Test
    fun `a frame that does not decode is refused and the connection survives`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                send(Frame.Text("not json at all"))
                expectError(ErrorCode.MALFORMED_FRAME)

                // A second refusal is what proves the first was not a close.
                send(Frame.Text("""{"type":"no_such_frame"}"""))
                expectError(ErrorCode.MALFORMED_FRAME)
            }
        }

    @Test
    fun `an unknown key is refused rather than dropped`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                send(Frame.Text("""{"type":"send","to":"bob","body":"hi","extra":1}"""))
                expectError(ErrorCode.MALFORMED_FRAME)
            }
        }

    @Test
    fun `a refusal does not quote the frame it refused`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                send(Frame.Text("""{"type":"send","to":"bob","body":"hi","telltale":1}"""))
                val error = expectError(ErrorCode.MALFORMED_FRAME)

                // The refusal travels back out to a terminal. Quoting the payload
                // would carry whatever the sender put in it to whoever renders it.
                assertFalse(error.reason.contains("telltale"), "the refusal quoted the frame: ${error.reason}")
            }
        }

    @Test
    fun `a binary frame is ignored`() =
        chat { client ->
            client.webSocket(url("alice")) {
                expectRoster()
                send(Frame.Binary(true, byteArrayOf(1, 2, 3)))

                // The loop answering this is what proves the binary frame did not end it.
                send(Frame.Text("not json at all"))
                expectError(ErrorCode.MALFORMED_FRAME)
            }
        }

    /** The application, plus a client that can upgrade. */
    private fun chat(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module() }
            block(createClient { install(WebSockets) })
        }

    private fun url(name: String): String = "${Endpoint.PATH}?${Endpoint.NAME_PARAM}=$name"

    /** A connection that stays open, so several clients can watch each other. */
    private suspend fun HttpClient.join(name: String): DefaultClientWebSocketSession = webSocketSession(url(name))

    private suspend fun WebSocketSession.sendTo(
        to: String,
        body: String,
    ) = send(Frame.Text(ProtocolJson.STRICT.encodeToString(ProtocolJson.CLIENT_FRAME, ClientFrame.Send(to, body))))

    private suspend fun WebSocketSession.nextFrame(): ServerFrame {
        val frame = withTimeout(BUDGET) { incoming.receive() }
        assertTrue(frame is Frame.Text, "expected a text frame, got $frame")
        return ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.SERVER_FRAME, (frame as Frame.Text).readText())
    }

    private suspend fun WebSocketSession.expectFrame(expected: ServerFrame) = assertEquals(expected, nextFrame())

    private suspend fun WebSocketSession.expectRoster(vararg names: String) = expectFrame(ServerFrame.Roster(names.toList()))

    private suspend fun WebSocketSession.expectError(code: ErrorCode): ServerFrame.Error {
        val frame = nextFrame()
        assertTrue(frame is ServerFrame.Error, "expected an error, got $frame")
        assertEquals(code, (frame as ServerFrame.Error).code)
        return frame
    }

    private suspend fun WebSocketSession.expectSilence() {
        val frame = withTimeoutOrNull(QUIET) { incoming.receive() }
        assertNull(frame, "the server sent something it should not have")
    }

    private suspend fun DefaultClientWebSocketSession.expectClosed() {
        val reason = withTimeout(BUDGET) { closeReason.await() }
        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code, "closed as $reason")
    }

    private companion object {
        /** Generous: it exists to fail a hung test rather than to time anything. */
        val BUDGET = 5.seconds

        /** How long to wait before believing nothing is coming. */
        val QUIET = 250.milliseconds

        const val ROUNDS = 10
        const val RACERS = 8
    }
}
