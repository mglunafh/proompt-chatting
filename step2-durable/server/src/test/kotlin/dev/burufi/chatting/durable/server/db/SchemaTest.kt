package dev.burufi.chatting.durable.server.db

import dev.burufi.chatting.durable.server.db.schema.schemaTables
import dev.burufi.chatting.durable.server.testing.TestDatabase
import dev.burufi.chatting.durable.server.testing.rollingBack
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

class SchemaTest {
    @Test
    fun `the first migration applies one version to an empty database`() {
        assertEquals(1, TestDatabase.migration.migrationsExecuted, "V1 is the whole schema for this step")
    }

    @Test
    fun `the migration and the mappings name the same tables, in both directions`() {
        val expected = schemaTables.map { it.tableName }.toSet() + FLYWAY_HISTORY

        assertEquals(expected, publicTables(), "V1 and schemaTables have drifted apart")
    }

    @Test
    fun `migrating again applies nothing, which is what lets every boot call it`() {
        assertEquals(
            0,
            DatabaseUtils.migrate(TestDatabase.pool).migrationsExecuted,
            "a second run must be a no-op, not a failure",
        )
    }

    @Test
    fun `every mapped column matches the migrated one, so a query cannot outrun the DDL`() {
        val mapped =
            transaction(TestDatabase.db) {
                schemaTables.associate { table ->
                    table.tableName to
                        table.columns.associate {
                            it.name to Shape(it.columnType.sqlType().normalizedType(), it.columnType.nullable)
                        }
                }
            }

        mapped.forEach { (table, columns) ->
            assertEquals(
                columns,
                databaseColumns(table),
                "the mapping of $table disagrees with V1; the SQL is the source of truth, so fix Tables.kt",
            )
        }
    }

    @Test
    fun `the direct pair index is partial, so a later conversation kind is not caught by it`() {
        val definition = indexDefinition("conversations_direct_pair_key")
        assertNotNull(definition, "the unique index W-03 names is missing")
        assertTrue(
            definition!!.contains("WHERE (kind = 'direct'"),
            "the index must be partial or a group conversation with two null members would collide: $definition",
        )
    }

    @Test
    fun `one direct conversation per pair, so concurrent first sends cannot fork the history`() {
        rollingBack { connection ->
            val alice = connection.insertUser("alice")
            val bob = connection.insertUser("bob")
            connection.insertDirect(alice, bob)

            assertViolates(UNIQUE_VIOLATION, "a second conversation for one pair must be refused") {
                connection.insertDirect(alice, bob)
            }
        }
    }

    @Test
    fun `a direct pair has one spelling, so the canonical order is enforced rather than trusted`() {
        rollingBack { connection ->
            val alice = connection.insertUser("alice")
            val bob = connection.insertUser("bob")

            assertViolates(CHECK_VIOLATION, "the pair reversed is the same pair and must not be insertable") {
                connection.insertDirect(bob, alice)
            }
        }
    }

    @Test
    fun `a resend under one client id finds the existing row rather than writing a second`() {
        rollingBack { connection ->
            val (conversation, alice, _) = connection.insertPairWithConversation()
            connection.insertMessage(conversation, alice, "hello", "c-1")

            assertViolates(UNIQUE_VIOLATION, "the same client_msg_id from one sender is one message") {
                connection.insertMessage(conversation, alice, "hello again", "c-1")
            }
        }
    }

    @Test
    fun `the idempotency key is per sender, so two senders may pick the same client id`() {
        rollingBack { connection ->
            val (conversation, alice, bob) = connection.insertPairWithConversation()
            connection.insertMessage(conversation, alice, "hello", "c-1")
            connection.insertMessage(conversation, bob, "hello", "c-1")
        }
    }

    @Test
    fun `created_at is server-assigned, so a sender cannot backdate a message`() {
        rollingBack { connection ->
            val (conversation, alice, _) = connection.insertPairWithConversation()
            val id = connection.insertMessage(conversation, alice, "hello", "c-1")

            val stamped =
                connection.prepareStatement("select created_at from messages where id = ?").use { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getTimestamp(1)
                    }
                }
            assertNotNull(stamped, "the column defaults to now(), so an insert naming no time still gets one")
        }
    }

    @Test
    fun `a message without a sender is refused, since no frame in this step is sender-less`() {
        rollingBack { connection ->
            val (conversation, _, _) = connection.insertPairWithConversation()

            assertViolates(NOT_NULL_VIOLATION, "sender_id carries no null case for a reader to handle") {
                connection
                    .prepareStatement(
                        "insert into messages (conversation_id, sender_id, body, client_msg_id) " +
                            "values (?, null, 'hello', 'c-1')",
                    ).use { statement ->
                        statement.setLong(1, conversation)
                        statement.executeUpdate()
                    }
            }
        }
    }

    @Test
    fun `a username is unique in the database rather than in anything held in memory`() {
        rollingBack { connection ->
            connection.insertUser("alice")

            assertViolates(UNIQUE_VIOLATION, "SEC-05 puts this guarantee in the index") {
                connection.insertUser("alice")
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun assertViolates(
        state: String,
        why: String,
        block: () -> Unit,
    ) {
        val failure = assertThrows(SQLException::class.java, block, why)
        assertEquals(state, failure.sqlState, "$why — refused, but for the wrong reason: ${failure.message}")
    }

    private fun Connection.insertUser(username: String): Long =
        prepareStatement("insert into users (username, password_hash) values (?, 'hash') returning id")
            .use { statement ->
                statement.setString(1, username)
                statement.singleId()
            }

    private fun Connection.insertDirect(
        lo: Long,
        hi: Long,
    ): Long =
        prepareStatement("insert into conversations (kind, direct_lo, direct_hi) values ('direct', ?, ?) returning id")
            .use { statement ->
                statement.setLong(1, lo)
                statement.setLong(2, hi)
                statement.singleId()
            }

    /** The pair and their conversation, which almost every message assertion needs first. */
    private fun Connection.insertPairWithConversation(): Triple<Long, Long, Long> {
        val alice = insertUser("alice")
        val bob = insertUser("bob")
        return Triple(insertDirect(alice, bob), alice, bob)
    }

    private fun Connection.insertMessage(
        conversation: Long,
        sender: Long,
        body: String,
        clientMsgId: String,
    ): Long =
        prepareStatement(
            "insert into messages (conversation_id, sender_id, body, client_msg_id) values (?, ?, ?, ?) returning id",
        ).use { statement ->
            statement.setLong(1, conversation)
            statement.setLong(2, sender)
            statement.setString(3, body)
            statement.setString(4, clientMsgId)
            statement.singleId()
        }

    private fun publicTables(): Set<String> =
        TestDatabase.pool.connection.use { connection ->
            connection
                .prepareStatement("select table_name from information_schema.tables where table_schema = 'public'")
                .use { statement ->
                    statement.executeQuery().use { rows ->
                        buildSet { while (rows.next()) add(rows.getString(1)) }
                    }
                }
        }

    private fun databaseColumns(table: String): Map<String, Shape> =
        TestDatabase.pool.connection.use { connection ->
            connection
                .prepareStatement(
                    "select column_name, data_type, is_nullable from information_schema.columns " +
                        "where table_schema = 'public' and table_name = ?",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { rows ->
                        buildMap {
                            while (rows.next()) {
                                put(
                                    rows.getString("column_name"),
                                    Shape(rows.getString("data_type").normalizedType(), rows.getString("is_nullable") == "YES"),
                                )
                            }
                        }
                    }
                }
        }

    private fun indexDefinition(name: String): String? =
        TestDatabase.pool.connection.use { connection ->
            connection.prepareStatement("select indexdef from pg_indexes where indexname = ?").use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    /** What a column has to agree on for a query written against the mapping to work. */
    private data class Shape(
        val type: String,
        val nullable: Boolean,
    )

    companion object {
        private const val FLYWAY_HISTORY = "flyway_schema_history"

        private const val UNIQUE_VIOLATION = "23505"
        private const val CHECK_VIOLATION = "23514"
        private const val NOT_NULL_VIOLATION = "23502"

        /**
         * Exposed spells an auto-incrementing key as the serial pseudo-type Postgres expands
         * into a bigint plus a sequence, so the two names have to be brought together before
         * they can be compared.
         */
        private fun String.normalizedType(): String =
            when (val type = uppercase()) {
                "BIGSERIAL" -> "BIGINT"
                "SERIAL" -> "INTEGER"
                else -> type
            }

        private fun java.sql.PreparedStatement.singleId(): Long =
            executeQuery().use { rows ->
                check(rows.next()) { "an insert with a RETURNING clause produced no row" }
                rows.getLong(1)
            }
    }
}
