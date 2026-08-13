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
 * The rules a message body and a connect name are held to, as pure functions.
 */
object Validation {
    /**
     * The shape of a connect name. Length is carried by the leading lookahead,
     * so shape and length stay one pattern with no second rule to keep in step.
     *
     * Lowercase ASCII only, starting with a letter, with single `_` or `-`
     * separators between runs and none at either end.
     */
    const val NAME_PATTERN: String = "^(?=[a-z0-9_-]{3,32}\$)[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*\$"

    /**
     * Names that would read as the server itself, or as an address, if a client
     * could connect under them.
     */
    val RESERVED_NAMES: Set<String> =
        setOf(
            "admin",
            "system",
            "server",
            "root",
            "mod",
            "moderator",
            "support",
            "everyone",
            "here",
            "bot",
            "invite",
            "health",
            "metrics",
            "api",
            "attachments",
            "blocks",
            "bookmarks",
        )

    private val NAME_REGEX = Regex(NAME_PATTERN)

    /** The two control characters a body may carry. */
    private const val TAB = '\t'
    private const val NEWLINE = '\n'

    /** Validate the name. */
    fun validateName(raw: String): Validated<String> {
        if (!NAME_REGEX.matches(raw)) {
            return Validated.Invalid(
                ErrorCode.INVALID_NAME,
                "a name is 3 to 32 characters of lowercase letters, digits and single separators, starting with a letter",
            )
        }
        if (raw in RESERVED_NAMES) {
            return Validated.Invalid(ErrorCode.INVALID_NAME, "'$raw' is reserved")
        }
        return Validated.Valid(raw)
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
