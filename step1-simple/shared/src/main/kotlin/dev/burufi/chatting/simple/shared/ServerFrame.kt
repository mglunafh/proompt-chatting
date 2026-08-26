package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ServerFrame {
    @Serializable
    @SerialName("roster")
    data class Roster(
        val names: List<String>,
    ) : ServerFrame

    @Serializable
    @SerialName("message")
    data class Message(
        val sender: String,
        val body: String,
    ) : ServerFrame

    @Serializable
    @SerialName("joined")
    data class Joined(
        val name: String,
    ) : ServerFrame

    @Serializable
    @SerialName("left")
    data class Left(
        val name: String,
    ) : ServerFrame

    @Serializable
    @SerialName("error")
    data class Error(
        val reason: String,
    ) : ServerFrame
}
