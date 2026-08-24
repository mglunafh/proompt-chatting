package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode

object Password {
    const val MIN_CHARS: Int = 12

    const val MAX_CHARS: Int = 256

    const val MAX_BYTES: Int = 1024

    /**
     * Refuse [raw] if it breaks a rule, reporting the first one it breaks.
     */
    fun validate(
        raw: String,
        username: String? = null,
    ): Validated<String> {
        if (raw.length < MIN_CHARS) {
            return refuse("a password is at least $MIN_CHARS characters")
        }
        if (raw.length > MAX_CHARS) {
            return refuse("a password is at most $MAX_CHARS characters")
        }

        val size = utf8Size(raw)
        if (size > MAX_BYTES) {
            return refuse("a password is at most $MAX_BYTES bytes, and this one is $size")
        }

        if (username != null && raw.equals(username, ignoreCase = true)) {
            return refuse("a password cannot be your username")
        }
        if (PasswordBlocklist.contains(raw)) {
            return refuse("that password is too common to use")
        }

        return Validated.Valid(raw)
    }

    private fun refuse(reason: String) = Validated.Invalid(ErrorCode.INVALID_PASSWORD, reason)
}
