package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.Limits
import dev.burufi.chatting.simple.shared.Validated
import dev.burufi.chatting.simple.shared.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class CommandTest {
    @Test
    fun `a line names a recipient and carries the rest as the body`() {
        assertEquals(Command.Send(clientName("bob"), "hi"), Command.of("@bob hi"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "hi there, how are you?",
            "an @carol inside is part of the message",
            "  leading space is the body's",
            "trailing space is too   ",
        ],
    )
    fun `the body is the rest of the line, verbatim`(body: String) {
        assertEquals(Command.Send(clientName("bob"), body), Command.of("@bob $body"))
    }

    @Test
    fun `the body sent is the normalized one`() {
        // The client checks what the server will check, so it must send what it checked.
        assertEquals(Command.Send(clientName("bob"), "a\nb"), Command.of("@bob a\r\nb"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t"])
    fun `a blank line is not a message`(line: String) {
        assertEquals(Command.Nothing, Command.of(line))
    }

    @ParameterizedTest
    @ValueSource(strings = ["bob hi", "hello", "/msg bob hi", " @bob hi"])
    fun `a line that names nobody is refused here rather than sent`(line: String) {
        assertTrue(Command.of(line) is Command.Unusable, "'$line' was turned into a message")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/exit", "/exit ", "/exit	", "/exit   "])
    fun `a command is itself whatever trails it in whitespace`(line: String) {
        assertEquals(Command.Exit, Command.of(line))
    }

    @Test
    fun `help is a command`() {
        assertEquals(Command.Help, Command.of("/help"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/", "/quit", "/exit now", "/EXIT", "/help me"])
    fun `a line this client does not know is refused as a command, not as a message`(line: String) {
        val reason = (Command.of(line) as Command.Unusable).reason
        assertFalse(reason.startsWith("start a message with"), "answered with the message syntax: $reason")
        assertTrue(reason.contains("/help"), "the refusal does not say where the commands are: $reason")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/exit", "/help", "/ and on"])
    fun `a sigil inside a body is body`(body: String) {
        // Only the head of the line decides who the line is for.
        assertEquals(Command.Send(clientName("bob"), body), Command.of("@bob $body"))
    }

    @Test
    fun `the help names both sigils`() {
        assertTrue(Command.HELP.contains('@'), "the help does not name the recipient sigil")
        assertTrue(Command.HELP.contains("/exit"), "the help does not list /exit")
        assertTrue(Command.HELP.contains("/help"), "the help does not list /help")
    }

    @Test
    fun `a recipient with nothing after it is refused`() {
        assertTrue(Command.of("@bob") is Command.Unusable)
    }

    @ParameterizedTest
    @ValueSource(strings = ["@telltale", "telltale", "@telltale[2J", "@telltale! hi", "/telltale"])
    fun `a refusal does not quote the line it refused`(line: String) {
        // Stdin is not always a keyboard, and the reason goes to the terminal unescaped
        // until W-09. Nothing here has passed ClientName or Validation yet.
        val reason = (Command.of(line) as Command.Unusable).reason
        assertFalse(reason.contains("telltale"), "the refusal quoted the line: $reason")
        assertFalse(reason.contains(ESC), "the refusal carries the escape itself")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Bob", "ab", "admin", "bob!"])
    fun `a recipient breaking the name rules is refused with the shared reason`(to: String) {
        assertEquals(Command.Unusable(nameRefusal(to)), Command.of("@$to hi"))
    }

    @ParameterizedTest
    @MethodSource("refusedBodies")
    fun `a body breaking a rule is refused with the shared reason`(body: String) {
        val expected = (Validation.validateBody(body) as Validated.Invalid).reason
        assertEquals(Command.Unusable(expected), Command.of("@bob $body"))
    }

    companion object {
        private val ESC = Char(0x1B)

        @JvmStatic
        fun refusedBodies(): List<Arguments> =
            listOf(
                argumentSet("empty", " "),
                argumentSet("past the byte cap", "a".repeat(Limits.MAX_BODY_BYTES + 1)),
                argumentSet("past the line cap", "a\n".repeat(Limits.MAX_BODY_LINES) + "a"),
                argumentSet("an escape sequence", "a$ESC[2Jb"),
            )
    }
}
