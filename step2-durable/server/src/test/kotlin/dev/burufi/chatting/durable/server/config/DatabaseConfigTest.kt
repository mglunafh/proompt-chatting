package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class DatabaseConfigTest {
    private fun from(vararg env: Pair<String, String>) = DatabaseConfig.from(Config(mapOf("DB_PASSWORD" to "hunter2", *env)))

    @Test
    fun `an environment carrying only the password still describes a local database`() {
        val config = from()

        assertEquals(DatabaseConfig.DEFAULT_HOST, config.host)
        assertEquals(DatabaseConfig.DEFAULT_PORT, config.port)
        assertEquals(DatabaseConfig.DEFAULT_DATABASE, config.database)
        assertEquals(DatabaseConfig.DEFAULT_USER, config.user)
        assertEquals(DatabaseConfig.DEFAULT_MAX_POOL_SIZE, config.maxPoolSize)
        assertEquals(DatabaseConfig.DEFAULT_CONNECTION_TIMEOUT_MS.milliseconds, config.connectionTimeout)
    }

    @Test
    fun `every setting is overridable from the environment`() {
        val config =
            from(
                "DB_HOST" to "db",
                "DB_PORT" to "6543",
                "DB_NAME" to "chat",
                "DB_USER" to "app",
                "DB_POOL_MAX_SIZE" to "4",
                "DB_CONNECTION_TIMEOUT_MS" to "250",
            )

        assertEquals("db", config.host)
        assertEquals(6543, config.port)
        assertEquals("chat", config.database)
        assertEquals("app", config.user)
        assertEquals(4, config.maxPoolSize)
        assertEquals(250.milliseconds, config.connectionTimeout)
    }

    @Test
    fun `the jdbc url is composed from host, port and database`() {
        val config = from("DB_HOST" to "db", "DB_PORT" to "6543", "DB_NAME" to "chat")

        assertEquals("jdbc:postgresql://db:6543/chat", config.jdbcUrl)
    }

    @Test
    fun `the password is missing from the rendered config, which startup logging prints`() {
        val rendered = from().toString()

        assertFalse(
            rendered.contains("hunter2"),
            "W-05 logs this object at boot; a password in it would land in the log store: $rendered",
        )
        assertEquals("hunter2", from().password.reveal(), "the value is still readable where it is needed")
    }

    @Test
    fun `a database without a password refuses to boot rather than trying a blank one`() {
        assertThrows(ConfigException::class.java) {
            DatabaseConfig.from(Config(emptyMap()))
        }
    }
}
