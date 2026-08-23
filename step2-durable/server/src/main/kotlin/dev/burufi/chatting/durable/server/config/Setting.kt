package dev.burufi.chatting.durable.server.config

/**
 * Every setting the server takes, declared once.
 */
enum class Setting(
    val env: String,
    val property: String,
    val type: Type,
    val default: String? = null,
    val secret: Boolean = false,
) {
    SERVER_PORT("SERVER_PORT", "server.port", Type.INT, "8080"),
    DB_HOST("DB_HOST", "db.host", Type.STRING, "localhost"),
    DB_PORT("DB_PORT", "db.port", Type.INT, "5432"),
    DB_NAME("DB_NAME", "db.name", Type.STRING, "chatting"),
    DB_USER("DB_USER", "db.user", Type.STRING, "chatting"),
    DB_PASSWORD("DB_PASSWORD", "db.password", Type.STRING, secret = true),
    DB_POOL_MAX_SIZE("DB_POOL_MAX_SIZE", "db.pool.max-size", Type.INT, "10"),
    DB_CONNECTION_TIMEOUT_MS("DB_CONNECTION_TIMEOUT_MS", "db.connection-timeout-ms", Type.INT, "5000"),
    ;

    /** The spelling that names a file holding the value rather than the value itself. */
    val envFile get() = "${env}_FILE"
    val propertyFile get() = "$property.file"

    val required get() = default == null

    enum class Type {
        STRING,
        INT,
    }
}
