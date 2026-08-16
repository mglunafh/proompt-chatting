package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

/**
 * The queue between a fan-out and one socket. W-06 made these paths reachable for the
 * first time, so they are pinned here rather than left as insurance nothing exercises.
 */
class SocketConnectionTest {
    @Test
    fun `queued frames reach the socket, and the loop ends once nothing more will be sent`() =
        runBlocking {
            val session = FakeWebSocketSession()
            val connection = SocketConnection(clientName("alice"), session)

            connection.send(ServerFrame.UserJoined("bob"))
            connection.send(ServerFrame.UserLeft("bob"))
            connection.closeOutbound()
            connection.writeLoop()

            // Asserted as text because encoding through the base type is what puts the
            // discriminator in: encode a subtype and the client cannot tell them apart.
            assertEquals(
                listOf("""{"type":"user_joined","name":"bob"}""", """{"type":"user_left","name":"bob"}"""),
                session.text(),
            )
        }

    @Test
    fun `a client that has stopped reading is dropped rather than accumulated`() =
        runBlocking {
            val session = FakeWebSocketSession()
            val connection = SocketConnection(clientName("alice"), session)

            // No writer running, so the queue is exactly the buffer and the count is a
            // fact rather than a question of timing.
            repeat(SocketConnection.OUTBOUND_CAPACITY) { connection.send(ServerFrame.UserJoined("client-$it")) }
            assertTrue(session.frames().isEmpty(), "frames reached the socket with nothing draining the queue")

            connection.send(ServerFrame.UserJoined("one-too-many"))

            val close = session.frames().filterIsInstance<Frame.Close>().singleOrNull()
            assertNotNull(close, "the client that overflowed its queue was left connected")
            assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, close!!.readReason()?.code)
        }

    @Test
    fun `a write loop whose socket is already gone ends instead of failing the connection`() =
        runBlocking {
            val session = FakeWebSocketSession()
            val connection = SocketConnection(clientName("alice"), session)
            connection.send(ServerFrame.UserJoined("bob"))
            session.dropSocket()

            // Returning is the assertion. The loop runs as a child of the session, so a
            // throw here would fail the whole connection to report a client that left.
            connection.writeLoop()
        }
}

/** A session that records rather than writes, and can be made to look like a dead socket. */
private class FakeWebSocketSession : WebSocketSession {
    private val written = Channel<Frame>(Channel.UNLIMITED)
    private val drained = mutableListOf<Frame>()

    override val coroutineContext: CoroutineContext = Job()
    override val incoming: ReceiveChannel<Frame> = Channel()
    override val outgoing: SendChannel<Frame> = written
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    override suspend fun flush() = Unit

    @Deprecated("Use cancel() instead", replaceWith = ReplaceWith("cancel()", "kotlinx.coroutines.cancel"))
    override fun terminate() = dropSocket()

    /** What the socket has been given so far, accumulated so this can be asked twice. */
    fun frames(): List<Frame> {
        while (true) drained += written.tryReceive().getOrNull() ?: break
        return drained.toList()
    }

    fun text(): List<String> = frames().filterIsInstance<Frame.Text>().map { it.readText() }

    /** Make the next write throw, which is what a socket closing under the writer looks like. */
    fun dropSocket() {
        written.close()
    }
}
