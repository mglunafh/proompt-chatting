package dev.burufi.chatting.simple.client

/**
 * The one function every string off the socket passes through before it reaches the
 * terminal.
 *
 * An allowlist: printable characters, `\n` and `\t` survive, and everything else is
 * replaced by something visible.
 */
internal fun inert(text: String): String {
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        // By code point: a walk over `Char` would see each half of an astral pair as a
        // lone surrogate and mangle every emoji.
        val code = text.codePointAt(index)
        val replacement = replacementForOrNull(code)
        if (replacement == null) out.appendCodePoint(code) else out.append(replacement)
        index += Character.charCount(code)
    }
    return out.toString()
}

private const val TAB = '\t'.code
private const val NEWLINE = '\n'.code

/** Control Pictures: U+2400 is the symbol for NUL, and the block runs in step with C0. */
private const val CONTROL_PICTURES = 0x2400
private const val DELETE = 0x7F

/** The block's symbol for DEL. */
private const val SYMBOL_FOR_DELETE = '␡'

private const val REPLACEMENT = '�'

/** What a code point may not be. */
private val REJECTED =
    setOf(
        Character.UNASSIGNED,
        Character.CONTROL,
        Character.FORMAT,
        Character.PRIVATE_USE,
        Character.SURROGATE,
        Character.LINE_SEPARATOR,
        Character.PARAGRAPH_SEPARATOR,
    )

/** What to print instead of [code], or null to keep it. */
private fun replacementForOrNull(code: Int): Char? =
    when {
        code == NEWLINE || code == TAB -> null
        // C0 has a printable twin per character, so ESC shows as its own symbol rather
        // than as an anonymous smudge.
        code < 0x20 -> (CONTROL_PICTURES + code).toChar()
        code == DELETE -> SYMBOL_FOR_DELETE
        Character.getType(code).toByte() in REJECTED -> REPLACEMENT
        else -> null
    }
