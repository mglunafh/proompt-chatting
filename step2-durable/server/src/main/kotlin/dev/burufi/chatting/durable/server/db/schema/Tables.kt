package dev.burufi.chatting.durable.server.db.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = text("username")
    val passwordHash = text("password_hash")
    val isAdmin = bool("is_admin")
    val disabled = bool("disabled")
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val tokenHash = binary("token_hash")
    val userId = long("user_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val lastUsedAt = timestampWithTimeZone("last_used_at").defaultExpression(CurrentTimestampWithTimeZone)
    val expiresAt = timestampWithTimeZone("expires_at")

    override val primaryKey = PrimaryKey(id)
}

object Invites : Table("invites") {
    val id = long("id").autoIncrement()
    val tokenHash = binary("token_hash")
    val issuedBy = long("issued_by").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Conversations : Table("conversations") {
    val id = long("id").autoIncrement()
    val kind = text("kind")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val directLo = long("direct_lo").references(Users.id).nullable()
    val directHi = long("direct_hi").references(Users.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ConversationMembers : Table("conversation_members") {
    val conversationId = long("conversation_id").references(Conversations.id)
    val userId = long("user_id").references(Users.id)
    val joinedAt = timestampWithTimeZone("joined_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(conversationId, userId)
}

object Messages : Table("messages") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id").references(Conversations.id)
    val senderId = long("sender_id").references(Users.id)
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val clientMsgId = text("client_msg_id")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Every table the migration creates, in dependency order. `SchemaTest` compares this against
 * the database in both directions, so one left out of it fails rather than going unchecked.
 */
val schemaTables =
    arrayOf(
        Users,
        Sessions,
        Invites,
        Conversations,
        ConversationMembers,
        Messages,
    )
