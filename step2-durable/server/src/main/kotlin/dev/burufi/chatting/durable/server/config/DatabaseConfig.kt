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
        fun from(resolved: ResolvedConfig): DatabaseConfig =
            DatabaseConfig(
                host = resolved.string(Setting.DB_HOST),
                port = resolved.int(Setting.DB_PORT),
                database = resolved.string(Setting.DB_NAME),
                user = resolved.string(Setting.DB_USER),
                password = Secret(resolved.string(Setting.DB_PASSWORD)),
                maxPoolSize = resolved.int(Setting.DB_POOL_MAX_SIZE),
                connectionTimeout = resolved.int(Setting.DB_CONNECTION_TIMEOUT_MS).milliseconds,
            )
    }
}
