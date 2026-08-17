package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.ServerFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderTest {
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
