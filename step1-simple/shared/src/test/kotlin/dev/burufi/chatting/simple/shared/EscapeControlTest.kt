package dev.burufi.chatting.simple.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EscapeControlTest {
    @Test
    fun `passes printable ASCII through`() {
        assertEquals("hello world 123 !@#", Validation.escapeControl("hello world 123 !@#"))
    }

    @Test
    fun `keeps tab and newline`() {
        assertEquals("a\tb\nc", Validation.escapeControl("a\tb\nc"))
    }

    @Test
    fun `empty string passes through`() {
        assertEquals("", Validation.escapeControl(""))
    }

    @Test
    fun `preserves multibyte UTF-8 text`() {
        assertEquals("héllo 🌍 wörld", Validation.escapeControl("héllo 🌍 wörld"))
    }

    @Test
    fun `strips an ANSI escape sequence`() {
        assertEquals("?[31mred", Validation.escapeControl("\u001B[31mred"))
    }

    @Test
    fun `strips C0 controls other than tab and newline`() {
        assertEquals("???", Validation.escapeControl("\u0001\u0002\u0003"))
    }

    @Test
    fun `strips a bare carriage return`() {
        assertEquals("a?b", Validation.escapeControl("a\rb"))
    }

    @Test
    fun `strips DEL`() {
        assertEquals("?", Validation.escapeControl("\u007F"))
    }

    @Test
    fun `strips C1 control characters`() {
        assertEquals("?", Validation.escapeControl("\u009B"))
    }

    @Test
    fun `strips a long escape that tries to clear the screen`() {
        assertEquals("?[2J", Validation.escapeControl("\u001B[2J"))
    }
}
