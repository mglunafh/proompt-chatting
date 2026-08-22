package dev.burufi.chatting.durable.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingTest {
    @Test
    fun `each setting is spelled consistently across the two namespaces`() {
        Setting.entries.forEach { setting ->
            assertEquals(
                setting.env,
                setting.property
                    .uppercase()
                    .replace('.', '_')
                    .replace('-', '_'),
                "the two spellings of ${setting.name} have drifted apart",
            )
        }
    }

    @Test
    fun `the file spelling is derived, never declared, so it cannot disagree with its base`() {
        Setting.entries.forEach { setting ->
            assertEquals("${setting.env}_FILE", setting.envFile)
            assertEquals("${setting.property}.file", setting.propertyFile)
        }
    }

    @Test
    fun `an int setting's default is itself an int, so the type check cannot fail on a default`() {
        Setting.entries
            .filter { it.type == Setting.Type.INT }
            .forEach { setting ->
                assertTrue(
                    setting.default?.toIntOrNull() != null,
                    "${setting.name} is declared INT but defaults to '${setting.default}'",
                )
            }
    }
}
