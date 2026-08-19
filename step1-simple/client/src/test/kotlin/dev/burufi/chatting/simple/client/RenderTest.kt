package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.ServerFrame
import dev.burufi.chatting.simple.shared.TestCharacters.BIDI_OVERRIDE
import dev.burufi.chatting.simple.shared.TestCharacters.CSI
import dev.burufi.chatting.simple.shared.TestCharacters.ESC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderTest {
    /** The two a line may legitimately carry, as codes so no escape lands in the source. */
    private val tab = 0x09
    private val newline = 0x0A

    private val me = clientName("alice")

    @Test
    fun `an empty roster says so rather than trailing off`() {
        assertEquals("nobody else is here", render(ServerFrame.Roster(emptyList()), me))
    }

    @Test
    fun `a roster names who is here`() {
        assertEquals("here: bob, carol", render(ServerFrame.Roster(listOf("bob", "carol")), me))
    }

    @Test
    fun `presence edges read as what happened`() {
        assertEquals("bob joined", render(ServerFrame.UserJoined("bob"), me))
        assertEquals("bob left", render(ServerFrame.UserLeft("bob"), me))
    }

    @Test
    fun `a message from someone else is theirs`() {
        assertEquals("bob: hi", render(ServerFrame.Message("bob", "alice", "hi"), me))
    }

    @Test
    fun `the echo of our own message reads as outgoing`() {
        assertEquals("-> bob: hi", render(ServerFrame.Message("alice", "bob", "hi"), me))
    }

    @Test
    fun `a note to ourselves is outgoing, not something that arrived`() {
        // `from` and `to` are both ours here, so the direction turns on `from` alone.
        assertEquals("-> alice: note", render(ServerFrame.Message("alice", "alice", "note"), me))
    }

    @Test
    fun `a refusal is marked as one and keeps its code`() {
        val error = ServerFrame.Error(ErrorCode.UNKNOWN_RECIPIENT, "'bob' is not connected")
        assertEquals("! unknown_recipient: 'bob' is not connected", render(error, me))
    }

    @Test
    fun `a roster name is made inert`() {
        // The pairing ChatClient makes. That it makes it is pinned over a socket in
        // ChatClientTest; what it pairs with is pinned in InertTest.
        assertEquals(
            "here: a␛b, carol",
            inert(render(ServerFrame.Roster(listOf("a${ESC}b", "carol")), me)),
        )
    }

    @Test
    fun `a body spanning lines cannot forge a line of its own`() {
        // Newlines are legitimate in a body, so what stops the second line reading as a
        // message from alice is where it starts, not whether it is there.
        assertEquals(
            "bob: hi\n    alice: trust me",
            render(ServerFrame.Message("bob", "alice", "hi\nalice: trust me"), me),
        )
    }

    @Test
    fun `a name from a server that did not check one cannot forge a line either`() {
        // The server rejects this, but the server is not what the client trusts.
        assertEquals(
            "bob\n    carol: trust me joined",
            render(ServerFrame.UserJoined("bob\ncarol: trust me"), me),
        )
    }

    @Test
    fun `no frame can carry a character that drives the terminal`() {
        val hostile = "a$ESC[2Jb${CSI}c${BIDI_OVERRIDE}d"
        val frames =
            listOf(
                ServerFrame.Roster(listOf(hostile, hostile)),
                ServerFrame.UserJoined(hostile),
                ServerFrame.UserLeft(hostile),
                ServerFrame.Message(hostile, hostile, hostile),
                ServerFrame.Error(ErrorCode.MALFORMED_FRAME, hostile),
            )

        // Every frame with every string field hostile, so a field added later is covered
        // by this rather than by somebody remembering to sanitize it.
        assertEquals(
            ServerFrame::class.sealedSubclasses.toSet(),
            frames.map { it::class }.toSet(),
            "a frame the server can send is not among these",
        )

        frames.forEach { frame ->
            inert(render(frame, me)).forEach { char ->
                assertTrue(
                    char.code == newline ||
                        char.code == tab ||
                        (char.code >= 0x20 && char.code != 0x7F && char.code !in 0x80..0x9F),
                    "a control character reached the terminal from $frame",
                )
                assertNotEquals(
                    Character.FORMAT,
                    Character.getType(char).toByte(),
                    "a format character reached the terminal from $frame",
                )
            }
        }
    }

    @Test
    fun `every server frame has a line`() {
        val rendered =
            listOf(
                ServerFrame.Roster(emptyList()),
                ServerFrame.UserJoined("bob"),
                ServerFrame.UserLeft("bob"),
                ServerFrame.Message("bob", "alice", "hi"),
                ServerFrame.Error(ErrorCode.MALFORMED_FRAME, "no"),
            ).map { it::class }

        assertEquals(
            ServerFrame::class.sealedSubclasses.toSet(),
            rendered.toSet(),
            "a frame the server can send has no case here",
        )
    }
}
