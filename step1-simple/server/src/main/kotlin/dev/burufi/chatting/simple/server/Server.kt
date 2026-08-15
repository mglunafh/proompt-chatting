package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.Limits
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_PORT = 8080

fun main() {
    embeddedServer(CIO, port = DEFAULT_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        maxFrameSize = Limits.MAX_FRAME_BYTES
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }

    // One registry per application, so a test gets a fresh room.
    val registry = ConnectionRegistry()

    routing {
        chatRoute(registry)
    }
}
