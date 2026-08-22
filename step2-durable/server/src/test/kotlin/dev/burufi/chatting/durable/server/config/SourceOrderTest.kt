package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SourceOrderTest {
    @TempDir
    lateinit var dir: Path

    private fun write(
        name: String,
        contents: String,
    ): Path = Files.writeString(dir.resolve(name), contents)

    private fun namedFile(vararg lines: String) = NamedFile(write("server.properties", lines.joinToString("\n")), properties(*lines))

    private fun properties(vararg lines: String) =
        lines.associate { line ->
            val (key, value) = line.split("=", limit = 2)
            key to value
        }

    private fun resolveConfig(vararg sources: Source) = Config(sources.toList()).resolveAll()

    private val passwords = Environment(mapOf("DB_PASSWORD" to "hunter2"))

    @Test
    fun `a system property outranks every other source`() {
        val resolved =
            resolveConfig(
                SystemProperties(mapOf("db.host" to "from-property")),
                Environment(mapOf("DB_HOST" to "from-env", "DB_PASSWORD" to "hunter2")),
                namedFile("db.host=from-named"),
                PackagedFile(properties("db.host=from-packaged")),
            )

        assertEquals("from-property", resolved.string(Setting.DB_HOST))
        assertEquals("system property", resolved.originOf(Setting.DB_HOST).label)
    }

    @Test
    fun `the environment outranks both files`() {
        val resolved =
            resolveConfig(
                Environment(mapOf("DB_HOST" to "from-env", "DB_PASSWORD" to "hunter2")),
                namedFile("db.host=from-named"),
                PackagedFile(properties("db.host=from-packaged")),
            )

        assertEquals("from-env", resolved.string(Setting.DB_HOST))
        assertEquals("environment", resolved.originOf(Setting.DB_HOST).label)
    }

    @Test
    fun `the named file outranks the packaged one`() {
        val resolved = resolveConfig(passwords, namedFile("db.host=from-named"), PackagedFile(properties("db.host=from-packaged")))

        assertEquals("from-named", resolved.string(Setting.DB_HOST))
    }

    @Test
    fun `the packaged file outranks the declared default`() {
        val resolved = resolveConfig(passwords, PackagedFile(properties("db.host=from-packaged")))

        assertEquals("from-packaged", resolved.string(Setting.DB_HOST))
    }

    @Test
    fun `a setting the named file omits still falls through to the packaged one`() {
        val resolved =
            resolveConfig(
                passwords,
                namedFile("db.host=from-named"),
                PackagedFile(properties("db.pool.max-size=20")),
            )

        assertEquals("from-named", resolved.string(Setting.DB_HOST))
        assertEquals(20, resolved.int(Setting.DB_POOL_MAX_SIZE))
    }

    @Test
    fun `a higher source answering in the file form displaces a lower source's plain value`() {
        val secret = write("secret", "from-mount")

        val resolved =
            resolveConfig(
                Environment(mapOf("DB_PASSWORD_FILE" to secret.toAbsolutePath().toString())),
                namedFile("db.password=from-named"),
            )

        assertEquals(
            "from-mount",
            resolved.string(Setting.DB_PASSWORD),
            "answering in either form wins outright, or the two layers read as an ambiguity that is not one",
        )
    }

    @Test
    fun `a relative path in a named file anchors to that file's directory, not the working one`() {
        Files.createDirectory(dir.resolve("secrets"))
        Files.writeString(dir.resolve("secrets/db_password"), "from-mount")

        val resolved = resolveConfig(namedFile("db.password.file=secrets/db_password"))

        assertEquals(
            "from-mount",
            resolved.string(Setting.DB_PASSWORD),
            "moving the file must move what it points at, whatever directory the process runs in",
        )
    }

    @Test
    fun `a path from the environment is taken as given, having no file to be relative to`() {
        val secret = write("secret", "from-mount")

        val resolved = resolveConfig(Environment(mapOf("DB_PASSWORD_FILE" to secret.toAbsolutePath().toString())))

        assertEquals("from-mount", resolved.string(Setting.DB_PASSWORD))
    }

    @Test
    fun `the packaged file refuses to name a path, having no directory to anchor it against`() {
        val error =
            assertThrows(ConfigException::class.java) {
                resolveConfig(passwords, PackagedFile(properties("db.password.file=/run/secrets/db_password")))
            }

        assertTrue(
            error.message!!.contains("db.password.file") && error.message!!.contains("packaged"),
            "the message must say why a path cannot live in the jar: ${error.message}",
        )
    }

    @Test
    fun `the report attributes each setting to the source that answered it`() {
        val resolved =
            resolveConfig(
                Environment(mapOf("DB_HOST" to "from-env", "DB_PASSWORD" to "hunter2")),
                PackagedFile(properties("db.pool.max-size=20")),
            )

        assertEquals("environment", resolved.originOf(Setting.DB_HOST).label)
        assertTrue(resolved.originOf(Setting.DB_POOL_MAX_SIZE).label.contains("packaged"))
        assertEquals("default", resolved.originOf(Setting.DB_PORT).label)
    }
}
