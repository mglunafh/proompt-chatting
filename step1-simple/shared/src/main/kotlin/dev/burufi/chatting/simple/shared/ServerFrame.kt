package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A frame the server sends to the client, carried as JSON with a `type`
 * discriminator.
 */
@Serializable
sealed interface ServerFrame {
    /** The names currently connected. The first frame after the upgrade. */
    @Serializable
    @SerialName("roster")
    data class Roster(
        val names: List<String>,
    ) : ServerFrame

    /** A client connected, broadcast to everyone already connected. */
    @Serializable
    @SerialName("user_joined")
    data class UserJoined(
        val name: String,
    ) : ServerFrame

    /** A client disconnected, broadcast to everyone still connected. */
    @Serializable
    @SerialName("user_left")
    data class UserLeft(
        val name: String,
    ) : ServerFrame

    /**
     * A direct message, sent to the recipient and echoed to the sender as the
     * same frame. A client tells the two apart by comparing [from] with its own
     * name.
     */
    @Serializable
    @SerialName("message")
    data class Message(
        val from: String,
        val to: String,
        val body: String,
    ) : ServerFrame

    /**
     * A refusal. The connection should survive it.
     *
     * [reason] is free text for display and is untrusted by the client.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        val code: ErrorCode,
        val reason: String,
    ) : ServerFrame
}
