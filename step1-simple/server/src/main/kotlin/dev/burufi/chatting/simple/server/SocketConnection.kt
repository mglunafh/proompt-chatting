package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ProtocolJson
import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

/**
 * A [ClientConnection] over a real socket: a queue and the coroutine that drains it.
 *
 * The queue is what keeps [send] off the socket, so whoever is broadcasting is never
 * held up by the slowest reader among the recipients.
 */
class SocketConnection(
    override val name: ClientName,
    private val session: WebSocketSession,
) : ClientConnection {
    private val outbound = Channel<ServerFrame>(OUTBOUND_CAPACITY)

    /**
     * Enqueue [frame], or drop the client that has stopped reading.
     */
    override suspend fun send(frame: ServerFrame) {
        if (outbound.trySend(frame).isFailure) {
            outbound.close()
            session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "not reading"))
        }
    }

    /**
     * Write queued frames to the socket until [closeOutbound].
     */
    suspend fun writeLoop() {
        try {
            for (frame in outbound) {
                session.send(Frame.Text(ProtocolJson.STRICT.encodeToString(ProtocolJson.SERVER_FRAME, frame)))
            }
        } catch (_: ClosedSendChannelException) {
            // The client left with frames still queued, which is what a disconnect
            // mid-broadcast looks like from here. Caught rather than thrown because
            // this runs as a child of the session: an escaping failure would fail
            // the whole connection to report something entirely ordinary, and the
            // read loop is already ending for the same reason.
        }
    }

    /** Stop [writeLoop], once nothing more will be sent. */
    fun closeOutbound() {
        outbound.close()
    }

    override fun toString(): String = "$name@${hashCode().toString(16)}"

    internal companion object {
        /**
         * Deep enough that ordinary traffic never reaches it, shallow enough that a
         * client which stops reading is noticed rather than accumulated.
         */
        const val OUTBOUND_CAPACITY = 64
    }
}
