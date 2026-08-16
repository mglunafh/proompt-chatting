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
    val router = MessageRouter(registry)

    webSocket(Endpoint.PATH) {
        val name = admit() ?: return@webSocket
        val connection = SocketConnection(name, this)

        when (val registration = registry.register(connection)) {
            Registration.NameTaken -> refuse(ErrorCode.NAME_TAKEN, "'$name' is already connected")

            is Registration.Admitted -> {
                // The roster is already queued, put there by the registration itself.
                val writer = launch { connection.writeLoop() }
                try {
                    registration.others.broadcast(ServerFrame.UserJoined(name.value))
                    readLoop(connection, router)
                } finally {
                    // Shielded because the writer is a child of this scope: a write
                    // loop that fails cancels the scope its own cleanup runs in,
                    // and unregister suspends on the registry's mutex. Unshielded,
                    // that path ends with the name held by nobody.
                    withContext(NonCancellable) {
                        when (val departure = registry.unregister(connection)) {
                            is Departure.Removed ->
                                departure.remaining.broadcast(ServerFrame.UserLeft(name.value))

                            Departure.NotConnected -> Unit
                        }
                        connection.closeOutbound()
                        writer.join()
                    }
                }
            }
        }
    }
}

/** Queue [frame] for everyone here. Each send enqueues, so one slow client holds up nobody. */
private suspend fun List<ClientConnection>.broadcast(frame: ServerFrame) {
    forEach { it.send(frame) }
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
private suspend fun DefaultWebSocketServerSession.readLoop(
    connection: SocketConnection,
    router: MessageRouter,
) {
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
            is ClientFrame.Send -> router.route(connection, clientFrame)
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
