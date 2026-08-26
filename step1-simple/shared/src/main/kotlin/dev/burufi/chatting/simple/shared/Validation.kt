package dev.burufi.chatting.simple.shared

sealed interface ValidationOutcome {
    data object Ok : ValidationOutcome

    data class Invalid(
        val reason: String,
    ) : ValidationOutcome
}

object Validation {
    fun messageBody(body: String): ValidationOutcome {
        val stored = body.replace("\r\n", "\n")
        if ('\r' in stored) {
            return ValidationOutcome.Invalid("body has a bare carriage return")
        }
        if (stored.trimEnd().isEmpty()) {
            return ValidationOutcome.Invalid("body is empty after trimming whitespace")
        }
        if (byteCount(stored) > Caps.MAX_MESSAGE_BYTES) {
            return ValidationOutcome.Invalid("body exceeds the byte cap")
        }
        if (lineCount(stored) > Caps.MAX_MESSAGE_LINES) {
            return ValidationOutcome.Invalid("body exceeds the line cap")
        }
        if (containsForbiddenControl(stored)) {
            return ValidationOutcome.Invalid("body contains a forbidden control character")
        }
        return ValidationOutcome.Ok
    }

    fun name(name: String): ValidationOutcome {
        if (byteCount(name) > Caps.MAX_MESSAGE_BYTES) {
            return ValidationOutcome.Invalid("name exceeds the byte cap")
        }
        if (containsForbiddenControl(name)) {
            return ValidationOutcome.Invalid("name contains a forbidden control character")
        }
        return ValidationOutcome.Ok
    }

    private fun byteCount(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun lineCount(s: String): Int = if (s.isEmpty()) 0 else s.split('\n').size

    private fun containsForbiddenControl(s: String): Boolean = s.any(::isForbiddenControl)

    private fun isForbiddenControl(c: Char): Boolean {
        val code = c.code
        if (code == 0x09 || code == 0x0A) return false
        if (code in 0x00..0x1F) return true
        if (code == 0x7F) return true
        if (code in 0x80..0x9F) return true
        return false
    }
}
