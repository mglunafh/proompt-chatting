package dev.burufi.chatting.simple.shared

sealed interface Validated<out T> {
    data class Valid<T>(
        val value: T,
    ) : Validated<T>

    /** Why the value was refused. [reason] is free text, for display only. */
    data class Invalid(
        val code: ErrorCode,
        val reason: String,
    ) : Validated<Nothing>
}

/**
 * The rules an input is held to, as pure functions. The name rules live on [ClientName],
 * which is the only thing that can vouch for one.
 */
object Validation {
    /** The two control characters a body may carry. */
    private const val TAB = '\t'
    private const val NEWLINE = '\n'

    /** A DNS label: 1 to 63 characters, alphanumeric at both ends, hyphens within (RFC 1123). */
    private const val LABEL = "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"

    /** Labels separated by dots, optionally fully qualified. IPv4 and punycode match this too. */
    private val HOST_NAME = Regex("$LABEL(?:\\.$LABEL)*\\.?")

    /** RFC 3986's IP-literal. The brackets are the form a URL needs, and nothing adds them for us. */
    private val IP_V6 = Regex("\\[[0-9A-Fa-f:.]{2,45}]")

    /** RFC 1035's cap on a whole name. */
    private const val MAX_HOST_LENGTH = 253

    /**
     * Whether [raw] may stand in the host slot of a URL.
     *
     * A predicate rather than a [Validated], because there is no [ErrorCode] that fits: a
     * host is an argument to a process, never something a frame carries, and every code is
     * half of a [ServerFrame.Error].
     *
     * Deliberately narrower than RFC 3986's `reg-name`, which permits sub-delims. The point
     * is to stop a host carrying a path, query or fragment of its own, not to prove the
     * address resolves.
     */
    fun isHost(raw: String): Boolean =
        if (raw.startsWith('[')) {
            IP_V6.matches(raw)
        } else {
            raw.length <= MAX_HOST_LENGTH && HOST_NAME.matches(raw)
        }

    /**
     * Normalize a body and refuse it if it breaks a rule, reporting the first
     * rule it breaks.
     *
     * `\r\n` collapses to `\n` before anything is measured, so the caps count the
     * form that travels; a bare `\r` survives that and is refused below as the
     * control character it is.
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
     * The first character a body may not carry, as a refusal, or null.
     *
     * C0 and C1 are what make a message drive the terminal rather than appear in
     * it: C0 covers `ESC` at 0x1B, and C1 covers U+009B, which is `CSI` on its
     * own and reaches the same result without an `ESC` byte. An unpaired
     * surrogate is refused beside them because it has no UTF-8 form, so a body
     * carrying one cannot cross the wire intact.
     *
     * Bidi overrides and zero-width characters are deliberately allowed: mixed
     * direction text is legitimate in a message, and what they do to a terminal
     * is the client's to render inertly.
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
                // The character itself is never quoted back, or the refusal frame
                // carries the escape sequence out to whoever renders it.
                return Validated.Invalid(
                    ErrorCode.BODY_INVALID_CHARACTERS,
                    "$kind ${"U+%04X".format(code)} at index $index",
                )
            }
        }
        return null
    }

    /** The UTF-8 length of [text], counted rather than encoded. */
    private fun utf8Size(text: String): Int {
        var size = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            size +=
                when {
                    char.code < 0x80 -> 1
                    char.code < 0x800 -> 2
                    // A pair encodes as four bytes and covers two indices; a lone
                    // surrogate counts as three and is refused by the scan above.
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
}
