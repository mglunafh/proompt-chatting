package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.Endpoint
import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.ProtocolJson
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.Validated
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * The one route. A client arrives naming itself and either holds the socket for the
 * length of its stay, or is told why it may not.
 */
fun Route.chatRoute(registry: ConnectionRegistry) {
    webSocket(Endpoint.PATH) {
        val name = admit() ?: return@webSocket
        val connection = SocketConnection(name, this)

        when (registry.register(connection)) {
            Registration.NameTaken -> refuse(ErrorCode.NAME_TAKEN, "'$name' is already connected")

            is Registration.Admitted -> {
                val writer = launch { connection.writeLoop() }
                try {
                    // W-06 sends the roster here, and fans a join out to the others.
                    readLoop(connection)
                } finally {
                    // Shielded because the writer is a child of this scope: a write
                    // loop that fails cancels the scope its own cleanup runs in,
                    // and unregister suspends on the registry's mutex. Unshielded,
                    // that path ends with the name held by nobody. Nothing writes on
                    // this path yet, so it is W-06 and W-07 that make it reachable.
                    withContext(NonCancellable) {
                        registry.unregister(connection) // W-06 broadcasts a leave on true
                        connection.closeOutbound()
                        writer.join()
                    }
                }
            }
        }
    }
}

/** The name this client may hold, or null once it has been told why it may not. */
private suspend fun DefaultWebSocketServerSession.admit(): ClientName? {
    val raw = call.request.queryParameters[Endpoint.NAME_PARAM]
    if (raw == null) {
        refuse(ErrorCode.INVALID_NAME, "connect with ?${Endpoint.NAME_PARAM}=<name>")
        return null
    }

    return when (val validated = ClientName.of(raw)) {
        is Validated.Invalid -> {
            refuse(validated.code, validated.reason)
            null
        }

        is Validated.Valid -> validated.value
    }
}

/**
 * Read until the socket closes.
 *
 * A frame that does not decode is answered and the connection survives it, which is
 * what makes a refusal a frame rather than a close.
 */
private suspend fun DefaultWebSocketServerSession.readLoop(connection: SocketConnection) {
    for (frame in incoming) {
        // Binary is not this wire, and ping, pong and close never reach here.
        val text = (frame as? Frame.Text)?.readText() ?: continue

        val clientFrame =
            try {
                ProtocolJson.STRICT.decodeFromString(ProtocolJson.CLIENT_FRAME, text)
            } catch (_: SerializationException) {
                connection.send(ServerFrame.Error(ErrorCode.MALFORMED_FRAME, "the frame did not decode"))
                continue
            }

        when (clientFrame) {
            // W-07 validates the body and writes the message to the recipient and back.
            is ClientFrame.Send -> Unit
        }
    }
}

/**
 * Turn a client away with a reason it can read, before the connection exists.
 */
private suspend fun DefaultWebSocketServerSession.refuse(
    code: ErrorCode,
    reason: String,
) {
    val error: ServerFrame = ServerFrame.Error(code, reason)
    send(Frame.Text(ProtocolJson.STRICT.encodeToString(ProtocolJson.SERVER_FRAME, error)))
    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, code.name))
}
