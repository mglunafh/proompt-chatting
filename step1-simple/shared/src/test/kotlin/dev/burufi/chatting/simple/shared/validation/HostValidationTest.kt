package dev.burufi.chatting.simple.shared.validation

import dev.burufi.chatting.simple.shared.Validation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HostValidationTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "my-host.local",
            "example.com.",
            "xn--80ak6aa92e.com",
            "3com.com",
            "[::1]",
            "[fe80::1]",
        ],
    )
    fun `a hostname, an address or a bracketed IPv6 literal is a host`(host: String) {
        assertTrue(Validation.isHost(host), "'$host' was refused")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "evil.com/#",
            "evil.com?",
            "evil.com/path",
            "evil.com#fragment",
            "localhost:8080/ws?name=zzz#",
            "localhost:8080",
            "user@evil.com",
            "a b",
            "",
            ".",
            "-leading-hyphen.com",
            "trailing-hyphen-.com",
            "double..dot.com",
            "::1",
            "[::1",
            "[::1]extra",
        ],
    )
    fun `anything that could carry a path, query or credentials of its own is not a host`(host: String) {
        assertFalse(Validation.isHost(host), "'$host' was accepted as a host")
    }

    @Test
    fun `a label may run to the limit RFC 1035 sets, and no further`() {
        assertTrue(Validation.isHost("a".repeat(63) + ".com"))
        assertFalse(Validation.isHost("a".repeat(64) + ".com"))
    }

    @Test
    fun `a whole name may run to its own limit, and no further`() {
        val label = "a".repeat(63)
        val atCap = listOf(label, label, label, "a".repeat(61)).joinToString(".")
        assertTrue(Validation.isHost(atCap), "${atCap.length} characters was refused")
        assertFalse(Validation.isHost(atCap + "a"))
    }

    @Test
    fun `a digit may start a label, which RFC 952 forbade and RFC 1123 allows`() {
        assertTrue(Validation.isHost("3com.com"))
        assertTrue(Validation.isHost("1"))
    }
}
