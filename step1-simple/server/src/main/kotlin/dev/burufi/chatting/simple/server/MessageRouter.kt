package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.Validated
import dev.burufi.chatting.simple.shared.Validation

class MessageRouter(
    private val registry: ConnectionRegistry,
) {
    /**
     * Deliver [send] on behalf of [sender], or tell it why not.
     */
    suspend fun route(
        sender: ClientConnection,
        send: ClientFrame.Send,
    ) {
        // The body first, so a bad frame is refused the same way whoever is connected.
        val body =
            when (val validated = Validation.validateBody(send.body)) {
                is Validated.Invalid -> return sender.send(ServerFrame.Error(validated.code, validated.reason))
                is Validated.Valid -> validated.value
            }

        val recipient =
            when (val to = ClientName.of(send.to)) {
                is Validated.Invalid ->
                    return sender.send(ServerFrame.Error(ErrorCode.UNKNOWN_RECIPIENT, "the recipient is not a name"))

                is Validated.Valid ->
                    registry.lookup(to.value)
                        ?: return sender.send(
                            ServerFrame.Error(ErrorCode.UNKNOWN_RECIPIENT, "'${to.value}' is not connected"),
                        )
            }

        val message = ServerFrame.Message(sender.name.value, recipient.name.value, body)
        recipient.send(message)
        if (sender !== recipient) {
            sender.send(message)
        }
    }
}
