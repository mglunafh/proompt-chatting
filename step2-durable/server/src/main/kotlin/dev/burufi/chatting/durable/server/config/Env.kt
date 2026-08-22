package dev.burufi.chatting.durable.server.config

import java.nio.file.Files
import java.nio.file.Path

class ConfigException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Where a resolved value came from; `null` for a value that fell through to the declared default. */
data class Origin(
    val source: Source?,
) {
    val label get() = source?.name ?: "default"
}

class ResolvedConfig(
    private val values: Map<Setting, String>,
    private val origins: Map<Setting, Origin>,
) {
    fun string(setting: Setting): String = values.getValue(setting)

    fun int(setting: Setting): Int = string(setting).toInt()

    fun originOf(setting: Setting): Origin = origins.getValue(setting)

    /** One line per setting for the boot log; a secret never renders its value. */
    fun report(): List<String> =
        Setting.entries.map { setting ->
            val shown = if (setting.secret) Secret.REDACTED else string(setting)
            "${setting.env} = $shown (${originOf(setting).label})"
        }
}

/**
 * Resolves each setting against the sources in order. The rules are in
 * `docs/general/notes-configuration.md`, which stays authoritative.
 */
class Config(
    private val sources: List<Source> = defaultSources(),
    private val readFile: (Path) -> String = { Files.readString(it) },
) {
    /** Every failure is collected, so a boot with three mistakes reports three. */
    fun resolveAll(): ResolvedConfig {
        val values = mutableMapOf<Setting, String>()
        val origins = mutableMapOf<Setting, Origin>()
        val problems = mutableListOf<String>()

        for (setting in Setting.entries) {
            try {
                val (value, origin) = resolve(setting)
                values[setting] = checkType(setting, value)
                origins[setting] = origin
            } catch (e: ConfigException) {
                problems += e.message ?: e.toString()
            }
        }

        if (problems.isNotEmpty()) {
            throw ConfigException(problems.joinToString(prefix = "configuration rejected:\n  - ", separator = "\n  - "))
        }
        return ResolvedConfig(values, origins)
    }

    private fun resolve(setting: Setting): Pair<String, Origin> {
        for (source in sources) {
            val (direct, path) = source.lookup(setting)

            if (direct != null && path != null) {
                throw ConfigException(bothForms(setting, source))
            }
            if (path != null) return read(setting, source, path) to Origin(source)
            if (direct != null) return direct to Origin(source)
        }
        return (setting.default ?: throw ConfigException(missing(setting))) to Origin(null)
    }

    private fun read(
        setting: Setting,
        source: Source,
        path: String,
    ): String {
        val anchored = source.anchor(path)
        val contents =
            try {
                readFile(anchored)
            } catch (e: Exception) {
                throw ConfigException("${fileSpelling(setting, source)} points at $anchored, which cannot be read", e)
            }
        return contents.trimEnd('\r', '\n')
    }

    private fun checkType(
        setting: Setting,
        value: String,
    ): String {
        if (setting.type == Setting.Type.INT && value.toIntOrNull() == null) {
            throw ConfigException("${setting.env} must be a number, but was '$value'")
        }
        return value
    }

    private fun bothForms(
        setting: Setting,
        source: Source,
    ) = "${spelling(setting, source)} and ${fileSpelling(setting, source)} are both set " +
        "in the ${source.name}; use one or the other"

    private fun missing(setting: Setting) =
        "${setting.env} is required; set it or ${setting.envFile}, or their ${setting.property} equivalents"

    private fun spelling(
        setting: Setting,
        source: Source,
    ) = if (source is Environment) setting.env else setting.property

    private fun fileSpelling(
        setting: Setting,
        source: Source,
    ) = if (source is Environment) setting.envFile else setting.propertyFile
}
