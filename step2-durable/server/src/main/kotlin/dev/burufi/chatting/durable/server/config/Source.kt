package dev.burufi.chatting.durable.server.config

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

const val CONFIG_PROPERTY = "server.config"
const val PACKAGED_RESOURCE = "/server.properties"

/**
 * One place a setting may be written, in one spelling, with one rule for anchoring paths.
 * In precedence order, highest first:
 *
 * - [SystemProperties] — `-Ddb.host=…`, one invocation.
 * - [Environment] — `DB_HOST=…`, the deployment's contract.
 * - [NamedFile] — the properties file at `-D$CONFIG_PROPERTY`.
 * - [PackagedFile] — `$PACKAGED_RESOURCE` shipped in the jar.
 *
 * Below them all sits [Setting.default], which is not a source.
 */
sealed interface Source {
    val name: String

    /**
     * Returns the value itself as well as path to a file containing the value.
     * Either may be absent.
     */
    fun lookup(setting: Setting): Pair<String?, String?>

    /** Where a relative path found in this source points. */
    fun anchor(path: String): Path = Path.of(path)
}

private fun Map<String, String>.value(key: String): String? = this[key]?.takeIf { it.isNotEmpty() }

class SystemProperties(
    private val values: Map<String, String> = System.getProperties().toMap(),
) : Source {
    override val name = "system property"

    override fun lookup(setting: Setting) = values.value(setting.property) to values.value(setting.propertyFile)
}

class Environment(
    private val values: Map<String, String> = System.getenv(),
) : Source {
    override val name = "environment"

    override fun lookup(setting: Setting) = values.value(setting.env) to values.value(setting.envFile)
}

/** The file named by `-Dserver.config`. */
class NamedFile(
    private val file: Path,
    private val values: Map<String, String>,
) : Source {
    override val name = "$CONFIG_PROPERTY ($file)"

    override fun lookup(setting: Setting) = values.value(setting.property) to values.value(setting.propertyFile)

    override fun anchor(path: String): Path {
        val written = Path.of(path)
        val parent = file.toAbsolutePath().parent ?: return written
        return if (written.isAbsolute) written else parent.resolve(written).normalize()
    }
}

/** `/server.properties` from the jar. */
class PackagedFile(
    private val values: Map<String, String>,
) : Source {
    override val name = "packaged $PACKAGED_RESOURCE"

    init {
        // Checked on construction rather than on lookup, so a bad key is caught even when a
        // higher source shadows the setting it belongs to.
        values.keys.firstOrNull { it.endsWith(".file") }?.let {
            throw ConfigException("$it is set in the packaged $PACKAGED_RESOURCE, which cannot name a path")
        }
    }

    override fun lookup(setting: Setting) = values.value(setting.property) to null
}

fun readProperties(stream: InputStream): Map<String, String> {
    val properties = Properties()
    stream.use(properties::load)
    return properties.toMap()
}

/** The sources in precedence order, highest first, skipping the files that are not there. */
fun defaultSources(
    systemProperties: Map<String, String> = System.getProperties().toMap(),
    environment: Map<String, String> = System.getenv(),
): List<Source> =
    buildList {
        add(SystemProperties(systemProperties))
        add(Environment(environment))
        namedFileSource(systemProperties[CONFIG_PROPERTY])?.let(::add)
        packagedFileSource()?.let(::add)
    }

private fun namedFileSource(named: String?): NamedFile? {
    val path = named?.takeIf { it.isNotEmpty() }?.let(Path::of) ?: return null
    val values =
        try {
            Files.newInputStream(path).use(::readProperties)
        } catch (e: Exception) {
            // Naming the path was deliberate, so its absence is a mistake rather than a fallback.
            throw ConfigException("-D$CONFIG_PROPERTY names $path, which cannot be read", e)
        }
    return NamedFile(path, values)
}

private fun packagedFileSource(): PackagedFile? {
    val stream = Setting::class.java.getResourceAsStream(PACKAGED_RESOURCE) ?: return null
    return PackagedFile(readProperties(stream))
}

/** Picks String-typed keys with non-null values. */
private fun Properties.toMap(): Map<String, String> =
    stringPropertyNames()
        .mapNotNull { name ->
            getProperty(name)?.let { name to it }
        }.toMap()
