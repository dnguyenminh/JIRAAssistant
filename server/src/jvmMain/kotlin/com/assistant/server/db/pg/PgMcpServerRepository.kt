package com.assistant.server.db.pg

import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [McpServerRepository].
 * Maps disabled/internal boolean columns correctly.
 * Requirements: 6.1, 6.2
 */
class PgMcpServerRepository(
    private val dataSource: DataSource
) : McpServerRepository {

    override suspend fun getAll(): List<McpServerConfig> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgMcpServerSql.SELECT_ALL).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgMcpServerRepository] getAll failed: ${e.message}")
        emptyList()
    }

    override suspend fun findById(id: String): McpServerConfig? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgMcpServerSql.FIND_BY_ID).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgMcpServerRepository] findById failed: ${e.message}")
        null
    }

    override suspend fun findByName(name: String): McpServerConfig? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgMcpServerSql.FIND_BY_NAME).use { ps ->
                ps.setString(1, name)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgMcpServerRepository] findByName failed: ${e.message}")
        null
    }

    override suspend fun isInternal(id: String): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgMcpServerSql.IS_INTERNAL).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    rs.next() && rs.getBoolean("internal")
                }
            }
        }
    } catch (e: Exception) {
        println("[PgMcpServerRepository] isInternal failed: ${e.message}")
        false
    }

    override suspend fun insert(config: McpServerConfig) {
        try {
            val now = Instant.now().toString()
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgMcpServerSql.INSERT).use { ps ->
                    setInsertParams(ps, config, now)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgMcpServerRepository] insert failed: ${e.message}")
        }
    }

    override suspend fun update(config: McpServerConfig) {
        try {
            val now = Instant.now().toString()
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgMcpServerSql.UPDATE).use { ps ->
                    setUpdateParams(ps, config, now)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgMcpServerRepository] update failed: ${e.message}")
        }
    }

    override suspend fun updateStatus(id: String, status: String) {
        try {
            val now = Instant.now().toString()
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgMcpServerSql.UPDATE_STATUS).use { ps ->
                    ps.setString(1, status)
                    ps.setString(2, now)
                    ps.setString(3, id)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgMcpServerRepository] updateStatus failed: ${e.message}")
        }
    }

    override suspend fun delete(id: String) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgMcpServerSql.DELETE_BY_ID).use { ps ->
                    ps.setString(1, id)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgMcpServerRepository] delete failed: ${e.message}")
        }
    }

    override suspend fun deleteAll() {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgMcpServerSql.DELETE_ALL).use { ps ->
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgMcpServerRepository] deleteAll failed: ${e.message}")
        }
    }

    private fun mapRow(rs: ResultSet): McpServerConfig = McpServerConfig(
        id = rs.getString("id"),
        name = rs.getString("name"),
        type = rs.getString("type"),
        command = rs.getString("command"),
        url = rs.getString("url"),
        args = rs.getString("args"),
        env = rs.getString("env"),
        autoApprove = rs.getString("auto_approve"),
        disabled = rs.getBoolean("disabled"),
        status = rs.getString("status"),
        createdAt = rs.getString("created_at"),
        updatedAt = rs.getString("updated_at"),
        internal = rs.getBoolean("internal")
    )

    private fun setInsertParams(
        ps: java.sql.PreparedStatement, config: McpServerConfig, now: String
    ) {
        ps.setString(1, config.id)
        ps.setString(2, config.name)
        ps.setString(3, config.type)
        ps.setString(4, config.command)
        ps.setString(5, config.url)
        ps.setString(6, config.args)
        ps.setString(7, config.env)
        ps.setString(8, config.autoApprove)
        ps.setBoolean(9, config.disabled)
        ps.setString(10, config.status)
        ps.setString(11, now)
        ps.setString(12, now)
        ps.setBoolean(13, config.internal)
    }

    private fun setUpdateParams(
        ps: java.sql.PreparedStatement, config: McpServerConfig, now: String
    ) {
        ps.setString(1, config.name)
        ps.setString(2, config.type)
        ps.setString(3, config.command)
        ps.setString(4, config.url)
        ps.setString(5, config.args)
        ps.setString(6, config.env)
        ps.setString(7, config.autoApprove)
        ps.setBoolean(8, config.disabled)
        ps.setString(9, now)
        ps.setString(10, config.id)
    }
}
