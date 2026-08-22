package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** The rules within a single source: the two spellings, the file form, and the type check. */
class ConfigTest {
    @TempDir
    lateinit var dir: Path

    private fun file(
        name: String,
        contents: String,
    ): String = Files.writeString(dir.resolve(name), contents).toAbsolutePath().toString()

    private fun resolve(vararg values: Pair<String, String>) = Config(listOf(Environment(mapOf(*values)))).resolveAll()

    private fun withPassword(vararg values: Pair<String, String>) = resolve("DB_PASSWORD" to "hunter2", *values)

    @Test
    fun `the plain spelling is used when no file is named`() {
        assertEquals("hunter2", resolve("DB_PASSWORD" to "hunter2").string(Setting.DB_PASSWORD))
    }

    @Test
    fun `the file's contents are used when the file spelling is named`() {
        val path = file("db_password", "hunter2")

        assertEquals("hunter2", resolve("DB_PASSWORD_FILE" to path).string(Setting.DB_PASSWORD))
    }

    @Test
    fun `a trailing newline is stripped, as the postgres entrypoint strips it`() {
        val path = file("db_password", "hunter2\n")

        assertEquals("hunter2", resolve("DB_PASSWORD_FILE" to path).string(Setting.DB_PASSWORD))
    }

    @Test
    fun `whitespace inside a value survives, since only newlines are the file format's own`() {
        val path = file("db_password", "hunter 2 \n")

        assertEquals("hunter 2 ", resolve("DB_PASSWORD_FILE" to path).string(Setting.DB_PASSWORD))
    }

    @Test
    fun `both spellings in one source is refused rather than resolved by precedence`() {
        val path = file("db_password", "from-file")

        val error =
            assertThrows(ConfigException::class.java) {
                resolve("DB_PASSWORD" to "direct", "DB_PASSWORD_FILE" to path)
            }

        assertTrue(
            error.message!!.contains("DB_PASSWORD ") && error.message!!.contains("DB_PASSWORD_FILE"),
            "the message must name both spellings so the operator knows what to unset: ${error.message}",
        )
    }

    @Test
    fun `a file spelling pointing at nothing names the path it could not read`() {
        val missing = dir.resolve("absent").toAbsolutePath().toString()

        val error = assertThrows(ConfigException::class.java) { resolve("DB_PASSWORD_FILE" to missing) }

        assertTrue(
            error.message!!.contains(missing),
            "a wrong mount path is only diagnosable if the message repeats it: ${error.message}",
        )
    }

    @Test
    fun `a required setting that is set nowhere names both spellings it accepts`() {
        val error = assertThrows(ConfigException::class.java) { resolve() }

        assertTrue(
            error.message!!.contains("DB_PASSWORD_FILE") && error.message!!.contains("db.password"),
            "the operator must learn the file and property forms exist from the failure: ${error.message}",
        )
    }

    @Test
    fun `an empty value counts as unset, since compose substitutes one for a missing variable`() {
        assertEquals("localhost", withPassword("DB_HOST" to "").string(Setting.DB_HOST))
    }

    @Test
    fun `a setting nobody supplies falls back to its declared default`() {
        val resolved = withPassword()

        assertEquals("localhost", resolved.string(Setting.DB_HOST))
        assertEquals(5432, resolved.int(Setting.DB_PORT))
        assertEquals("default", resolved.originOf(Setting.DB_HOST).label)
    }

    @Test
    fun `a numeric setting that is not a number is refused at boot rather than coerced`() {
        val error = assertThrows(ConfigException::class.java) { withPassword("DB_PORT" to "five") }

        assertTrue(
            error.message!!.contains("DB_PORT") && error.message!!.contains("five"),
            "the message must show the offending value: ${error.message}",
        )
    }

    @Test
    fun `every problem is reported at once, so three mistakes are not three restarts`() {
        val error =
            assertThrows(ConfigException::class.java) {
                resolve("DB_PORT" to "five", "DB_POOL_MAX_SIZE" to "many")
            }

        assertTrue(
            listOf("DB_PORT", "DB_POOL_MAX_SIZE", "DB_PASSWORD").all { error.message!!.contains(it) },
            "one boot should name every broken setting, but said: ${error.message}",
        )
    }

    @Test
    fun `the boot report never prints a secret`() {
        val report = withPassword().report()

        assertTrue(
            report.none { it.contains("hunter2") },
            "the report goes to the log store, so a password in it would be persisted: $report",
        )
        assertTrue(
            report.any { it.startsWith("DB_PASSWORD = ${Secret.REDACTED}") },
            "the secret must still appear, redacted, so its origin is visible: $report",
        )
    }
}
