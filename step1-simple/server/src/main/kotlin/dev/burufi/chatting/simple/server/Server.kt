package dev.burufi.chatting.simple.server

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    embeddedServer(CIO, port = 8080, module = Application::chatModule).start(wait = true)
}
