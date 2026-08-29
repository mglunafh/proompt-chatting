package dev.burufi.chatting.simple.client

import dev.burufi.chatting.simple.shared.ClientFrame

sealed interface LineAction {
    data class Send(
        val frame: ClientFrame.Send,
    ) : LineAction

    data object Exit : LineAction

    data object Help : LineAction

    data class UnknownCommand(
        val word: String,
    ) : LineAction

    data object NotACommand : LineAction
}

internal fun parseLine(line: String): LineAction =
    when (
        line.firstOrNull()
    ) {
        '@' -> parseSendLine(line)
        '/' -> parseSlashLine(line)
        else -> LineAction.NotACommand
    }

private fun parseSendLine(line: String): LineAction {
    val rest = line.drop(1)
    val space = rest.indexOf(' ')
    if (space <= 0) return LineAction.NotACommand
    val recipient = rest.substring(0, space)
    val body = rest.substring(space + 1)
    return LineAction.Send(ClientFrame.Send(recipient = recipient, body = body))
}

private fun parseSlashLine(line: String): LineAction {
    val firstWord = line.drop(1).takeWhile { it != ' ' && it != '\t' }
    return when (firstWord) {
        "exit" -> LineAction.Exit
        "help" -> LineAction.Help
        "" -> LineAction.NotACommand
        else -> LineAction.UnknownCommand(firstWord)
    }
}
