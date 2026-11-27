package com.example.database

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

/** Small helpers so repositories can stay readable while using plain JDBC. */
object Db {

    fun <T> withConnection(block: (Connection) -> T): T =
        DatabaseFactory.dataSource.connection.use(block)

    fun <T> transaction(block: (Connection) -> T): T =
        DatabaseFactory.dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
}

fun PreparedStatement.bind(params: Array<out Any?>): PreparedStatement {
    params.forEachIndexed { index, value ->
        val position = index + 1
        when (value) {
            null -> setNull(position, Types.NULL)
            is java.sql.Array -> setArray(position, value)
            else -> setObject(position, value)
        }
    }
    return this
}

fun <T> Connection.queryList(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(params).executeQuery().use { rs ->
            buildList { while (rs.next()) add(mapper(rs)) }
        }
    }

fun <T> Connection.querySingle(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): T? =
    queryList(sql, *params, mapper = mapper).firstOrNull()

fun Connection.execute(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { it.bind(params).executeUpdate() }

fun Connection.exists(sql: String, vararg params: Any?): Boolean =
    querySingle(sql, *params) { true } ?: false

fun Connection.uuidArray(ids: Collection<UUID>): java.sql.Array =
    createArrayOf("uuid", ids.toTypedArray())

fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)
fun ResultSet.uuidOrNull(column: String): UUID? = getObject(column, UUID::class.java)
fun ResultSet.instant(column: String): String = getTimestamp(column).toInstant().toString()
fun ResultSet.instantOrNull(column: String): String? = getTimestamp(column)?.toInstant()?.toString()
