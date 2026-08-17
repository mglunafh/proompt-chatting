package dev.burufi.chatting.simple.client

import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking { ChatCommand().main(args) }
