package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

sealed interface Registration {
    /** [others] is everyone *else*, captured in the same step as the insert. */
    data class Admitted(
        val others: List<ClientConnection>,
    ) : Registration

    data object NameTaken : Registration
}

sealed interface Departure {
    /** [remaining] is everyone still connected, captured in the same step as the removal. */
    data class Removed(
        val remaining: List<ClientConnection>,
    ) : Departure

    data object NotConnected : Departure
}

/** The connected clients, by name. */
class ConnectionRegistry {
    private val connections = ConcurrentHashMap<ClientName, ClientConnection>()
    private val mutation = Mutex()

    /**
     * File [connection] under its own name, refusing a name already held.
     *
     * The roster is both captured and sent inside the guard, so no delta can
     * reach the new client ahead of the snapshot it is meant to build on.
     */
    suspend fun register(connection: ClientConnection): Registration =
        mutation.withLock {
            if (connections.containsKey(connection.name)) {
                // Never a replace: that would drop a live client for whoever asked second.
                return@withLock Registration.NameTaken
            }
            val others = connections.values.sortedBy { it.name }
            connections[connection.name] = connection
            connection.send(ServerFrame.Roster(others.map { it.name.value }))
            Registration.Admitted(others)
        }

    /**
     * Drop [connection], answering [Departure.Removed] only for the call that
     * removed it, so a leave is broadcast once.
     *
     * Matches on the object rather than the name: a late unregister from a
     * dropped socket must not evict a client that reconnected under it.
     */
    suspend fun unregister(connection: ClientConnection): Departure =
        mutation.withLock {
            // Matches on an object so the reconnections from the dropped socket
            // do not get evicted by a late unregister
            if (connections[connection.name] !== connection) {
                return@withLock Departure.NotConnected
            }
            connections.remove(connection.name)
            Departure.Removed(connections.values.sortedBy { it.name })
        }

    /** Who to write to for [name], or null if nobody is connected under it. */
    fun lookup(name: ClientName): ClientConnection? = connections[name]

    /** Who is connected as of this call, sorted. */
    fun names(): List<ClientName> = connections.keys.sorted()
}
