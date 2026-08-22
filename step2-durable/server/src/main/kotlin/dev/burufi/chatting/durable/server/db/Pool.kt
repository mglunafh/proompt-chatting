package dev.burufi.chatting.durable.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database

const val POOL_NAME = "chatting-pool"

/**
 * The one pool the process owns: opened at boot, closed on shutdown, and bound to Exposed
 * here so nothing downstream is tempted to open a second one.
 */
fun openPool(config: DatabaseConfig): HikariDataSource {
    val hikari =
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password.reveal()
            maximumPoolSize = config.maxPoolSize
            connectionTimeout = config.connectionTimeout.inWholeMilliseconds
            poolName = POOL_NAME
        }
    val dataSource = HikariDataSource(hikari)
    Database.connect(dataSource)
    return dataSource
}
