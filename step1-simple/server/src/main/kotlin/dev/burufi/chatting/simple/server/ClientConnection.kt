package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame

/**
 * One connected client. Narrow so the registry and the fan-out are testable
 * without a socket.
 *
 * [send] must enqueue rather than write, or the slowest reader in a fan-out sets
 * the pace for whoever is sending to all of them.
 */
interface ClientConnection {
    val name: ClientName

    suspend fun send(frame: ServerFrame)
}
