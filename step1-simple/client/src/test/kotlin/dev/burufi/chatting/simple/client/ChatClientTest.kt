package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.server.module
import dev.burufi.chatting.simple.shared.Endpoint
import dev.burufi.chatting.simple.shared.TestCharacters.BIDI_OVERRIDE
import dev.burufi.chatting.simple.shared.TestCharacters.REPLACEMENT
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client against the real server over a real socket. What a person at a terminal
 * would see is the whole contract.
 */
class ChatClientTest {
    @Test
    fun `the first line says who is here`() =
        chat { room ->
            join(room.port, "alice").expect("nobody else is here")
        }

    @Test
    fun `a later client is told who is here, and the earlier one that it arrived`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")

            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")
        }

    @Test
    fun `a message shows on both ends, and reads differently on each`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")

            alice.type("@bob hi there")
            bob.expect("alice: hi there")
            alice.expect("-> bob: hi there")
        }

    @Test
    fun `a refusal from the server is printed and the client carries on`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")

            alice.type("@nobody hi")
            alice.expect("! unknown_recipient: 'nobody' is not connected")

            // Still usable afterwards, which is what a typed error frame is for.
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")
            alice.type("@bob hi")
            bob.expect("alice: hi")
        }

    @Test
    fun `a line naming nobody is refused without troubling the server`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")

            alice.type("hello everyone")
            assertTrue(alice.next()!!.startsWith("! start a message with @"), "the line was not refused locally")

            // Bob hearing nothing is what says the line never reached the server.
            bob.expectSilence()
        }

    @Test
    fun `a name already connected is refused and never admitted`() =
        chat { room ->
            join(room.port, "alice").expect("nobody else is here")

            val other = join(room.port, "alice")
            other.expect("! name_taken: 'alice' is already connected")
            assertFalse(other.admitted(), "a refused client reported itself admitted")
        }

    @Test
    fun `exit closes the connection and the room is told`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")

            alice.type("/exit")

            withTimeout(BUDGET) { alice.job.join() }
            assertTrue(alice.admitted(), "alice was admitted before leaving")
            // Bob hearing it is what says the socket really closed, rather than the
            // client merely stopping on its own side.
            bob.expect("alice left")
        }

    @Test
    fun `help is answered by the client without troubling the server`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")

            alice.type("/help")
            assertTrue(alice.next()!!.contains("/exit"), "the help does not list the commands")

            bob.expectSilence()
        }

    @Test
    fun `text the server lets through is still rendered inertly`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")
            val bob = join(room.port, "bob")
            bob.expect("here: alice")
            alice.expect("bob joined")

            // A bidi override is legitimate in a body and the server passes it, which is
            // exactly why rendering it inertly is the client's job.
            alice.type("@bob a${BIDI_OVERRIDE}b")
            bob.expect("alice: a${REPLACEMENT}b")

            // Newlines are legitimate too, and are all it takes to forge a line.
            alice.type("@bob hi\nbob: trust me")
            bob.expect("alice: hi\n    bob: trust me")
        }

    @Test
    fun `the client stops when the server does`() =
        chat { room ->
            val alice = join(room.port, "alice")
            alice.expect("nobody else is here")

            room.stop()

            // No reconnect: the socket closing is the end of the client.
            withTimeout(BUDGET) { alice.job.join() }
            assertTrue(alice.admitted(), "alice was admitted before the server went away")
        }

    private class Room(
        val port: Int,
        val stop: () -> Unit,
    )

    private fun chat(block: suspend CoroutineScope.(Room) -> Unit) =
        runBlocking {
            val server = embeddedServer(ServerCIO, port = 0, module = Application::module)
            server.start(wait = false)
            val stop = { server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000) }
            try {
                val port =
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                coroutineScope {
                    block(Room(port, stop))
                    // A chatter runs until its socket closes, which in most of these is
                    // never. The test ending is what ends them.
                    coroutineContext.cancelChildren()
                }
            } finally {
                stop()
            }
        }

    private fun CoroutineScope.join(
        port: Int,
        name: String,
    ): Chatter {
        val typed = Channel<String>(Channel.UNLIMITED)
        val printed = Channel<String>(Channel.UNLIMITED)
        val admitted = CompletableDeferred<Boolean>()

        val job =
            launch {
                try {
                    HttpClient(CIO) { install(WebSockets) }.use { http ->
                        http.webSocket("ws://localhost:$port${Endpoint.PATH}?${Endpoint.NAME_PARAM}=$name") {
                            admitted.complete(ChatClient(clientName(name), typed, { printed.trySend(it) }).run(this))
                        }
                    }
                } finally {
                    // A handshake that never happened is not an admission either.
                    admitted.complete(false)
                    printed.close()
                }
            }

        return Chatter(typed, printed, admitted, job)
    }

    private class Chatter(
        private val typed: SendChannel<String>,
        private val printed: ReceiveChannel<String>,
        private val admittance: CompletableDeferred<Boolean>,
        val job: Job,
    ) {
        suspend fun type(line: String) = typed.send(line)

        suspend fun next(): String? = withTimeout(BUDGET) { printed.receiveCatching().getOrNull() }

        suspend fun expect(line: String) = assertEquals(line, next())

        suspend fun expectSilence() = assertNull(withTimeoutOrNull(QUIET) { printed.receiveCatching().getOrNull() })

        suspend fun admitted(): Boolean = withTimeout(BUDGET) { admittance.await() }
    }

    private companion object {
        /** Generous: it exists to fail a hung test rather than to time anything. */
        val BUDGET = 5.seconds

        /** How long to wait before believing nothing is coming. */
        val QUIET = 250.milliseconds
    }
}
