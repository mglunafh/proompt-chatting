package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.Caps
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets

fun Application.chatModule(registry: ConnectionRegistry = ConnectionRegistry()) {
    install(WebSockets) {
        maxFrameSize = Caps.MAX_FRAME_BYTES
    }
    routing {
        chatRoute(registry)
    }
}
