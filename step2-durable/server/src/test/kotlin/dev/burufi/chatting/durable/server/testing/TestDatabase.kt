package dev.burufi.chatting.durable.server.testing

import com.zaxxer.hikari.HikariDataSource
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import dev.burufi.chatting.durable.server.config.Secret
import dev.burufi.chatting.durable.server.db.DatabaseUtils
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import kotlin.time.Duration.Companion.seconds

/**
 * One Postgres for the whole suite, started by this object's initializer rather than by a
 * JUnit lifecycle, so the second test class to need a database pays nothing. Nothing stops
 * it: Testcontainers' reaper removes the container when the JVM exits.
 */
object TestDatabase {
    val container: PostgreSQLContainer =
        PostgreSQLContainer(IMAGE).also { it.start() }

    val config: DatabaseConfig =
        DatabaseConfig(
            host = container.host,
            port = container.firstMappedPort,
            database = container.databaseName,
            user = container.username,
            password = Secret(container.password),
            maxPoolSize = POOL_SIZE,
            connectionTimeout = 10.seconds,
        )

    val pool: HikariDataSource = DatabaseUtils.createConnectionPool(config)

    val db: Database = DatabaseUtils.connect(pool)

    val migration: MigrateResult = DatabaseUtils.migrate(pool)

    /** Room for the concurrency tests to hold several connections at once and still race. */
    private const val POOL_SIZE = 8

    private const val IMAGE = "postgres:17-alpine"
}

/**
 * Runs a block in a transaction that is always rolled back, so each test starts from the
 * freshly migrated schema without anything having to be truncated between them.
 */
fun <T> rollingBack(block: JdbcTransaction.(Connection) -> T): T =
    transaction(TestDatabase.db) {
        try {
            block(jdbc)
        } finally {
            rollback()
        }
    }

val JdbcTransaction.jdbc: Connection
    get() = connection.connection as Connection
