package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode
import dev.burufi.chatting.durable.shared.Limits

object Validation {
    private const val TAB = '\t'
    private const val NEWLINE = '\n'

    /**
     * Normalize a body and refuse it if it breaks a rule, reporting the first rule it breaks.
     *
     * `\r\n` collapses to `\n` before anything is measured, so the caps count the form that
     * travels; a bare `\r` survives that and is refused below as the control character it is.
     */
    fun validateBody(raw: String): Validated<String> {
        val body = raw.replace("\r\n", "\n")

        val size = utf8Size(body)
        if (size > Limits.MAX_BODY_BYTES) {
            return Validated.Invalid(
                ErrorCode.BODY_TOO_LARGE,
                "$size bytes, limit is ${Limits.MAX_BODY_BYTES}",
            )
        }

        forbiddenCharacter(body)?.let { return it }

        val lines = body.count { it == NEWLINE } + 1
        if (lines > Limits.MAX_BODY_LINES) {
            return Validated.Invalid(
                ErrorCode.BODY_TOO_MANY_LINES,
                "$lines lines, limit is ${Limits.MAX_BODY_LINES}",
            )
        }

        if (body.trimEnd().isEmpty()) {
            return Validated.Invalid(
                ErrorCode.BODY_EMPTY,
                "the body is empty once trailing whitespace is stripped",
            )
        }

        return Validated.Valid(body)
    }

    /**
     * The send idempotency key, which the ack correlates on and the database holds under a
     * unique index.
     */
    fun validateClientMsgId(raw: String): Validated<String> {
        if (raw.isBlank()) {
            return Validated.Invalid(
                ErrorCode.CLIENT_MSG_ID_TOO_LONG,
                "a client_msg_id is required, since it is what makes a resend idempotent",
            )
        }

        val size = utf8Size(raw)
        if (size > Limits.MAX_CLIENT_MSG_ID_BYTES) {
            return Validated.Invalid(
                ErrorCode.CLIENT_MSG_ID_TOO_LONG,
                "$size bytes, limit is ${Limits.MAX_CLIENT_MSG_ID_BYTES}",
            )
        }

        return Validated.Valid(raw)
    }

    /**
     * The first character a body may not carry, as a refusal, or null.
     *
     * C0 and C1 are what make a message drive the terminal rather than appear in it: C0 covers
     * `ESC` at 0x1B, and C1 covers U+009B, which is `CSI` on its own and reaches the same result
     * without an `ESC` byte. An unpaired surrogate is refused beside them because it has no UTF-8
     * form, so a body carrying one cannot cross the wire intact.
     *
     * Bidi overrides and zero-width characters are deliberately allowed: mixed direction text is
     * legitimate in a message, and what they do to a terminal is the client's to render inertly.
     */
    private fun forbiddenCharacter(body: String): Validated.Invalid? {
        body.forEachIndexed { index, char ->
            val code = char.code
            val kind =
                when {
                    char == NEWLINE || char == TAB -> null
                    code < 0x20 || code == 0x7F || code in 0x80..0x9F -> "control character"
                    char.isHighSurrogate() && body.getOrNull(index + 1)?.isLowSurrogate() != true -> "unpaired surrogate"
                    char.isLowSurrogate() && body.getOrNull(index - 1)?.isHighSurrogate() != true -> "unpaired surrogate"
                    else -> null
                }
            if (kind != null) {
                // The character itself is never quoted back, or the refusal frame carries the
                // escape sequence out to whoever renders it.
                return Validated.Invalid(
                    ErrorCode.BODY_INVALID_CHARACTERS,
                    "$kind ${"U+%04X".format(code)} at index $index",
                )
            }
        }
        return null
    }
}

/**
 * The UTF-8 length of [text].
 */
internal fun utf8Size(text: String): Int {
    var size = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        size +=
            when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                // A pair encodes as four bytes and covers two indices; a lone surrogate counts
                // as three and is refused by the body scan.
                char.isHighSurrogate() && text.getOrNull(index + 1)?.isLowSurrogate() == true -> {
                    index++
                    4
                }
                else -> 3
            }
        index++
    }
    return size
}
