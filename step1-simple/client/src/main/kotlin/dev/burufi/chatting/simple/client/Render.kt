package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientName
import dev.burufi.chatting.simple.shared.ServerFrame

private const val CONTINUATION = "    "

/** A frame as text for a terminal. Later lines are indented. */
fun render(
    frame: ServerFrame,
    me: ClientName,
): String =
    when (frame) {
        is ServerFrame.Roster ->
            if (frame.names.isEmpty()) "nobody else is here" else "here: ${frame.names.joinToString(", ")}"

        is ServerFrame.UserJoined -> "${frame.name} joined"
        is ServerFrame.UserLeft -> "${frame.name} left"

        // A send to oneself arrives once with `from` and `to` both ours, and reads as
        // what it was: something we sent.
        is ServerFrame.Message ->
            if (frame.from == me.value) "-> ${frame.to}: ${frame.body}" else "${frame.from}: ${frame.body}"

        is ServerFrame.Error -> "! ${frame.code.name.lowercase()}: ${frame.reason}"
    }.replace("\n", "\n$CONTINUATION")
