package dev.burufi.chatting.durable.server.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A value that must never reach a log line, whatever it is embedded in. */
@JvmInline
value class Secret(
    private val value: String,
) {
    fun reveal(): String = value

    override fun toString(): String = REDACTED

    companion object {
        const val REDACTED = "***"
    }
}

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: Secret,
    val maxPoolSize: Int,
    val connectionTimeout: Duration,
) {
    val jdbcUrl: String get() = "jdbc:postgresql://$host:$port/$database"

    companion object {
        const val DEFAULT_HOST = "localhost"
        const val DEFAULT_PORT = 5432
        const val DEFAULT_DATABASE = "chatting"
        const val DEFAULT_USER = "chatting"
        const val DEFAULT_MAX_POOL_SIZE = 10
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 5_000

        fun from(config: Config = Config()): DatabaseConfig =
            DatabaseConfig(
                host = config.string("DB_HOST", DEFAULT_HOST),
                port = config.int("DB_PORT", DEFAULT_PORT),
                database = config.string("DB_NAME", DEFAULT_DATABASE),
                user = config.string("DB_USER", DEFAULT_USER),
                password = Secret(config.required("DB_PASSWORD")),
                maxPoolSize = config.int("DB_POOL_MAX_SIZE", DEFAULT_MAX_POOL_SIZE),
                connectionTimeout =
                    config.int("DB_CONNECTION_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS).milliseconds,
            )
    }
}
