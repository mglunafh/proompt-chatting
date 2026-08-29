package dev.burufi.chatting.simple.client

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.Validation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

private val chatJson: Json = Json.Default

internal const val HELP_TEXT: String =
    "commands:\n" +
        "  @<recipient> <body>  send a message to a connected client\n" +
        "  /exit                leave the chat\n" +
        "  /help                show this help"

fun main(args: Array<String>) = Client().main(args)

class Client : CliktCommand() {
    private val name: String by option("--name").required()
    private val host: String by option("--host").default("127.0.0.1")
    private val port: String by option("--port").default("8080")

    override fun run() {
        runBlocking { runClient(name, host, port.toInt()) }
    }
}

internal suspend fun runClient(
    name: String,
    host: String,
    port: Int,
    lines: ReceiveChannel<String> = stdinChannel(),
    emit: (String) -> Unit = ::println,
) {
    HttpClient(CIO) {
        install(WebSockets)
    }.use { client ->
        client.webSocket(urlString = chatUrl(host, port, name)) {
            runSession(lines, emit)
        }
    }
}

internal suspend fun DefaultClientWebSocketSession.runSession(
    lines: ReceiveChannel<String>,
    emit: (String) -> Unit,
) {
    coroutineScope {
        launch { sendLoop(lines, emit) }
        launch { receiveLoop(emit) }
    }
}

internal fun stdinChannel(stdin: BufferedReader = stdinReader()): ReceiveChannel<String> {
    val channel = Channel<String>(Channel.UNLIMITED)
    thread(name = "stdin-reader", isDaemon = true) {
        try {
            while (true) {
                val line = stdin.readLine() ?: break
                if (channel.trySend(line).isClosed) break
            }
        } finally {
            channel.close()
        }
    }
    return channel
}

private fun stdinReader(): BufferedReader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))

private fun chatUrl(
    host: String,
    port: Int,
    name: String,
): String {
    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
    return "ws://$host:$port/chat?name=$encoded"
}

private suspend fun DefaultClientWebSocketSession.sendLoop(
    lines: ReceiveChannel<String>,
    emit: (String) -> Unit,
) {
    for (line in lines) {
        when (val action = parseLine(line)) {
            is LineAction.Send -> send(Frame.Text(chatJson.encodeToString(ClientFrame.serializer(), action.frame)))
            LineAction.Exit -> close(CloseReason(CloseReason.Codes.NORMAL, "exit"))
            LineAction.Help -> emit(HELP_TEXT)
            is LineAction.UnknownCommand -> emit("! unknown command: /${action.word}")
            LineAction.NotACommand -> {}
        }
    }
}

private suspend fun DefaultClientWebSocketSession.receiveLoop(emit: (String) -> Unit) {
    for (frame in incoming) {
        if (frame !is Frame.Text) continue
        val serverFrame = chatJson.decodeFromString(ServerFrame.serializer(), frame.readText())
        emit(renderServerFrame(serverFrame))
    }
}

private fun renderServerFrame(frame: ServerFrame): String =
    when (frame) {
        is ServerFrame.Roster -> "[roster] ${frame.names.joinToString(", ") { Validation.escapeControl(it) }}"
        is ServerFrame.Message -> "[${Validation.escapeControl(frame.sender)}] ${Validation.escapeControl(frame.body)}"
        is ServerFrame.Joined -> "* ${Validation.escapeControl(frame.name)} joined"
        is ServerFrame.Left -> "* ${Validation.escapeControl(frame.name)} left"
        is ServerFrame.Error -> "! error: ${Validation.escapeControl(frame.reason)}"
    }
