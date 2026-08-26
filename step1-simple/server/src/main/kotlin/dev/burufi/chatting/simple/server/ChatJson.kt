package dev.burufi.chatting.simple.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal val ChatJson: Json = Json.Default
