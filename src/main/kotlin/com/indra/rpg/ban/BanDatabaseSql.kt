package com.indra.rpg.ban

import com.indra.rpg.ban.BanMessages.getTimeFormat
import com.indra.rpg.common.db.IndraDb
import com.indra.rpg.common.util.TimeUtil.formatToLocalDateTime
import taboolib.module.database.ActionUpdate
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnOptionSQLite
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.Order
import taboolib.module.database.Table

/**
 * 封禁数据实现（表 indra_ban / indra_ban_warn / indra_ban_history）。
 *
 * ── 存储后端 ─────────────────────────────────────────────
 *   由 datasource.yml 的 `database.enable` 决定（见 [IndraDb]）：
 *     false → 本地 SQLite 文件；true → 外部 MySQL。
 *
 * ── 方言差异只出现在建表处 ────────────────────────────────
 *   SQL / SQLite 是两套列类型与列选项枚举，且 MySQL 的 TEXT 列
 *   不能直接做主键，因此主键列按方言分别声明；
 *   查询与写入共用一份代码（`Table<*, *>` 星投影），
 *   全部外部输入经 DSL 参数绑定，不拼字符串。
 *
 * ── 整行替换语义 ─────────────────────────────────────────
 *   原 REPLACE INTO 等价实现为「更新，0 行则插入」；
 *   并发下主键冲突说明对方已插入，随后的兜底更新等价 REPLACE。
 *
 * 移植自 Phoenix 的 ban/BanDatabaseSql.kt（v1.16.0），
 * 数据源改为 IndraDb、表名前缀改为 indra_。
 */
class BanDatabaseSql : BanDatabase {

    private val ds = IndraDb.dataSource

    private val banTable: Table<*, *> = when (IndraDb.type()) {
        IndraDb.Dialect.MYSQL -> mysqlBanTable()
        IndraDb.Dialect.SQLITE -> sqliteBanTable()
    }
    private val warnTable: Table<*, *> = when (IndraDb.type()) {
        IndraDb.Dialect.MYSQL -> mysqlWarnTable()
        IndraDb.Dialect.SQLITE -> sqliteWarnTable()
    }
    private val historyTable: Table<*, *> = when (IndraDb.type()) {
        IndraDb.Dialect.MYSQL -> mysqlHistoryTable()
        IndraDb.Dialect.SQLITE -> sqliteHistoryTable()
    }

    init {
        banTable.createTable(ds)
        warnTable.createTable(ds)
        historyTable.createTable(ds)
    }

    // ══ 建表（方言分支）══════════════════════════════

    private fun mysqlBanTable() = Table("indra_ban", IndraDb.mysqlHost()) {
        add("playerID") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.PRIMARY_KEY) } }
        add("isBanned") { type(ColumnTypeSQL.BOOL) { options(ColumnOptionSQL.NOTNULL); def(false) } }
        add("banReason") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("banDuration") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("banTime") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("unbanTime") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("banningAdmin") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("isWhitelisted") { type(ColumnTypeSQL.BOOL) { options(ColumnOptionSQL.NOTNULL); def(false) } }
        add("whitelistTime") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("whitelistingAdmin") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
    }

    private fun sqliteBanTable() = Table("indra_ban", IndraDb.sqliteHost()) {
        add("playerID") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.PRIMARY_KEY) } }
        add("isBanned") { type(ColumnTypeSQLite.INTEGER) { options(ColumnOptionSQLite.NOTNULL); def(0) } }
        add("banReason") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("banDuration") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("banTime") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("unbanTime") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("banningAdmin") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("isWhitelisted") { type(ColumnTypeSQLite.INTEGER) { options(ColumnOptionSQLite.NOTNULL); def(0) } }
        add("whitelistTime") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("whitelistingAdmin") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
    }

    private fun mysqlWarnTable() = Table("indra_ban_warn", IndraDb.mysqlHost()) {
        add("id") { type(ColumnTypeSQL.INT) { options(ColumnOptionSQL.PRIMARY_KEY, ColumnOptionSQL.AUTO_INCREMENT) } }
        add("playerID") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.NOTNULL) } }
        add("reason") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("operator") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("time") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
    }

    private fun sqliteWarnTable() = Table("indra_ban_warn", IndraDb.sqliteHost()) {
        add("id") {
            type(ColumnTypeSQLite.INTEGER) {
                options(ColumnOptionSQLite.PRIMARY_KEY, ColumnOptionSQLite.AUTOINCREMENT)
            }
        }
        add("playerID") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("reason") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("operator") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("time") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
    }

    private fun mysqlHistoryTable() = Table("indra_ban_history", IndraDb.mysqlHost()) {
        add("id") { type(ColumnTypeSQL.INT) { options(ColumnOptionSQL.PRIMARY_KEY, ColumnOptionSQL.AUTO_INCREMENT) } }
        add("playerID") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.NOTNULL) } }
        add("action") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("reason") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("duration") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("operator") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
        add("time") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
    }

    private fun sqliteHistoryTable() = Table("indra_ban_history", IndraDb.sqliteHost()) {
        add("id") {
            type(ColumnTypeSQLite.INTEGER) {
                options(ColumnOptionSQLite.PRIMARY_KEY, ColumnOptionSQLite.AUTOINCREMENT)
            }
        }
        add("playerID") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("action") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("reason") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("duration") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("operator") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
        add("time") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL) } }
    }

    // ══ 查询 / 写入 ══════════════════════════════════

    override fun getPlayerByName(name: String): BanPlayer {
        return banTable.select(ds) {
            where { "playerID" eq name }
        }.map {
            BanPlayer(
                isBanned = getInt("isBanned") != 0,
                banReason = getString("banReason") ?: "",
                banDuration = getString("banDuration") ?: "",
                banTime = getString("banTime") ?: "",
                unbanTime = getString("unbanTime") ?: "",
                banningAdmin = getString("banningAdmin") ?: "",
                isWhitelisted = getInt("isWhitelisted") != 0,
                whitelistTime = getString("whitelistTime") ?: "",
                whitelistingAdmin = getString("whitelistingAdmin") ?: ""
            )
        }.firstOrNull() ?: BanPlayer()
    }

    override fun getBannedPlayers(): List<BannedPlayer> {
        return banTable.select(ds) {
            where { "isBanned" eq 1 }
            rows("playerID")
        }.map {
            BannedPlayer(
                playerID = getString("playerID") ?: "",
                isBanned = getInt("isBanned") != 0,
                banReason = getString("banReason") ?: "",
                banDuration = getString("banDuration") ?: "",
                banTime = getString("banTime") ?: "",
                unbanTime = getString("unbanTime") ?: "",
                banningAdmin = getString("banningAdmin") ?: "",
                isWhitelisted = getInt("isWhitelisted") != 0,
                whitelistTime = getString("whitelistTime") ?: "",
                whitelistingAdmin = getString("whitelistingAdmin") ?: ""
            )
        }
    }

    override fun getExpiredBans(nowTimestamp: Long): List<BannedPlayer> {
        // unbanTime 格式为 yyyy-MM-dd HH:mm:ss；自动解封只处理有时效的封禁（unbanTime 非空）。
        // SQL 侧不解析日期（跨方言差异大）：读出全部已封禁玩家，在内存中按时间戳过滤。
        // 该查询只在启动 / 重载 / 定时周期执行，玩家量级通常不大。
        return getBannedPlayers().filter { it.unbanTime.isNotBlank() }
            .filter { ban ->
                val local = ban.unbanTime.formatToLocalDateTime(BanMessages.getTimeFormat()) ?: return@filter false
                local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() < nowTimestamp
            }
    }

    override fun updatePlayer(name: String, value: BanPlayer) {
        if (banTable.update(ds) { playerSets(name, value) } > 0) return
        // 新玩家：插入整行（并发下主键冲突说明对方已插入，随后的兜底更新等价 REPLACE 语义）
        runCatching {
            banTable.insert(
                ds, "playerID", "isBanned", "banReason", "banDuration", "banTime",
                "unbanTime", "banningAdmin", "isWhitelisted", "whitelistTime", "whitelistingAdmin"
            ) {
                value(
                    name,
                    value.isBanned,
                    value.banReason,
                    value.banDuration,
                    value.banTime,
                    value.unbanTime,
                    value.banningAdmin,
                    value.isWhitelisted,
                    value.whitelistTime,
                    value.whitelistingAdmin
                )
            }
        }
        banTable.update(ds) { playerSets(name, value) }
    }

    private fun ActionUpdate.playerSets(name: String, value: BanPlayer) {
        set("isBanned", value.isBanned)
        set("banReason", value.banReason)
        set("banDuration", value.banDuration)
        set("banTime", value.banTime)
        set("unbanTime", value.unbanTime)
        set("banningAdmin", value.banningAdmin)
        set("isWhitelisted", value.isWhitelisted)
        set("whitelistTime", value.whitelistTime)
        set("whitelistingAdmin", value.whitelistingAdmin)
        where { "playerID" eq name }
    }

    override fun addWarning(playerID: String, reason: String, operator: String, time: String): Long {
        warnTable.insert(ds, "playerID", "reason", "operator", "time") {
            value(playerID, reason, operator, time)
        }
        // DSL 的 insert 只返回受影响行数；插入后回读该玩家最新一条警告的 id
        return warnTable.select(ds) {
            rows("MAX(id)")
            where { "playerID" eq playerID }
        }.map { getLong(1) }.firstOrNull() ?: 0L
    }

    override fun getWarnings(playerID: String): List<WarningRecord> {
        return warnTable.select(ds) {
            where { "playerID" eq playerID }
            orderBy("id", Order.Type.ASC)
        }.map {
            WarningRecord(
                id = getLong("id"),
                playerID = getString("playerID") ?: "",
                reason = getString("reason") ?: "",
                operator = getString("operator") ?: "",
                time = getString("time") ?: ""
            )
        }
    }

    override fun getWarningCount(playerID: String): Int {
        return warnTable.select(ds) {
            rows("COUNT(*)")
            where { "playerID" eq playerID }
        }.map { getInt(1) }.firstOrNull() ?: 0
    }

    override fun removeWarningById(warningId: Long): Boolean {
        return warnTable.delete(ds) {
            where { "id" eq warningId }
        } > 0
    }

    override fun clearWarnings(playerID: String): Int {
        return warnTable.delete(ds) {
            where { "playerID" eq playerID }
        }
    }

    override fun addHistory(
        playerID: String,
        action: String,
        reason: String,
        duration: String,
        operator: String,
        time: String
    ) {
        historyTable.insert(ds, "playerID", "action", "reason", "duration", "operator", "time") {
            value(playerID, action, reason, duration, operator, time)
        }
    }

    override fun getHistory(playerID: String, limit: Int): List<HistoryRecord> {
        return historyTable.select(ds) {
            where { "playerID" eq playerID }
            orderBy("id", Order.Type.DESC)
            limit(limit)
        }.map {
            HistoryRecord(
                id = getLong("id"),
                playerID = getString("playerID") ?: "",
                action = getString("action") ?: "",
                reason = getString("reason") ?: "",
                duration = getString("duration") ?: "",
                operator = getString("operator") ?: "",
                time = getString("time") ?: ""
            )
        }
    }

    /** 直连模式下无需显式刷盘（每次写操作即时落库） */
    override fun save() {}

    /**
     * 删除主记录整行。
     *
     * ⚠️ `delete` 返回的是**受影响行数**，不是布尔：
     *    返回 0 既可能是「本来就没这行」，也可能是 WHERE 条件写错 —— 两种都应当
     *    如实回报 false，让上层（面板 / 指令）给出准确提示，而不是假装成功。
     *
     * ⚠️ 与 [updatePlayer] 不同，这里**没有**「0 行则插入」的兜底：
     *    删除语义下插入是反向灾难（会把刚删掉的行又建回来）。
     */
    override fun deleteBanRecord(playerID: String): Boolean {
        return banTable.delete(ds) {
            where { "playerID" eq playerID }
        } > 0
    }
}
