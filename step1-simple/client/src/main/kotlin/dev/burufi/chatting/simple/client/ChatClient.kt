package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ProtocolJson
import dev.burufi.chatting.simple.shared.ServerFrame
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

/**
 * A connected client: typed lines out, frames in, both at once.
 *
 * @property name Username the client got registered on the server with.
 * @property userInput Buffer for lines as the person enters them, before being sent to the server.
 * @property emit Consumer for finished lines.
 */
class ChatClient(
    private val name: ClientName,
    private val userInput: ReceiveChannel<String>,
    private val emit: (String) -> Unit,
) {
    /** Emit only the inert messages. */
    private fun show(line: String) = emit(inert(line))

    /**
     * Run until the socket closes.
     *
     * @return whether the connection was admitted.
     */
    suspend fun run(session: WebSocketSession): Boolean =
        coroutineScope {
            var admitted = false

            val receiving =
                launch {
                    for (frame in session.incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue

                        val serverFrame =
                            try {
                                ProtocolJson.TOLERANT.decodeFromString(ProtocolJson.SERVER_FRAME, text)
                            } catch (_: SerializationException) {
                                show("! a frame arrived that this client cannot read, and was dropped")
                                continue
                            }

                        if (serverFrame is ServerFrame.Roster) admitted = true
                        show(render(serverFrame, name))
                    }
                }

            val sending =
                launch {
                    try {
                        for (line in userInput) {
                            when (val command = Command.of(line)) {
                                is Command.Send -> session.send(command)
                                is Command.Unusable -> show("! ${command.reason}")
                                Command.Help -> show(Command.HELP)
                                Command.Exit -> break
                                Command.Nothing -> Unit
                            }
                        }
                        // Stdin ended, so say goodbye rather than dropping the socket.
                        session.close()
                    } catch (_: ClosedSendChannelException) {
                        // The socket went while a line was in hand. The receive loop is
                        // ending for the same reason, and it is what decides when we stop.
                    }
                }

            // The socket closing is what ends the client, never the other way round.
            receiving.join()
            sending.cancel()
            admitted
        }

    private suspend fun WebSocketSession.send(command: Command.Send) {
        val frame: ClientFrame = ClientFrame.Send(command.to.value, command.body)
        send(Frame.Text(ProtocolJson.STRICT.encodeToString(ProtocolJson.CLIENT_FRAME, frame)))
    }
}
