package dev.burufi.chatting.durable.shared.validation

import dev.burufi.chatting.durable.shared.ErrorCode

/** The outcome of a rule: the value it vouches for, or why it was refused. */
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
