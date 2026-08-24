package dev.burufi.chatting.durable.shared

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitsTest {
    @Test
    fun `the frame cap leaves room for the JSON envelope around a body at its own cap`() {
        assertTrue(
            Limits.MAX_FRAME_BYTES > Limits.MAX_BODY_BYTES,
            "the gap is what absorbs the envelope and the escaping of a worst-case body",
        )
    }

    @Test
    fun `the HTTP cap is the loosest, since it bounds a different surface`() {
        assertTrue(
            Limits.MAX_HTTP_BODY_BYTES > Limits.MAX_FRAME_BYTES,
            "a REST body carries more than one frame ever does, attachments excluded",
        )
    }

    @Test
    fun `a body can hold more lines than the line cap allows within the byte cap`() {
        assertTrue(
            Limits.MAX_BODY_BYTES > Limits.MAX_BODY_LINES,
            "the line cap exists precisely because the byte cap does not imply it",
        )
    }
}
