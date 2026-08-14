package dev.burufi.chatting.simple.shared

/**
 * A name that has passed the name rules.
 */
@JvmInline
value class ClientName private constructor(
    val value: String,
) : Comparable<ClientName> {
    override fun compareTo(other: ClientName): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        /**
         * Length rides the leading lookahead, so shape and length stay one
         * pattern with no second rule to keep in step.
         *
         * Lowercase ASCII only, starting with a letter, with single `_` or `-`
         * separators between runs and none at either end.
         */
        const val PATTERN: String = "^(?=[a-z0-9_-]{3,32}\$)[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*\$"

        /**
         * Names that would read as the server itself, or as an address, if a
         * client could connect under them.
         */
        val RESERVED: Set<String> =
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

        private val REGEX = Regex(PATTERN)

        fun of(raw: String): Validated<ClientName> {
            if (!REGEX.matches(raw)) {
                return Validated.Invalid(
                    ErrorCode.INVALID_NAME,
                    "a name is 3 to 32 characters of lowercase letters, digits and single separators, starting with a letter",
                )
            }
            if (raw in RESERVED) {
                return Validated.Invalid(ErrorCode.INVALID_NAME, "'$raw' is reserved")
            }
            return Validated.Valid(ClientName(raw))
        }
    }
}
