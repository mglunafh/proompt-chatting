package dev.burufi.chatting.durable.server

import dev.burufi.chatting.durable.server.config.ConfigException
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import dev.burufi.chatting.durable.server.db.openPool
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.burufi.chatting.durable.server")

/** Placeholder boot until W-05 puts the Ktor application here; proves the pool reaches Postgres. */
fun main() {
    val config =
        try {
            DatabaseConfig.from()
        } catch (e: ConfigException) {
            log.error("configuration rejected: {}", e.message)
            exitProcess(1)
        }

    log.info("connecting to {} as {}", config.jdbcUrl, config.user)
    openPool(config).use { pool ->
        pool.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1").use { rows ->
                    check(rows.next()) { "SELECT 1 returned no row" }
                }
            }
        }
        log.info("database reachable, pool open")
    }
    log.info("pool closed")
}
