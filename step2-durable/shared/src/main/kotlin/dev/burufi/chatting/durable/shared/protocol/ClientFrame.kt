package dev.burufi.chatting.durable.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A frame the client sends to the server, carried as JSON with a `type`
 * discriminator.
 */
@Serializable
sealed interface ClientFrame {
    /**
     * Deliver [body] to the account named [to].
     */
    @Serializable
    @SerialName("send")
    data class Send(
        @SerialName("client_msg_id") val clientMsgId: String,
        val to: String,
        val body: String,
    ) : ClientFrame

    /**
     * The echo answer to [ServerFrame.Heartbeat] to prove client liveness.
     */
    @Serializable
    @SerialName("heartbeat_reply")
    data class HeartbeatReply(
        val seq: Long,
    ) : ClientFrame
}
