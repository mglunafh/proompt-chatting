package dev.burufi.chatting.simple.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a client frame or an upgrade was refused, the machine-readable half of
 * [ServerFrame.Error].
 *
 * An unrecognized code fails to decode rather than falling back to a member.
 * That holds while server and client ship from one build; it needs revisiting
 * once the protocol carries a version.
 */
@Serializable
enum class ErrorCode {
    /** A frame that would not decode: unknown key, unknown or absent type, missing field. */
    @SerialName("malformed_frame")
    MALFORMED_FRAME,

    /** The name offered at the upgrade does not pass the name rules. */
    @SerialName("invalid_name")
    INVALID_NAME,

    /** Another client is connected under that name. */
    @SerialName("name_taken")
    NAME_TAKEN,

    /** The addressee of a send is not connected; nothing is stored, so nothing is held. */
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
}
