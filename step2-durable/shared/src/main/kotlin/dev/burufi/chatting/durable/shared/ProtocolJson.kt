package dev.burufi.chatting.durable.shared

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object ProtocolJson {
    val CLIENT_FRAME: KSerializer<ClientFrame> = serializer()
    val SERVER_FRAME: KSerializer<ServerFrame> = serializer()

    /** The server's instance: decodes client frames, encodes server frames. */
    val STRICT: Json =
        Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = false
            encodeDefaults = true
        }

    /** The client's instance: decodes server frames, encodes client frames. */
    val TOLERANT: Json =
        Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
}
