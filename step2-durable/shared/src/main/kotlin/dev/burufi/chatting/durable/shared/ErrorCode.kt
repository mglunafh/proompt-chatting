package dev.burufi.chatting.durable.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why an input was refused. Most of these are the machine-readable half of the server's
 * error frame; the two credential codes reach a REST response instead, since no frame in
 * this step carries a username or a password.
 *
 * Lives above both subpackages because the frames carry it and the rules produce it.
 */
@Serializable
enum class ErrorCode {
    /** A frame that would not decode: unknown key, unknown or absent type, missing field. */
    @SerialName("malformed_frame")
    MALFORMED_FRAME,

    /** The username fails the pattern, the length cap, or is reserved. */
    @SerialName("invalid_username")
    INVALID_USERNAME,

    /** The password breaks a length bound, or is one the blocklist names. */
    @SerialName("invalid_password")
    INVALID_PASSWORD,

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
