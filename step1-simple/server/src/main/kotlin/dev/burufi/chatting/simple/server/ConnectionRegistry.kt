package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry {
    private val connections = ConcurrentHashMap<String, Session>()
    private val mutex = Mutex()

    suspend fun register(
        name: String,
        session: Session,
    ): RegistrationOutcome =
        mutex.withLock {
            val prior = connections.putIfAbsent(name, session)
            if (prior == null) {
                RegistrationOutcome.Registered(roster())
            } else {
                RegistrationOutcome.Duplicate
            }
        }

    fun unregister(name: String): Session? = connections.remove(name)

    fun roster(): List<String> = connections.keys.sorted()

    suspend fun sendTo(
        name: String,
        frame: ServerFrame,
    ): Boolean {
        val session = connections[name] ?: return false
        session.send(frame)
        return true
    }

    suspend fun broadcastExcept(
        name: String,
        frame: ServerFrame,
    ) {
        val snapshot = connections.entries.toList()
        for ((other, session) in snapshot) {
            if (other != name) session.send(frame)
        }
    }
}

sealed interface RegistrationOutcome {
    data class Registered(
        val roster: List<String>,
    ) : RegistrationOutcome

    data object Duplicate : RegistrationOutcome
}
