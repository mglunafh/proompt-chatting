package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParseLineTest {
    @Test
    fun `parses send lines`() {
        assertEquals(
            LineAction.Send(ClientFrame.Send(recipient = "bob", body = "hello")),
            parseLine("@bob hello"),
        )
        assertEquals(
            LineAction.Send(ClientFrame.Send(recipient = "bob", body = "hello there friend")),
            parseLine("@bob hello there friend"),
        )
    }

    @Test
    fun `parses exit and help`() {
        assertEquals(LineAction.Exit, parseLine("/exit"))
        assertEquals(LineAction.Help, parseLine("/help"))
    }

    @Test
    fun `slash commands take only the first word`() {
        assertEquals(LineAction.Exit, parseLine("/exit now"))
        assertEquals(LineAction.Help, parseLine("/help me"))
        assertEquals(LineAction.UnknownCommand("foo"), parseLine("/foo bar baz"))
    }

    @Test
    fun `tab separates the slash word the same as space`() {
        assertEquals(LineAction.Exit, parseLine("/exit\tnow"))
        assertEquals(LineAction.UnknownCommand("foo"), parseLine("/foo\tbar"))
    }

    @Test
    fun `unknown slash word is refused as a command`() {
        assertEquals(LineAction.UnknownCommand("foo"), parseLine("/foo"))
        assertEquals(LineAction.UnknownCommand("list"), parseLine("/list"))
    }

    @Test
    fun `malformed send lines fall through to NotACommand`() {
        assertEquals(LineAction.NotACommand, parseLine("@bob"))
        assertEquals(LineAction.NotACommand, parseLine("@"))
        assertEquals(LineAction.NotACommand, parseLine("@ body"))
        assertEquals(LineAction.NotACommand, parseLine("@\t"))
    }

    @Test
    fun `lines without a sigil are NotACommand`() {
        assertEquals(LineAction.NotACommand, parseLine("hello"))
        assertEquals(LineAction.NotACommand, parseLine(""))
        assertEquals(LineAction.NotACommand, parseLine("   "))
    }

    @Test
    fun `lone slash is NotACommand`() {
        assertEquals(LineAction.NotACommand, parseLine("/"))
    }
}
