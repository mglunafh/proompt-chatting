package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.Endpoint
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

fun main() {
    embeddedServer(CIO, port = Endpoint.DEFAULT_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        maxFrameSize = Limits.MAX_FRAME_BYTES
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }

    val registry = ConnectionRegistry()

    routing {
        chatRoute(registry)
    }
}
