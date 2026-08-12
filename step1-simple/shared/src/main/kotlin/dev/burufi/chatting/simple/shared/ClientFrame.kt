package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A frame the client sends to the server, carried as JSON with a `type`
 * discriminator.
 *
 * Encoding must go through this type rather than a subtype, or the
 * discriminator is left out.
 */
@Serializable
sealed interface ClientFrame {
    /**
     * Deliver [body] to the connected client named [to].
     *
     * Does not include the sender's name since the server knows it from the connection.
     */
    @Serializable
    @SerialName("send")
    data class Send(
        val to: String,
        val body: String,
    ) : ClientFrame
}
