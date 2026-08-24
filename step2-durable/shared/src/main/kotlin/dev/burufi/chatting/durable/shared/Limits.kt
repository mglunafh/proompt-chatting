package dev.burufi.chatting.durable.shared

/** The caps the wire is bounded by. */
object Limits {
    /** Message body cap, measured in UTF-8 bytes. */
    const val MAX_BODY_BYTES: Int = 8 * 1024

    /** Message body cap in lines, which bounds how far a body can scroll a terminal client. */
    const val MAX_BODY_LINES: Int = 100

    /**
     * The send idempotency key's cap, in UTF-8 bytes.
     */
    const val MAX_CLIENT_MSG_ID_BYTES: Int = 64

    /**
     * WebSocket `maxFrameSize`, enforced before deserialization on both sides.
     *
     * Exceeds [MAX_BODY_BYTES] so the JSON envelope and the escaping of a
     * worst-case body fit in the gap.
     */
    const val MAX_FRAME_BYTES: Long = 64L * 1024

    /** HTTP request body cap for the REST surface, enforced before deserialization as above. */
    const val MAX_HTTP_BODY_BYTES: Long = 256L * 1024
}
