package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode

/**
 * A username that has passed the name validation rules.
 */
@JvmInline
value class Username private constructor(
    val value: String,
) : Comparable<Username> {
    override fun compareTo(other: Username): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        /**
         * Lowercase ASCII only, starting with a letter, with single `_` or `-` separators
         * between runs and none at either end.
         */
        const val PATTERN: String = "^(?=[a-z0-9_-]{3,32}\$)[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*\$"
        private val REGEX = Regex(PATTERN)

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

        fun of(raw: String): Validated<Username> {
            if (!REGEX.matches(raw)) {
                return Validated.Invalid(
                    ErrorCode.INVALID_USERNAME,
                    "a username is 3 to 32 characters of lowercase letters, digits and single " +
                        "separators, starting with a letter",
                )
            }
            if (raw in RESERVED) {
                return Validated.Invalid(ErrorCode.INVALID_USERNAME, "'$raw' is reserved")
            }
            return Validated.Valid(Username(raw))
        }
    }
}
