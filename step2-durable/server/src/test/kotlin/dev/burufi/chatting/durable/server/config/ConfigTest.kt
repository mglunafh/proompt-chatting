package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path

class ConfigTest {
    @TempDir
    lateinit var dir: Path

    private fun secretFile(
        name: String,
        contents: String,
    ): String = Files.writeString(dir.resolve(name), contents).toAbsolutePath().toString()

    private fun config(vararg env: Pair<String, String>) = Config(mapOf(*env))

    @Test
    fun `the plain variable is used when no file is named`() {
        assertEquals("hunter2", config("DB_PASSWORD" to "hunter2").required("DB_PASSWORD"))
    }

    @Test
    fun `the file's contents are used when the file variable is named`() {
        val path = secretFile("db_password", "hunter2")
        assertEquals("hunter2", config("DB_PASSWORD_FILE" to path).required("DB_PASSWORD"))
    }

    @ParameterizedTest(name = "{0} is stripped from a secret file, as the postgres entrypoint strips it")
    @MethodSource("trailingNewlines")
    fun `trailing newlines never become part of the value`(
        description: String,
        contents: String,
    ) {
        val path = secretFile("db_password", contents)
        assertEquals(
            "hunter2",
            config("DB_PASSWORD_FILE" to path).required("DB_PASSWORD"),
            "$description must not reach the database as part of the credential",
        )
    }

    @Test
    fun `whitespace inside a secret survives, since only newlines are the file format's own`() {
        val path = secretFile("db_password", "hunter 2 \n")
        assertEquals("hunter 2 ", config("DB_PASSWORD_FILE" to path).required("DB_PASSWORD"))
    }

    @Test
    fun `both forms set is refused rather than resolved by precedence`() {
        val path = secretFile("db_password", "from-file")
        val error =
            assertThrows(ConfigException::class.java) {
                config("DB_PASSWORD" to "direct", "DB_PASSWORD_FILE" to path).required("DB_PASSWORD")
            }
        assertTrue(
            error.message!!.contains("DB_PASSWORD") && error.message!!.contains("DB_PASSWORD_FILE"),
            "the message must name both forms so the operator knows what to unset: ${error.message}",
        )
    }

    @Test
    fun `a file variable pointing at nothing names the path it could not read`() {
        val missing = dir.resolve("absent").toAbsolutePath().toString()
        val error =
            assertThrows(ConfigException::class.java) {
                config("DB_PASSWORD_FILE" to missing).required("DB_PASSWORD")
            }
        assertTrue(
            error.message!!.contains(missing),
            "a wrong mount path is only diagnosable if the message repeats it: ${error.message}",
        )
    }

    @Test
    fun `a required secret that is set nowhere names both forms it accepts`() {
        val error = assertThrows(ConfigException::class.java) { config().required("DB_PASSWORD") }
        assertTrue(
            error.message!!.contains("DB_PASSWORD") && error.message!!.contains("DB_PASSWORD_FILE"),
            "the operator must learn the file form exists from the failure itself: ${error.message}",
        )
    }

    @Test
    fun `an empty variable counts as unset, since compose substitutes one for a missing value`() {
        assertNull(config("DB_PASSWORD" to "").optional("DB_PASSWORD"))
    }

    @Test
    fun `a default stands in for an absent setting`() {
        assertEquals("localhost", config().string("DB_HOST", "localhost"))
        assertEquals(5432, config().int("DB_PORT", 5432))
    }

    @Test
    fun `a numeric setting that is not a number is refused at boot rather than coerced`() {
        val error =
            assertThrows(ConfigException::class.java) { config("DB_PORT" to "five").int("DB_PORT", 5432) }
        assertTrue(
            error.message!!.contains("DB_PORT") && error.message!!.contains("five"),
            "the message must show the offending value: ${error.message}",
        )
    }

    companion object {
        @JvmStatic
        fun trailingNewlines() =
            listOf(
                Arguments.of("a trailing newline", "hunter2\n"),
                Arguments.of("a trailing CRLF", "hunter2\r\n"),
                Arguments.of("repeated trailing newlines", "hunter2\n\n"),
            )
    }
}
