package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ClientFrame {
    @Serializable
    @SerialName("send")
    data class Send(
        val recipient: String,
        val body: String,
    ) : ClientFrame
}
