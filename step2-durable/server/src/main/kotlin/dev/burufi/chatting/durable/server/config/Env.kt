package dev.burufi.chatting.durable.server.config

import java.nio.file.Files
import java.nio.file.Path

class ConfigException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class Config(
    private val env: Map<String, String> = System.getenv(),
    private val readFile: (String) -> String = { Files.readString(Path.of(it)) },
) {
    fun optional(name: String): String? {
        val fileName = "${name}_FILE"
        val direct = env[name]?.takeIf { it.isNotEmpty() }
        val path = env[fileName]?.takeIf { it.isNotEmpty() }

        if (direct != null && path != null) {
            throw ConfigException("$name and $fileName are both set; use one or the other")
        }
        if (path == null) return direct

        val contents =
            try {
                readFile(path)
            } catch (e: Exception) {
                throw ConfigException("$fileName points at $path, which cannot be read", e)
            }
        return contents.trimEnd('\r', '\n')
    }

    fun required(name: String): String = optional(name) ?: throw ConfigException("$name is required; set it or ${name}_FILE")

    fun string(
        name: String,
        default: String,
    ): String = optional(name) ?: default

    fun int(
        name: String,
        default: Int,
    ): Int {
        val raw = optional(name) ?: return default
        return raw.toIntOrNull() ?: throw ConfigException("$name must be a number, but was '$raw'")
    }
}
