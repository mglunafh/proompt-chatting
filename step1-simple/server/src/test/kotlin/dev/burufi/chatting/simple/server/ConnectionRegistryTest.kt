package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionRegistryTest {
    private val registry = ConnectionRegistry()

    @Test
    fun `the first client in sees an empty roster`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            val admitted = registry.register(alice) as Registration.Admitted
            assertEquals(emptyList<String>(), alice.roster())
            assertEquals(emptyList<ClientConnection>(), admitted.others)
        }

    @Test
    fun `a later client sees who is already connected, and not itself`() =
        runBlocking {
            registry.register(RecordingConnection("alice"))
            val bob = RecordingConnection("bob")
            registry.register(bob)
            assertEquals(listOf("alice"), bob.roster())
        }

    @Test
    fun `the roster is sorted whatever order clients connected in`() =
        runBlocking {
            listOf("carol", "alice", "bob").forEach { registry.register(RecordingConnection(it)) }
            val dave = RecordingConnection("dave")
            registry.register(dave)
            assertEquals(listOf("alice", "bob", "carol"), dave.roster())
        }

    @Test
    fun `others are the connections the roster names`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            val bob = RecordingConnection("bob")
            registry.register(alice)
            registry.register(bob)

            val carol = RecordingConnection("carol")
            val admitted = registry.register(carol) as Registration.Admitted
            assertEquals(listOf(alice, bob), admitted.others)
            assertEquals(admitted.others.map { it.name.value }, carol.roster())
        }

    @Test
    fun `a name already held is refused`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            registry.register(alice)

            assertEquals(Registration.NameTaken, registry.register(RecordingConnection("alice")))
            assertSame(alice, registry.lookup(clientName("alice")), "the refusal replaced the connected client")
            assertEquals(names("alice"), registry.names())
        }

    @Test
    fun `a name is free again once its client leaves`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            registry.register(alice)

            assertTrue(registry.unregister(alice) is Departure.Removed, "the connected client was not removed")
            assertEquals(emptyList<ClientName>(), registry.names())
            assertTrue(registry.register(RecordingConnection("alice")) is Registration.Admitted)
        }

    @Test
    fun `unregister reports the removal once`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            registry.register(alice)

            assertTrue(registry.unregister(alice) is Departure.Removed)
            assertEquals(Departure.NotConnected, registry.unregister(alice), "the same connection was removed twice")
        }

    @Test
    fun `unregister reports who is left to tell`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            val bob = RecordingConnection("bob")
            val carol = RecordingConnection("carol")
            listOf(carol, alice, bob).forEach { registry.register(it) }

            val departure = registry.unregister(bob) as Departure.Removed
            assertEquals(listOf(alice, carol), departure.remaining, "the leaver is not told about itself")
        }

    @Test
    fun `a superseded connection cannot evict the one that replaced it`() =
        runBlocking {
            val first = RecordingConnection("alice")
            val second = RecordingConnection("alice")
            registry.register(first)
            registry.unregister(first)
            registry.register(second)

            assertEquals(Departure.NotConnected, registry.unregister(first), "a dead connection evicted a live one")
            assertSame(second, registry.lookup(clientName("alice")))
        }

    @Test
    fun `an unconnected name has no connection`() =
        runBlocking {
            registry.register(RecordingConnection("alice"))
            assertNull(registry.lookup(clientName("bob")))
        }

    @Test
    fun `the registry sends the roster and nothing else`() =
        runBlocking {
            val alice = RecordingConnection("alice")
            val bob = RecordingConnection("bob")
            registry.register(alice)
            registry.register(bob)
            registry.unregister(bob)

            assertEquals(listOf(ServerFrame.Roster(emptyList())), alice.frames(), "the registry must not send joins or leaves")
            assertEquals(listOf(ServerFrame.Roster(listOf("alice"))), bob.frames(), "unregister must not send anything")
        }

    @Test
    fun `one racer wins a contested name and the rest are refused`() {
        repeat(ROUNDS) {
            val registry = ConnectionRegistry()
            val racers = List(RACERS) { RecordingConnection("alice") }
            val outcomes = race(racers) { registry.register(it) }

            val winners = outcomes.filter { (_, outcome) -> outcome is Registration.Admitted }
            assertEquals(1, winners.size, "the name was handed out ${winners.size} times")
            assertEquals(RACERS - 1, outcomes.count { (_, outcome) -> outcome == Registration.NameTaken })
            assertSame(winners.single().first, registry.lookup(clientName("alice")))
            assertEquals(names("alice"), registry.names())
        }
    }

    /**
     * The test that actually proves the snapshot cannot escape the insert: two
     * clients seeing each other, or neither seeing the other, both mean the
     * roster was read outside the step that inserted.
     */
    @Test
    fun `racing registrations are ordered against each other`() {
        repeat(ROUNDS) {
            val registry = ConnectionRegistry()
            val racers = List(RACERS) { RecordingConnection("client-$it") }
            val outcomes = race(racers) { registry.register(it) }

            val rosters =
                outcomes.associate { (connection, outcome) ->
                    assertTrue(outcome is Registration.Admitted, "$connection was refused a free name")
                    connection.name to connection.roster().toSet()
                }
            assertEquals(RACERS, registry.names().size)

            racers.forEachIndexed { index, a ->
                racers.drop(index + 1).forEach { b ->
                    val aSawB = b.name.value in rosters.getValue(a.name)
                    val bSawA = a.name.value in rosters.getValue(b.name)
                    assertTrue(aSawB != bSawA, "$a and $b did not register one after the other")
                }
            }
        }
    }

    @Test
    fun `racing registrations and removals leave the registry agreeing with itself`() {
        repeat(ROUNDS) {
            val registry = ConnectionRegistry()
            val racers = List(RACERS) { RecordingConnection("alice") }
            val outcomes =
                race(racers) { racer ->
                    val outcome = registry.register(racer)
                    outcome is Registration.Admitted && registry.unregister(racer) is Departure.Removed
                }

            assertTrue(outcomes.any { (_, removed) -> removed }, "nobody ever got in")
            assertEquals(emptyList<ClientName>(), registry.names())
            assertNull(registry.lookup(clientName("alice")))
        }
    }

    private fun names(vararg names: String): List<ClientName> = names.map { clientName(it) }

    /**
     * Runs [action] for every racer at once on a genuinely multi-threaded
     * dispatcher. Not `runTest`, whose dispatcher is single-threaded and would
     * never expose the race these tests exist for.
     */
    private fun <T> race(
        racers: List<RecordingConnection>,
        action: suspend (RecordingConnection) -> T,
    ): List<Pair<RecordingConnection, T>> =
        runBlocking(Dispatchers.Default) {
            val gate = CompletableDeferred<Unit>()
            val racing =
                racers.map { racer ->
                    async {
                        gate.await()
                        racer to action(racer)
                    }
                }
            gate.complete(Unit)
            racing.awaitAll()
        }

    private companion object {
        const val ROUNDS = 50
        const val RACERS = 32
    }
}
