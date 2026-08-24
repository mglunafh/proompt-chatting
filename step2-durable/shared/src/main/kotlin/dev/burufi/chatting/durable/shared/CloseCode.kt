package dev.burufi.chatting.durable.shared

/**
 * The application close codes, which stop a client's reconnect loop. Passed in a WebSocket frame.
 */
enum class CloseCode(
    val code: Int,
) {
    /** The session is gone or expired. The client discards its token and asks for credentials. */
    SESSION_INVALID(4401),

    /** The account is disabled. The client discards its token and stops, since a password will not help. */
    ACCOUNT_DISABLED(4403),

    /**
     * Another socket took over this session. The token is still valid, so the client keeps it and
     * offers a manual reconnect; retrying would evict the socket that just evicted it.
     */
    SESSION_DISPLACED(4409),
    ;

    companion object {
        private val byCode = entries.associateBy(CloseCode::code)

        fun of(code: Int): CloseCode? = byCode[code]
    }
}
