package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.server.chatModule
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class ClientExitTest {
    @Test
    fun `slash exit closes the socket`() =
        testApplication {
            application { chatModule() }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/chat?name=alice") {
                val lines = Channel<String>(Channel.UNLIMITED)
                val job =
                    launch {
                        runSession(lines) { /* no-op */ }
                    }
                lines.send("/exit")
                lines.close()
                job.join()

                val reason = withTimeoutOrNull(2000) { closeReason.await() }
                assertNotNull(reason, "socket should have a close reason after /exit")
            }
        }
}
