package dev.burufi.chatting.durable.server

import dev.burufi.chatting.durable.server.config.Config
import dev.burufi.chatting.durable.server.config.ConfigException
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import dev.burufi.chatting.durable.server.db.DatabaseUtils
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.burufi.chatting.durable.server")

/** Placeholder boot until W-05 puts the Ktor application here; proves the pool reaches Postgres. */
fun main() {
    val resolved =
        try {
            Config().resolveAll()
        } catch (e: ConfigException) {
            log.error("{}", e.message)
            exitProcess(1)
        }

    resolved.report().forEach { log.info("config: {}", it) }
    val config = DatabaseConfig.from(resolved)

    log.info("connecting to {} as {}", config.jdbcUrl, config.user)
    DatabaseUtils.openPool(config).use { pool ->
        val result = DatabaseUtils.migrate(pool)
        if (result.migrationsExecuted == 0) {
            // targetSchemaVersion is null when nothing ran, so the version comes from where it started.
            log.info("schema already at {}", result.initialSchemaVersion)
        } else {
            log.info("applied {} migration(s), schema now at {}", result.migrationsExecuted, result.targetSchemaVersion)
        }
    }
    log.info("pool closed")
}
