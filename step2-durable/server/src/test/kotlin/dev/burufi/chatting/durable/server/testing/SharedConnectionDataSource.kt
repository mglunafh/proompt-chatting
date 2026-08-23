package dev.burufi.chatting.durable.server.testing

import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Hands every caller the one connection the test owns, so an application's transaction and
 * the test's own writes land in the same database transaction and a single rollback undoes
 * both.
 *
 * Tests writing concurrently should take [TestDatabase.pool] instead.
 */
class SharedConnectionDataSource(
    connection: Connection,
) : DataSource {
    private val pinned: Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                // The three ways a caller would end the test's transaction early. The last is
                // the quiet one: setAutoCommit(true) commits, and Exposed calls it while
                // putting the connection back the way it found it.
                "close", "commit", "setAutoCommit" -> null
                "isClosed" -> false
                else ->
                    try {
                        method.invoke(connection, *(args ?: emptyArray()))
                    } catch (e: InvocationTargetException) {
                        // Unwrapped, or an assertion on a SQLState would never see its SQLException.
                        throw e.cause ?: e
                    }
            }
        } as Connection

    override fun getConnection(): Connection = pinned

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = pinned

    override fun getLogWriter(): PrintWriter? = null

    override fun setLogWriter(out: PrintWriter?) = Unit

    override fun setLoginTimeout(seconds: Int) = Unit

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = Logger.getLogger(javaClass.name)

    override fun <T : Any?> unwrap(iface: Class<T>): T = throw UnsupportedOperationException()

    override fun isWrapperFor(iface: Class<*>): Boolean = false
}
