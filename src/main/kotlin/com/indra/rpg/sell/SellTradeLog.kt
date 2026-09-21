package com.indra.rpg.sell

import com.indra.rpg.common.db.IndraDb
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnOptionSQLite
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.Order
import taboolib.module.database.Table
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 出售交易审计。
 *
 * ── 与 Phoenix 的差异（重要）─────────────────────────────────
 *   Phoenix 把成交记录**写文本文件**（`data/sell/trades.log`）。
 *   但 Indra 的项目硬性约束是「任何存储都必须走本地数据库和 MySQL 双存储」，
 *   因此这里改为**落库**（表 `indra_sell_log`），复用 [IndraDb] 的方言切换。
 *
 * ── 建表方式 ─────────────────────────────────────────────────
 *   与 [com.indra.rpg.ban.BanDatabaseSql] 一致：SQL / SQLite 用各自的
 *   列类型与列选项枚举，在建表处分叉；查询与写入共用一份代码
 *   （`Table<*, *>` 星投影）。所有外部输入经 DSL 参数绑定，不拼字符串。
 *
 * ── 失败策略 ─────────────────────────────────────────────────
 *   写库是 IO、是旁路能力。任何异常只告警，**绝不影响玩家交易**
 *   （交易此刻已在内存中完成）。
 */
object SellTradeLog {

    private const val TABLE = "indra_sell_log"
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Volatile
    private var ready = false

    private val table: Table<*, *> by lazy {
        when (IndraDb.type()) {
            IndraDb.Dialect.MYSQL -> mysqlTable()
            IndraDb.Dialect.SQLITE -> sqliteTable()
        }
    }

    private fun mysqlTable() = Table(TABLE, IndraDb.mysqlHost()) {
        add("id") { type(ColumnTypeSQL.INT) { options(ColumnOptionSQL.AUTO_INCREMENT, ColumnOptionSQL.PRIMARY_KEY) } }
        add("playerName") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.NOTNULL) } }
        add("playerUUID") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.NOTNULL) } }
        add("tableName") { type(ColumnTypeSQL.VARCHAR, 64) { options(ColumnOptionSQL.NOTNULL) } }
        add("amount") { type(ColumnTypeSQL.INT) { options(ColumnOptionSQL.NOTNULL); def(0) } }
        add("money") { type(ColumnTypeSQL.DOUBLE) { options(ColumnOptionSQL.NOTNULL); def(0) } }
        add("point") { type(ColumnTypeSQL.INT) { options(ColumnOptionSQL.NOTNULL); def(0) } }
        add("tradeTime") { type(ColumnTypeSQL.TEXT) { options(ColumnOptionSQL.NOTNULL) } }
    }

    private fun sqliteTable() = Table(TABLE, IndraDb.sqliteHost()) {
        add("id") { type(ColumnTypeSQLite.INTEGER) { options(ColumnOptionSQLite.PRIMARY_KEY, ColumnOptionSQLite.AUTOINCREMENT) } }
        add("playerName") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("playerUUID") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("tableName") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
        add("amount") { type(ColumnTypeSQLite.INTEGER) { options(ColumnOptionSQLite.NOTNULL); def(0) } }
        add("money") { type(ColumnTypeSQLite.REAL) { options(ColumnOptionSQLite.NOTNULL); def(0) } }
        add("point") { type(ColumnTypeSQLite.INTEGER) { options(ColumnOptionSQLite.NOTNULL); def(0) } }
        add("tradeTime") { type(ColumnTypeSQLite.TEXT) { options(ColumnOptionSQLite.NOTNULL); def("") } }
    }

    /** 建表（幂等） */
    fun ensureTable() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            runCatching {
                table.createTable(IndraDb.dataSource)
                ready = true
            }.onFailure {
                warning("[Indra] 出售审计表建表失败（交易不受影响）：${it.message}")
            }
        }
    }

    /** 记录一次成交；任何异常都只告警，不打断交易 */
    fun record(player: Player, tableId: String, result: SellEngine.Result) {
        if (result.amount <= 0) return
        runCatching {
            ensureTable()
            table.insert(IndraDb.dataSource, "playerName", "playerUUID", "tableName", "amount", "money", "point", "tradeTime") {
                value(
                    player.name,
                    player.uniqueId.toString(),
                    tableId,
                    result.amount,
                    result.money,
                    result.point,
                    LocalDateTime.now().format(timeFormat),
                )
            }
        }.onFailure {
            warning("[Indra] 写出售审计失败（交易已完成，不受影响）：${it.message}")
        }
    }

    /**
     * 查询某玩家最近的成交记录（供 `/indra sell log`）。
     * @return 已渲染颜色码的文本行（最新在前）
     */
    fun recent(playerName: String, limit: Int = 10): List<String> = runCatching {
        ensureTable()
        // ⚠️ `order(...)` 在 TabooLib 6.3 已弃用，改用 `orderBy(column, Order.Type)`。
        //    这里用 DESC 取最新，配合 limit 实现「最近 N 条」。
        val rows = table.select(IndraDb.dataSource) {
            where { "playerName" eq playerName }
            orderBy("id", Order.Type.DESC)
            limit(limit.coerceIn(1, 100))
        }
        rows.map {
            "§7${getString("tradeTime")} §8| §f${getString("tableName")} " +
                "§8| §7x${getInt("amount")} §8| §e${getDouble("money").toInt()}"
        }
    }.getOrElse {
        warning("[Indra] 查询出售审计失败：${it.message}")
        emptyList()
    }

    /**
     * 成交总览（供 `/indra status`）：返回 (笔数, 总金额)。
     *
     * 用 TabooLib 的 `sum()` DSL 而非手写 `rows("SUM(...)")`：
     * 后者在 SQLite / MySQL 下对聚合列的**取值方式**不一致
     * （别名 vs 下标），`sum()` 由框架统一处理。
     */
    fun summary(): Pair<Long, Double> = runCatching {
        ensureTable()
        val count = table.select(IndraDb.dataSource) {
            rows("COUNT(*)")
        }.map { getLong(1) }.firstOrNull() ?: 0L

        var total = 0.0
        table.select(IndraDb.dataSource) {
            sum("money", "total") { }
        }.map { total = getDouble("total") }

        Pair(count, total)
    }.getOrElse {
        warning("[Indra] 统计出售数据失败：${it.message}")
        Pair(0L, 0.0)
    }
}
