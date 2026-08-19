package dev.burufi.chatting.simple.shared

object TestCharacters {
    val NUL = Char(0x00)
    val BEL = Char(0x07)
    val ESC = Char(0x1B)
    val DEL = Char(0x7F)
    val NEL = Char(0x85)
    val CSI = Char(0x9B)

    val BIDI_OVERRIDE = Char(0x202E)
    val BIDI_ISOLATE = Char(0x2066)
    val ZERO_WIDTH = Char(0x200B)
    val ZERO_WIDTH_JOINER = Char(0x200D)
    val BYTE_ORDER_MARK = Char(0xFEFF)

    val HIGH_SURROGATE = Char(0xD83D)
    val LOW_SURROGATE = Char(0xDE80)

    /** U+1F680, the pair whose halves are refused one at a time below. */
    val ROCKET = "$HIGH_SURROGATE$LOW_SURROGATE"

    /** Two UTF-8 bytes to one character, which is what the cap cases turn on. */
    val E_ACUTE = Char(0xE9)
    val CYRILLIC_A = Char(0x430)
    val REPLACEMENT = Char(0xFFFD)
}
