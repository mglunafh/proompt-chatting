package dev.burufi.chatting.durable.server

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)

fun Route.healthRoute() {
    get("/health") { call.respond(HealthResponse("ok")) }
}
