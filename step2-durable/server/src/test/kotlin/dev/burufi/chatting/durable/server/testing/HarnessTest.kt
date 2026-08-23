package dev.burufi.chatting.durable.server.testing

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * The harness testing itself. Every later task leans on these four properties, and each one
 * of them fails silently rather than loudly when it breaks: a test would simply see rows it
 * did not expect, or none of the ones it wrote.
 */
class HarnessTest {
    @Test
    fun `the suite migrates once, whichever class happens to reach the database first`() {
        assertEquals(
            1,
            TestDatabase.migration.migrationsExecuted,
            "the shared container is migrated by the singleton, not once per test class",
        )
    }

    @Test
    fun `a rolled-back write is gone once the block returns, so nothing needs truncating`() {
        val name = unique()

        rollingBack { connection ->
            connection.insertUser(name)
            assertEquals(1, connection.countUsers(name), "the write is visible inside its own transaction")
        }

        assertEquals(0, countCommitted(name), "a test that ends must leave the schema as it found it")
    }

    @Test
    fun `a route reads rows the test never committed, which is what pinning the connection buys`() {
        val name = unique()

        serverTest(countingModule) {
            transaction { exec("insert into users (username, password_hash) values ('$name', 'hash')") }

            // First, that the seed really is uncommitted. Without this the test would pass on a
            // fixture that had quietly committed it, which is the thing being ruled out.
            assertEquals(0, countCommitted(name), "the seed leaked out of the test's transaction")
            assertEquals(
                "1",
                client.get("/users/$name").bodyAsText(),
                "the application pulled its own connection and cannot see the seed",
            )
        }
    }

    @Test
    fun `a route's own write is rolled back too, so a request leaves nothing behind`() {
        val name = unique()

        serverTest(countingModule) {
            assertEquals(HttpStatusCode.Created, client.post("/users/$name").status, "the route should have inserted")
        }

        assertEquals(0, countCommitted(name), "the fixture rolls back what the application wrote, not just the seed")
    }

    @Test
    fun `the pool still hands out independent connections, so a concurrency test can race`() {
        // The opt-out W-10 and W-23 need: one connection is one transaction, so a race has to
        // go around the pinning. It commits, which is why this is the one test that cleans up.
        val name = unique()
        val refusals = ConcurrentLinkedQueue<SQLException>()
        val start = CountDownLatch(1)

        val racers =
            List(2) {
                thread {
                    start.await()
                    try {
                        TestDatabase.pool.connection.use { it.insertUser(name) }
                    } catch (e: SQLException) {
                        refusals += e
                    }
                }
            }
        start.countDown()
        racers.forEach { it.join() }

        try {
            assertEquals(1, refusals.size, "two racing inserts of one username: exactly one must be refused")
            assertEquals(UNIQUE_VIOLATION, refusals.first().sqlState, "refused, but for the wrong reason")
        } finally {
            TestDatabase.pool.connection.use { it.deleteUser(name) }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A unique name per test, so nothing here depends on the order the suite runs in. */
    private fun unique(): String = "harness-${UUID.randomUUID()}"

    private fun countCommitted(name: String): Int = TestDatabase.pool.connection.use { it.countUsers(name) }

    private fun Connection.insertUser(name: String) {
        prepareStatement("insert into users (username, password_hash) values (?, 'hash')").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate()
        }
    }

    private fun Connection.deleteUser(name: String) {
        prepareStatement("delete from users where username = ?").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate()
        }
    }

    private fun Connection.countUsers(name: String): Int =
        prepareStatement("select count(*) from users where username = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"

        /**
         * Stands in for the application W-05 will build. It reads and writes through a bare
         * `transaction { }` — the way a real route will — which is the whole point: what is
         * under test is which connection that resolves to, not the query inside it.
         */
        val countingModule: Application.() -> Unit = {
            routing {
                get("/users/{name}") {
                    val name = checkNotNull(call.parameters["name"])
                    val count =
                        transaction {
                            exec("select count(*) from users where username = '$name'") { rows ->
                                check(rows.next())
                                rows.getInt(1)
                            }
                        }
                    call.respondText(count.toString())
                }
                post("/users/{name}") {
                    val name = checkNotNull(call.parameters["name"])
                    transaction { exec("insert into users (username, password_hash) values ('$name', 'hash')") }
                    call.respond(HttpStatusCode.Created)
                }
            }
        }
    }
}
