package dev.burufi.chatting.simple.shared

import kotlinx.serialization.json.Json

/**
 * The two JSON configurations the wire is read with. They differ only in how
 * they treat an unknown key: the server rejects one, the client ignores it, so
 * a newer server can add server frame fields without breaking an older client.
 */
object ProtocolJson {
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
