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

    /** Close the client app. */
    data object Exit : Command

    data object Help : Command

    /** Refused command, with a reason to print. */
    data class Unusable(
        val reason: String,
    ) : Command

    data object Nothing : Command

    companion object {
        private const val RECIPIENT = '@'
        private const val COMMAND = '/'

        val HELP =
            """
            |$RECIPIENT<name> <message>  send a message to someone connected
            |${COMMAND}help              this list
            |${COMMAND}exit              leave
            """.trimMargin()

        /**
         * Read one typed line. The sigil at its head decides who the line is for:
         * [RECIPIENT] addresses someone connected, [COMMAND] addresses this client.
         */
        fun of(line: String): Command {
            if (line.isBlank()) return Nothing
            return when (line.first()) {
                COMMAND -> command(line)
                RECIPIENT -> message(line)
                else ->
                    Unusable(
                        "start a message with $RECIPIENT<name>, as in ${RECIPIENT}bob hello, " +
                            "or ${COMMAND}help for the rest",
                    )
            }
        }

        private fun command(line: String): Command =
            when (line.substring(1).trim()) {
                "exit" -> Exit
                "help" -> Help
                else -> Unusable("no such command; ${COMMAND}help lists them")
            }

        private fun message(line: String): Command {
            // Everything past the first space is the body, so it may hold spaces and
            // further sigils without any of it needing to be escaped.
            val split = line.indexOf(' ')
            if (split < 0) return Unusable("add a message after the name, as in ${RECIPIENT}bob hello")

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
