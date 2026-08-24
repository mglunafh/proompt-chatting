package dev.burufi.chatting.durable.shared.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CloseCodeTest {
    @Test
    fun `the codes mirror the upgrade's HTTP statuses`() {
        assertEquals(4401, CloseCode.SESSION_INVALID.code)
        assertEquals(4403, CloseCode.ACCOUNT_DISABLED.code)
        assertEquals(4409, CloseCode.SESSION_DISPLACED.code)
    }

    @ParameterizedTest
    @ValueSource(ints = [4401, 4403, 4409])
    fun `a known code resolves back to its member`(code: Int) {
        assertEquals(code, CloseCode.of(code)?.code)
    }

    @ParameterizedTest
    @ValueSource(ints = [1000, 1001, 1006, 4400, 4402, 4999, 0])
    fun `anything else is unrecognized, which is what means retry with backoff`(code: Int) {
        assertNull(CloseCode.of(code))
    }
}
