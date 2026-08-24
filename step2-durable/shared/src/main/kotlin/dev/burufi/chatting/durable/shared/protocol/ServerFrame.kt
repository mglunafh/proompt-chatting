package dev.burufi.chatting.durable.shared.protocol

import dev.burufi.chatting.durable.shared.ErrorCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A frame the server sends to the client, carried as JSON with a `type`
 * discriminator.
 *
 * Timestamps are ISO-8601 instants, readable in a captured frame and distinct from the ids
 * beside them, which every other numeric field here is.
 */
@Serializable
sealed interface ServerFrame {
    /**
     * Who is online, as the first frame after the upgrade.
     *
     * Taken by the same registry operation that added the socket, so no delta can slip between
     * the two.
     */
    @Serializable
    @SerialName("presence_snapshot")
    data class PresenceSnapshot(
        val users: List<PresenceEntry>,
    ) : ServerFrame

    /** A user's socket count went from zero to one. Never sent to that user's own sessions. */
    @Serializable
    @SerialName("user_online")
    data class UserOnline(
        @SerialName("user_id") val userId: Long,
        val username: String,
    ) : ServerFrame

    /**
     * A user's socket count reached zero and stayed there past the grace window.
     *
     * [lastSeenAt] rides the frame so a client renders "last seen" without a follow-up fetch.
     */
    @Serializable
    @SerialName("user_offline")
    data class UserOffline(
        @SerialName("user_id") val userId: Long,
        @SerialName("last_seen_at") val lastSeenAt: Instant,
    ) : ServerFrame

    /**
     * A send was persisted, answered to the sender's own socket and correlated by
     * [clientMsgId].
     */
    @Serializable
    @SerialName("ack")
    data class Ack(
        @SerialName("client_msg_id") val clientMsgId: String,
        @SerialName("message_id") val messageId: Long,
        @SerialName("conversation_id") val conversationId: Long,
        @SerialName("created_at") val createdAt: Instant,
    ) : ServerFrame

    /**
     * A message, fanned out to every live session of the conversation's members, the sender's
     * own included.
     *
     * References the sender by id: a client resolves the name from the presence frames it
     * already caches.
     */
    @Serializable
    @SerialName("message")
    data class Message(
        val id: Long,
        @SerialName("conversation_id") val conversationId: Long,
        @SerialName("sender_id") val senderId: Long,
        val body: String,
        @SerialName("created_at") val createdAt: Instant,
    ) : ServerFrame

    /**
     * The liveness tick, answered by [ClientFrame.HeartbeatReply].
     */
    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        val seq: Long,
    ) : ServerFrame

    /**
     * A refusal.
     *
     * @property clientMsgId ID of the frame which was refused.
     * @property reason free text for display.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        val code: ErrorCode,
        val reason: String,
        @SerialName("client_msg_id") val clientMsgId: String? = null,
    ) : ServerFrame
}

@Serializable
data class PresenceEntry(
    @SerialName("user_id") val userId: Long,
    val username: String,
)
