package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame

/**
 * One connected client. Narrow so the registry and the fan-out are testable
 * without a socket.
 *
 * [send] must enqueue rather than write: the registry sends the roster while it
 * holds its mutation guard, so a slow socket would stall every registration.
 */
interface ClientConnection {
    val name: ClientName

    suspend fun send(frame: ServerFrame)
}
