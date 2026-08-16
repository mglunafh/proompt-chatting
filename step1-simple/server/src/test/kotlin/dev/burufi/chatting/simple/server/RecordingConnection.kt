package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.Validated
import java.util.concurrent.CopyOnWriteArrayList

/** A name the test asserts is valid, since only [ClientName.of] can make one. */
fun clientName(raw: String): ClientName =
    when (val result = ClientName.of(raw)) {
        is Validated.Valid -> result.value
        is Validated.Invalid -> error("'$raw' is not a usable test name: ${result.reason}")
    }

/**
 * Deliberately not a data class: the registry tells connections apart by
 * identity, and two clients offering one name is the case under test.
 */
class RecordingConnection(
    name: String,
) : ClientConnection {
    override val name: ClientName = clientName(name)

    private val received = CopyOnWriteArrayList<ServerFrame>()

    override suspend fun send(frame: ServerFrame) {
        received += frame
    }

    fun frames(): List<ServerFrame> = received.toList()

    /** The roster this connection was sent, which is always the first thing it gets. */
    fun roster(): List<String> =
        when (val first = frames().firstOrNull()) {
            is ServerFrame.Roster -> first.names
            else -> error("$this was sent $first before any roster")
        }

    override fun toString(): String = "$name@${hashCode().toString(16)}"
}
