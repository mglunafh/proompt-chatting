package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionRegistryTest {
    @Test
    fun `register adds the name and returns the roster including it`() =
        runBlocking {
            val registry = ConnectionRegistry()
            val session = RecordingSession()

            val outcome = registry.register("alice", session)

            val registered = assertInstanceOf(RegistrationOutcome.Registered::class.java, outcome)
            assertEquals(listOf("alice"), registered.roster)
            assertEquals(listOf("alice"), registry.roster())
        }

    @Test
    fun `register returns Duplicate when the name is already connected`() =
        runBlocking {
            val registry = ConnectionRegistry()
            registry.register("alice", RecordingSession())

            val outcome = registry.register("alice", RecordingSession())

            assertEquals(RegistrationOutcome.Duplicate, outcome)
            assertEquals(listOf("alice"), registry.roster())
        }

    @Test
    fun `unregister removes the name and returns the session`() =
        runBlocking {
            val registry = ConnectionRegistry()
            val session = RecordingSession()
            registry.register("alice", session)

            val removed = registry.unregister("alice")

            assertSame(session, removed)
            assertEquals(emptyList<String>(), registry.roster())
        }

    @Test
    fun `unregister returns null when the name is not connected`() {
        val registry = ConnectionRegistry()

        val removed = registry.unregister("nobody")

        assertNull(removed)
    }

    @Test
    fun `roster lists all connected names sorted`() =
        runBlocking {
            val registry = ConnectionRegistry()
            registry.register("charlie", RecordingSession())
            registry.register("alice", RecordingSession())
            registry.register("bob", RecordingSession())

            assertEquals(listOf("alice", "bob", "charlie"), registry.roster())
        }

    @Test
    fun `sendTo delivers a frame to the named session`() =
        runBlocking {
            val registry = ConnectionRegistry()
            val alice = RecordingSession()
            val bob = RecordingSession()
            registry.register("alice", alice)
            registry.register("bob", bob)

            val ok = registry.sendTo("bob", ServerFrame.Message(sender = "alice", body = "hi"))

            assertTrue(ok)
            assertEquals(listOf<ServerFrame>(ServerFrame.Message("alice", "hi")), bob.sent)
            assertEquals(emptyList<ServerFrame>(), alice.sent)
        }

    @Test
    fun `sendTo returns false when the name is not connected`() =
        runBlocking {
            val registry = ConnectionRegistry()

            val ok = registry.sendTo("nobody", ServerFrame.Error(reason = "x"))

            assertFalse(ok)
        }

    @Test
    fun `broadcastExcept delivers to every session except the named one`() =
        runBlocking {
            val registry = ConnectionRegistry()
            val alice = RecordingSession()
            val bob = RecordingSession()
            val carol = RecordingSession()
            registry.register("alice", alice)
            registry.register("bob", bob)
            registry.register("carol", carol)

            val frame = ServerFrame.Joined(name = "alice")
            registry.broadcastExcept("alice", frame)

            assertEquals(emptyList<ServerFrame>(), alice.sent)
            assertEquals(listOf<ServerFrame>(frame), bob.sent)
            assertEquals(listOf<ServerFrame>(frame), carol.sent)
        }

    @Test
    fun `concurrent inserts with unique names all succeed and roster contains all`() {
        val registry = ConnectionRegistry()

        runBlocking(Dispatchers.Default) {
            val outcomes =
                (1..200)
                    .map { i ->
                        async { registry.register("user-$i", RecordingSession()) }
                    }.awaitAll()

            assertTrue(outcomes.all { it is RegistrationOutcome.Registered })
            assertEquals(200, registry.roster().size)
            assertEquals((1..200).map { "user-$it" }.sorted(), registry.roster())
        }
    }

    @Test
    fun `concurrent inserts with the same name only one wins`() {
        val registry = ConnectionRegistry()

        runBlocking(Dispatchers.Default) {
            val outcomes =
                (1..200)
                    .map {
                        async { registry.register("alice", RecordingSession()) }
                    }.awaitAll()

            val registered = outcomes.filterIsInstance<RegistrationOutcome.Registered>()
            val duplicates = outcomes.filterIsInstance<RegistrationOutcome.Duplicate>()

            assertEquals(1, registered.size)
            assertEquals(199, duplicates.size)
            assertEquals(listOf("alice"), registry.roster())
        }
    }
}

private class RecordingSession : Session {
    val sent: MutableList<ServerFrame> = java.util.Collections.synchronizedList(mutableListOf())

    override suspend fun send(frame: ServerFrame) {
        sent += frame
    }
}
