package dev.burufi.chatting.durable.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a client frame was refused, the machine-readable half of [ServerFrame.Error].
 */
@Serializable
enum class ErrorCode {
    /** A frame that would not decode: unknown key, unknown or absent type, missing field. */
    @SerialName("malformed_frame")
    MALFORMED_FRAME,

    /** The addressee of a send is not a known account. */
    @SerialName("unknown_recipient")
    UNKNOWN_RECIPIENT,

    /** The body is empty once trailing whitespace is stripped. */
    @SerialName("body_empty")
    BODY_EMPTY,

    /** The body exceeds [Limits.MAX_BODY_BYTES]. */
    @SerialName("body_too_large")
    BODY_TOO_LARGE,

    /** The body exceeds [Limits.MAX_BODY_LINES]. */
    @SerialName("body_too_many_lines")
    BODY_TOO_MANY_LINES,

    /** The body carries a C0 or C1 control character other than `\n` and `\t`. */
    @SerialName("body_invalid_characters")
    BODY_INVALID_CHARACTERS,

    /** The send's idempotency key exceeds [Limits.MAX_CLIENT_MSG_ID_BYTES]. */
    @SerialName("client_msg_id_too_long")
    CLIENT_MSG_ID_TOO_LONG,
}
