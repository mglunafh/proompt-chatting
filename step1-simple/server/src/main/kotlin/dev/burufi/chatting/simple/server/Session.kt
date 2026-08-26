package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ServerFrame

interface Session {
    suspend fun send(frame: ServerFrame)
}
