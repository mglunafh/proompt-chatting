package dev.burufi.chatting.durable.server

import dev.burufi.chatting.durable.server.config.Config
import dev.burufi.chatting.durable.server.config.ConfigException
import dev.burufi.chatting.durable.server.config.DatabaseConfig
import dev.burufi.chatting.durable.server.config.ResolvedConfig
import dev.burufi.chatting.durable.server.config.Setting
import dev.burufi.chatting.durable.server.db.DatabaseUtils
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.burufi.chatting.durable.server")

private const val BIND_HOST = "0.0.0.0"

fun main() {
    val resolvedConfig =
        try {
            Config().resolveAll()
        } catch (e: ConfigException) {
            log.error("{}", e.message)
            exitProcess(1)
        }

    resolvedConfig.report().forEach { log.info("config: {}", it) }

    val pool = openDatabase(resolvedConfig)

    val server =
        embeddedServer(
            CIO,
            port = resolvedConfig.int(Setting.SERVER_PORT),
            host = BIND_HOST,
            module = Application::module,
        )
    server.monitor.subscribe(ApplicationStopped) {
        pool.close()
        log.info("pool closed")
    }
    // Ktor's hook only stops the server; the pool closes off ApplicationStopped above, which
    // fires whether the stop came from a signal or from anywhere else.
    server.addShutdownHook { server.stop() }

    log.info("listening on {}:{}", BIND_HOST, resolvedConfig.int(Setting.SERVER_PORT))
    server.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }

    routing {
        healthRoute()
    }
}

private fun openDatabase(resolved: ResolvedConfig) =
    DatabaseConfig.from(resolved).let { config ->
        log.info("connecting to {} as {}", config.jdbcUrl, config.user)
        DatabaseUtils.createConnectionPool(config).also { pool ->
            DatabaseUtils.connect(pool)
            val result = DatabaseUtils.migrate(pool)
            if (result.migrationsExecuted == 0) {
                // targetSchemaVersion is null when nothing ran, so the version comes from where it started.
                log.info("schema already at {}", result.initialSchemaVersion)
            } else {
                log.info("applied {} migration(s), schema now at {}", result.migrationsExecuted, result.targetSchemaVersion)
            }
        }
    }
