package dev.burufi.chatting.simple.client

import com.github.ajalt.clikt.command.test
import dev.burufi.chatting.simple.shared.Endpoint
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChatCommandTest {
    @Test
    fun `a name is required`() =
        runBlocking {
            val result = ChatCommand().test("")
            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("--name"), result.output)
        }

    @Test
    fun `a name that breaks the rules is refused with the shared reason`() =
        runBlocking {
            val result = ChatCommand().test("--name Bob")
            assertEquals(1, result.statusCode)
            assertEquals(nameRefusal("Bob"), result.output.trim())
        }

    @Test
    fun `a reserved name is refused before any socket is opened`() =
        runBlocking {
            val result = ChatCommand().test("--name admin")
            assertEquals(1, result.statusCode)
            assertEquals(nameRefusal("admin"), result.output.trim())
        }

    @Test
    fun `the help names the host and port defaults`() =
        runBlocking {
            val result = ChatCommand().test("--help")
            assertEquals(0, result.statusCode)
            assertTrue(result.output.contains("--host"), result.output)
            assertTrue(result.output.contains("--port"), result.output)
        }

    @Test
    fun `the port defaults to the one the server listens on`() {
        assertEquals(8080, Endpoint.DEFAULT_PORT)
    }

    @Test
    fun `the url puts each part where it belongs`() {
        assertEquals("ws://localhost:8080/ws?name=alice", chatUrl("localhost", 8080, clientName("alice")))
        assertEquals("ws://[::1]:9000/ws?name=bob", chatUrl("[::1]", 9000, clientName("bob")))
    }

    @Test
    fun `a host that could redirect the connection is refused before any socket opens`() =
        runBlocking {
            val result = ChatCommand().test("--name alice --host evil.com/#")
            assertEquals(1, result.statusCode)
            assertTrue(result.output.contains("--host"), result.output)

            // The same rule as every other refusal: nothing unvouched-for is quoted back.
            assertFalse(result.output.contains("evil.com"), result.output)
        }

    @ParameterizedTest
    @ValueSource(strings = ["--name alice --port 0", "--name alice --port 65536", "--name alice --port -1"])
    fun `a port outside the range is refused`(argv: String) =
        runBlocking {
            assertEquals(1, ChatCommand().test(argv).statusCode)
        }
}
