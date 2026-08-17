package dev.burufi.chatting.simple.shared

object Endpoint {
    const val PATH: String = "/ws"
    const val NAME_PARAM: String = "name"

    /** Where the client looks and the server listens, absent anything said otherwise. */
    const val DEFAULT_PORT: Int = 8080
}
