package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class DatabaseConfigTest {
    private fun from(vararg values: Pair<String, String>) =
        DatabaseConfig.from(
            Config(listOf(Environment(mapOf("DB_PASSWORD" to "hunter2", *values)))).resolveAll(),
        )

    @Test
    fun `an environment carrying only the password still describes a local database`() {
        val config = from()

        assertEquals("localhost", config.host)
        assertEquals(5432, config.port)
        assertEquals("chatting", config.database)
        assertEquals("chatting", config.user)
        assertEquals(10, config.maxPoolSize)
        assertEquals(5_000.milliseconds, config.connectionTimeout)
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
            DatabaseConfig.from(Config(listOf(Environment(emptyMap()))).resolveAll())
        }
    }
}
