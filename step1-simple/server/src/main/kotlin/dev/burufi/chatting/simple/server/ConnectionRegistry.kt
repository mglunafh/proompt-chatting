package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

sealed interface Registration {
    /** [roster] and [others] are everyone *else*, captured in the same step as the insert. */
    data class Admitted(
        val roster: List<ClientName>,
        val others: List<ClientConnection>,
    ) : Registration

    data object NameTaken : Registration
}

/** The connected clients, by name. */
class ConnectionRegistry {
    private val connections = ConcurrentHashMap<ClientName, ClientConnection>()
    private val mutation = Mutex()

    /**
     * File [connection] under its own name, refusing a name already held.
     *
     * The roster is captured in the same guarded step as the insert, so nothing
     * can change between the snapshot a client is sent and the first delta it
     * sees.
     */
    suspend fun register(connection: ClientConnection): Registration =
        mutation.withLock {
            if (connections.containsKey(connection.name)) {
                // Never a replace: that would drop a live client for whoever asked second.
                return@withLock Registration.NameTaken
            }
            val others = connections.values.sortedBy { it.name }
            connections[connection.name] = connection
            Registration.Admitted(others.map { it.name }, others)
        }

    /**
     * Drop [connection], returning true only for the call that removed it, so a
     * leave is broadcast once.
     *
     * Matches on the object rather than the name: a late unregister from a
     * dropped socket must not evict a client that reconnected under it.
     */
    suspend fun unregister(connection: ClientConnection): Boolean =
        mutation.withLock {
            // Matches on an object so the reconnections from the dropped socket
            // do not get evicted by a late unregister
            if (connections[connection.name] !== connection) {
                return@withLock false
            }
            connections.remove(connection.name)
            true
        }

    /** Who to write to for [name], or null if nobody is connected under it. */
    fun lookup(name: ClientName): ClientConnection? = connections[name]

    /** Who is connected as of this call, sorted. */
    fun names(): List<ClientName> = connections.keys.sorted()
}
