package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.Validated

/** A name the test asserts is valid, since only [ClientName.of] can make one. */
fun clientName(raw: String): ClientName =
    when (val result = ClientName.of(raw)) {
        is Validated.Valid -> result.value
        is Validated.Invalid -> error("'$raw' is not a usable test name: ${result.reason}")
    }

/** The reason the shared rules give for [raw], so a test can assert it travels unchanged. */
fun nameRefusal(raw: String): String = (ClientName.of(raw) as Validated.Invalid).reason
