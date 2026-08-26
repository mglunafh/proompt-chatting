package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send

class KtorSession(
    private val ws: WebSocketSession,
) : Session {
    override suspend fun send(frame: ServerFrame) {
        ws.send(Frame.Text(ChatJson.encodeToString(ServerFrame.serializer(), frame)))
    }
}
