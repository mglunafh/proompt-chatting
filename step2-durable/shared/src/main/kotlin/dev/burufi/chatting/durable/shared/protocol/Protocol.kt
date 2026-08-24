package dev.burufi.chatting.durable.shared.protocol

/** Where the socket is, and which version of this module the two sides must share. */
object Protocol {
    /** Checked once at the upgrade; a mismatch is refused with `426` rather than carried per frame. */
    const val VERSION: Int = 1

    const val PATH: String = "/ws"

    /** Query parameter carrying [VERSION], since the upgrade's headers already carry the token. */
    const val VERSION_PARAM: String = "v"
}
