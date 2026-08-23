package dev.burufi.chatting.durable.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

object DatabaseUtils {
    const val POOL_NAME = "chatting-pool"
    const val MIGRATION_LOCATION = "classpath:db/migration"

    fun createConnectionPool(config: DatabaseConfig): HikariDataSource {
        val hikari =
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.user
                password = config.password.reveal()
                maximumPoolSize = config.maxPoolSize
                connectionTimeout = config.connectionTimeout.inWholeMilliseconds
                poolName = POOL_NAME
            }
        return HikariDataSource(hikari)
    }

    /**
     * Binds a pool to Exposed and hands back the database handle.
     */
    fun connect(dataSource: DataSource): Database = Database.connect(dataSource)

    fun migrate(dataSource: DataSource): MigrateResult =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(MIGRATION_LOCATION)
            .load()
            .migrate()
}
