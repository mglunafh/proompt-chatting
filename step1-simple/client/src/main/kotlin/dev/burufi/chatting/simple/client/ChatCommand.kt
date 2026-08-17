package dev.burufi.chatting.simple.client

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.Endpoint
import dev.burufi.chatting.simple.shared.Limits
import dev.burufi.chatting.simple.shared.Validated
import dev.burufi.chatting.simple.shared.Validation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.trySendBlocking
import java.io.IOException
import kotlin.concurrent.thread

class ChatCommand : SuspendingCliktCommand(name = "chat") {
    override fun help(context: Context): String = "Join the chat as a name and hold a conversation."

    private val name by option("--name", help = "the name to connect under").required()
    private val host by option("--host", help = "the host running the server").default("localhost")
    private val port by
        option("--port", help = "the port it listens on")
            .int()
            .restrictTo(MIN_PORT..MAX_PORT)
            .default(Endpoint.DEFAULT_PORT)

    override suspend fun run() {
        val userName =
            when (val validated = ClientName.of(name)) {
                is Validated.Invalid -> throw CliktError(validated.reason)
                is Validated.Valid -> validated.value
            }

        if (!Validation.isHost(host)) {
            throw CliktError("'--host' takes a hostname, an IPv4 address, or a bracketed IPv6 address")
        }

        val client =
            HttpClient(CIO) {
                install(WebSockets) {
                    maxFrameSize = Limits.MAX_FRAME_BYTES
                }
            }
        var admitted = false

        try {
            client.use {
                it.webSocket(chatUrl(host, port, userName)) {
                    admitted = ChatClient(userName, stdinLines(), ::echoLine).run(this)
                }
            }
        } catch (_: IOException) {
            throw CliktError("no server answered at $host:$port")
        }

        // The refusal has already been printed on its way past, so this only carries the
        // status out.
        if (!admitted) throw ProgramResult(1)
    }

    private fun echoLine(line: String) = echo(line)
}

private const val MIN_PORT = 1
private const val MAX_PORT = 65535

internal fun chatUrl(
    host: String,
    port: Int,
    name: ClientName,
): String =
    URLBuilder()
        .apply {
            protocol = URLProtocol.WS
            this.host = host
            this.port = port
            // "/ws" splits to ["", "ws"], the empty head being what keeps the leading slash.
            pathSegments = Endpoint.PATH.split("/")
            parameters.append(Endpoint.NAME_PARAM, name.value)
        }.buildString()

/**
 * Stdin as a channel, read on a separate daemon thread to circumvent
 * the blocking nature of `readLine()`.
 */
private fun stdinLines(): ReceiveChannel<String> {
    val lines = Channel<String>(Channel.RENDEZVOUS)
    thread(isDaemon = true, name = "stdin") {
        System.`in`.bufferedReader().useLines { typed ->
            for (line in typed) {
                if (lines.trySendBlocking(line).isFailure) return@useLines
            }
        }
        lines.close()
    }
    return lines
}
