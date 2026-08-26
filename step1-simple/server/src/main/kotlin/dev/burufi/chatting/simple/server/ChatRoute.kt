package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.Validation
import dev.burufi.chatting.simple.shared.ValidationOutcome
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException

private const val PATH_CHAT = "/chat"
private const val QUERY_NAME = "name"

fun Route.chatRoute(registry: ConnectionRegistry) {
    webSocket(PATH_CHAT) {
        runChat(registry)
    }
}

private suspend fun DefaultWebSocketServerSession.runChat(registry: ConnectionRegistry) {
    val name = call.request.queryParameters[QUERY_NAME]
    if (name == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "missing name"))
        return
    }
    when (val outcome = Validation.name(name)) {
        is ValidationOutcome.Invalid -> {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, outcome.reason))
            return
        }
        is ValidationOutcome.Ok -> {}
    }
    val session = KtorSession(this)
    when (val outcome = registry.register(name, session)) {
        is RegistrationOutcome.Duplicate -> {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "name already connected"))
            return
        }
        is RegistrationOutcome.Registered -> {}
    }
    try {
        for (frame in incoming) {
            handleFrame(session, frame)
        }
    } finally {
        registry.unregister(name)
    }
}

private suspend fun handleFrame(
    session: KtorSession,
    frame: Frame,
) {
    when (frame) {
        is Frame.Text -> {
            val text = frame.readText()
            val clientFrame =
                try {
                    ChatJson.decodeFromString(ClientFrame.serializer(), text)
                } catch (e: SerializationException) {
                    session.send(ServerFrame.Error("could not parse frame"))
                    return
                }
            when (clientFrame) {
                is ClientFrame.Send -> validateOrError(session, clientFrame)
            }
        }
        else -> session.send(ServerFrame.Error("unsupported frame type"))
    }
}

private suspend fun validateOrError(
    session: KtorSession,
    send: ClientFrame.Send,
) {
    when (val outcome = Validation.name(send.recipient)) {
        is ValidationOutcome.Invalid -> {
            session.send(ServerFrame.Error("recipient ${outcome.reason}"))
            return
        }
        is ValidationOutcome.Ok -> {}
    }
    when (val outcome = Validation.messageBody(send.body)) {
        is ValidationOutcome.Invalid -> {
            session.send(ServerFrame.Error(outcome.reason))
            return
        }
        is ValidationOutcome.Ok -> {}
    }
}
