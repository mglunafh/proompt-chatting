package dev.burufi.chatting.simple.server

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}

fun main() {
    embeddedServer(CIO, port = 8080) { module() }.start(wait = true)
}
