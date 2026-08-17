package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.Validated
import dev.burufi.chatting.simple.shared.Validation

/** What a typed line turned out to be. */
sealed interface Command {
    data class Send(
        val to: ClientName,
        val body: String,
    ) : Command

    /** Refused command, with a reason to print. */
    data class Unusable(
        val reason: String,
    ) : Command

    data object Nothing : Command

    companion object {
        private const val SIGIL = '@'

        /**
         * Read one typed line.
         */
        fun of(line: String): Command {
            if (line.isBlank()) return Nothing
            if (!line.startsWith(SIGIL)) {
                return Unusable("start a message with $SIGIL<name>, as in ${SIGIL}bob hello")
            }

            // Everything past the first space is the body, so it may hold spaces and
            // further sigils without any of it needing to be escaped.
            val split = line.indexOf(' ')
            // The line is never quoted back: nothing has vouched for it yet, and the
            // reason goes straight to the terminal.
            if (split < 0) return Unusable("add a message after the name, as in ${SIGIL}bob hello")

            val to =
                when (val name = ClientName.of(line.substring(1, split))) {
                    is Validated.Invalid -> return Unusable(name.reason)
                    is Validated.Valid -> name.value
                }

            return when (val body = Validation.validateBody(line.substring(split + 1))) {
                is Validated.Invalid -> Unusable(body.reason)
                // The normalized form, so the server validates what was checked here.
                is Validated.Valid -> Send(to, body.value)
            }
        }
    }
}
