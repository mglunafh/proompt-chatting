package dev.burufi.chatting.durable.shared.validation

/**
 * The strings commonly used as passwords which should be refused.
 */
object PasswordBlocklist {
    private const val RESOURCE = "/password-blocklist.txt"

    val entries: Set<String> by lazy { load() }

    fun contains(candidate: String): Boolean = candidate.lowercase() in entries

    private fun load(): Set<String> {
        val stream =
            PasswordBlocklist::class.java.getResourceAsStream(RESOURCE)
                ?: error("Password block list '$RESOURCE' is missing from the classpath")

        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
    }
}
