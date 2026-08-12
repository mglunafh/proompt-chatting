package dev.burufi.chatting.simple.shared

import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitsTest {
    @Test
    fun `the caps hold their documented values`() {
        assertEquals(8 * 1024, Limits.MAX_BODY_BYTES)
        assertEquals(100, Limits.MAX_BODY_LINES)
        assertEquals(64L * 1024, Limits.MAX_FRAME_BYTES)
    }

    @Test
    fun `the frame cap exceeds the body cap`() {
        assertTrue(Limits.MAX_FRAME_BYTES > Limits.MAX_BODY_BYTES.toLong())
    }

    @Test
    fun `a worst-case body still fits inside the frame cap`() {
        // Every byte of this body escapes to two, which is the widest a valid body encodes.
        val frame: ClientFrame =
            ClientFrame.Send(
                to = "a".repeat(32),
                body = "\"".repeat(Limits.MAX_BODY_BYTES),
            )
        val encoded = ProtocolJson.STRICT.encodeToString(serializer<ClientFrame>(), frame)
        assertTrue(
            encoded.toByteArray().size < Limits.MAX_FRAME_BYTES,
            "worst-case frame is ${encoded.toByteArray().size} bytes",
        )
    }
}
