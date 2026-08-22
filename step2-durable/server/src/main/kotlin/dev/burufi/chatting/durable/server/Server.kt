package dev.burufi.chatting.durable.server

import dev.burufi.chatting.durable.server.config.Config
import dev.burufi.chatting.durable.server.config.ConfigException
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import dev.burufi.chatting.durable.server.db.openPool
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
