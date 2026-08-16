package dev.burufi.chatting.simple.server

import dev.burufi.chatting.simple.shared.ClientFrame
import dev.burufi.chatting.simple.shared.ErrorCode
import dev.burufi.chatting.simple.shared.Limits
import dev.burufi.chatting.simple.shared.ServerFrame
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource

class MessageRouterTest {
    private val registry = ConnectionRegistry()
    private val router = MessageRouter(registry)

    @Test
    fun `a message reaches the recipient and comes back to the sender`() =
        runBlocking {
            val alice = join("alice")
            val bob = join("bob")
            router.route(alice, ClientFrame.Send("bob", "hi"))

            val message = ServerFrame.Message("alice", "bob", "hi")
            assertEquals(listOf(message), bob.routed())
            assertEquals(listOf(message), alice.routed(), "the echo must be the same frame, so the client can pair them")
        }

    @Test
    fun `a message goes to its recipient and to nobody else`() =
        runBlocking {
            val alice = join("alice")
            join("bob")
            val carol = join("carol")
            router.route(alice, ClientFrame.Send("bob", "hi"))

            assertEquals(emptyList<ServerFrame>(), carol.routed(), "a direct message was broadcast")
        }

    @Test
    fun `a send to oneself arrives once`() =
        runBlocking {
            val alice = join("alice")
            router.route(alice, ClientFrame.Send("alice", "note"))

            // The delivery and the echo are the same client here, and its own name in
            // `from` is all it has to tell them apart with.
            assertEquals(listOf(ServerFrame.Message("alice", "alice", "note")), alice.routed())
        }

    @Test
    fun `a recipient that is not connected is refused`() =
        runBlocking {
            val alice = join("alice")
            router.route(alice, ClientFrame.Send("bob", "hi"))

            assertEquals(ErrorCode.UNKNOWN_RECIPIENT, alice.refusal().code)
        }

    @ParameterizedTest
    @MethodSource("impossibleNames")
    fun `a recipient that could never connect is refused like one that is simply away`(to: String) =
        runBlocking {
            val alice = join("alice")
            router.route(alice, ClientFrame.Send(to, "hi"))

            // One code, because a name that breaks the rules is not connected in the
            // only sense a sender can act on. INVALID_NAME stays the upgrade's.
            assertEquals(ErrorCode.UNKNOWN_RECIPIENT, alice.refusal().code)
        }

    @Test
    fun `a refusal does not quote a recipient that is not a name`() =
        runBlocking {
            val alice = join("alice")
            router.route(alice, ClientFrame.Send("$ESC[2Jtelltale", "hi"))

            // `to` is unbounded untrusted text until ClientName vouches for it, and the
            // reason travels back out to a terminal.
            val reason = alice.refusal().reason
            assertFalse(reason.contains("telltale"), "the refusal quoted the recipient: $reason")
            assertFalse(reason.contains(ESC), "the refusal carries the escape itself")
        }

    @ParameterizedTest
    @MethodSource("refusedBodies")
    fun `a refused body is answered to the sender and delivered to nobody`(
        body: String,
        code: ErrorCode,
    ) = runBlocking {
        val alice = join("alice")
        val bob = join("bob")
        router.route(alice, ClientFrame.Send("bob", body))

        assertEquals(code, alice.refusal().code)
        assertEquals(emptyList<ServerFrame>(), bob.routed(), "a refused body was delivered anyway")
    }

    @Test
    fun `the body is refused before the recipient is looked up`() =
        runBlocking {
            val alice = join("alice")
            router.route(alice, ClientFrame.Send("nobody", ""))

            // Pins the order: a bad frame gets the same answer whoever is connected.
            assertEquals(ErrorCode.BODY_EMPTY, alice.refusal().code)
        }

    @Test
    fun `the delivered body is the normalized one`() =
        runBlocking {
            val alice = join("alice")
            val bob = join("bob")
            router.route(alice, ClientFrame.Send("bob", "a\r\nb"))

            assertEquals(listOf(ServerFrame.Message("alice", "bob", "a\nb")), bob.routed())
        }

    private suspend fun join(name: String): RecordingConnection = RecordingConnection(name).also { registry.register(it) }

    /** What the router sent, past the roster the registration put there. */
    private fun RecordingConnection.routed(): List<ServerFrame> = frames().drop(1)

    private fun RecordingConnection.refusal(): ServerFrame.Error {
        val routed = routed()
        assertTrue(routed.singleOrNull() is ServerFrame.Error, "expected one refusal, got $routed")
        return routed.single() as ServerFrame.Error
    }

    companion object {
        private val ESC = Char(0x1B)

        @JvmStatic
        fun impossibleNames(): List<Arguments> =
            listOf(
                argumentSet("uppercase, which the rules do not allow", "Bob"),
                argumentSet("too short", "ab"),
                argumentSet("empty", ""),
                argumentSet("reserved, so nobody holds it", "admin"),
                argumentSet("punctuation", "bob!"),
            )

        /** One case per `BODY_` code, since the rules themselves are `BodyValidationTest`'s. */
        @JvmStatic
        fun refusedBodies(): List<Arguments> =
            listOf(
                argumentSet("empty", "", ErrorCode.BODY_EMPTY),
                argumentSet("past the byte cap", "a".repeat(Limits.MAX_BODY_BYTES + 1), ErrorCode.BODY_TOO_LARGE),
                argumentSet("past the line cap", "a\n".repeat(Limits.MAX_BODY_LINES) + "a", ErrorCode.BODY_TOO_MANY_LINES),
                argumentSet("an escape sequence", "a$ESC[2Jb", ErrorCode.BODY_INVALID_CHARACTERS),
            )
    }
}
