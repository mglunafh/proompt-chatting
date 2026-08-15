package dev.burufi.chatting.simple.shared

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * How the wire is read: two JSON configurations, and the serializer to use with
 * each. The configurations differ only in how they treat an unknown key: the
 * server rejects one, the client ignores it, so a newer server can add server
 * frame fields without breaking an older client.
 */
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
