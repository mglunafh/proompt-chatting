package dev.burufi.chatting.durable.server.testing

import dev.burufi.chatting.durable.server.db.DatabaseUtils
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Boots an application against the shared container, with the database pinned to one
 * connection the fixture rolls back at the end. A route reads and writes the same
 * transaction the test seeds in, and nothing survives the test.
 *
 * The module is a parameter because W-05 owns the real one; until it exists a test supplies
 * its own, and W-05 then passes the real module in without touching anything here.
 */
fun serverTest(
    module: Application.() -> Unit,
    block: suspend ApplicationTestBuilder.() -> Unit,
) {
    TestDatabase.pool.connection.use { shared ->
        shared.autoCommit = false
        val pinned = DatabaseUtils.connect(SharedConnectionDataSource(shared))
        val previous = TransactionManager.defaultDatabase
        TransactionManager.defaultDatabase = pinned
        try {
            testApplication {
                application(module)
                block()
            }
        } finally {
            TransactionManager.defaultDatabase = previous
            TransactionManager.closeAndUnregister(pinned)
            shared.rollback()
        }
    }
}
