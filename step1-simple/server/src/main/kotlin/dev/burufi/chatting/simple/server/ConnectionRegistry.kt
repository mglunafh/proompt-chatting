package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry {
    private val connections = ConcurrentHashMap<String, Session>()

    fun register(
        name: String,
        session: Session,
    ): RegistrationOutcome {
        val prior = connections.putIfAbsent(name, session)
        return if (prior == null) {
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
