package com.indra.rpg.common.db

import taboolib.expansion.db
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.database.HostSQL
import taboolib.module.database.HostSQLite
import java.io.File
import javax.sql.DataSource

/**
 * Indra 统一数据源（本地 SQLite / 外部 MySQL 双存储）。
 *
 * ── 为什么不用 Kotlin 对象直接持有配置 ─────────────────────────
 *   与 [com.indra.rpg.data.DataManager] 共用同一份 `datasource.yml`，
 *   通过 TabooLib 的 `db(name, node, file)` 解析：
 *     `database.enable = true`  → 返回 ConfigurationSection（MySQL）
 *     `database.enable = false` → 返回 File（SQLite）
 *   这样切换存储只改配置，代码零改动（项目硬性要求）。
 *
 * ── 与 DataManager 的分工 ────────────────────────────────────
 *   · DataManager（PTC Object）：玩家主数据，注解驱动、自动建表
 *   · IndraDb（Table DSL）：封禁等「多行记录 + 复杂条件查询」场景
 *   两者共用同一个 DataSource 与同一份配置，不冲突。
 *
 * ── 方言差异（建表时必须分叉）────────────────────────────────
 *   · 自增主键：SQLite 用 AUTOINCREMENT，MySQL 用 AUTO_INCREMENT
 *   · 文本列：MySQL 的 TEXT 不能做主键/带默认值，主键列用 VARCHAR
 *   · 布尔：SQLite 存 0/1（INTEGER），MySQL 有 BOOL
 *
 * 移植自 Phoenix 的 common/PhoenixDb.kt（v1.16.0），
 * 去掉了「本地冒烟测试专用」的 sqliteFileOverride / useDataSource 两个后门。
 */
object IndraDb {

    enum class Dialect { SQLITE, MYSQL }

    @Volatile
    private var backing: DataSource? = null

    /** `db()` 的结果：File（SQLite）或 ConfigurationSection（MySQL） */
    @Volatile
    private var source: Any? = null

    @Volatile
    private var dialect: Dialect? = null

    /** 当前数据源；首次访问时惰性建立（HikariCP 由 TabooLib 托管） */
    val dataSource: DataSource
        get() = backing ?: synchronized(this) {
            backing ?: createDataSource().also { backing = it }
        }

    /** 当前方言 */
    fun type(): Dialect = dialect ?: synchronized(this) {
        dialect ?: run {
            val s = resolveSource()
            val d = if (s is File) Dialect.SQLITE else Dialect.MYSQL
            dialect = d
            d
        }
    }

    fun isMySQL(): Boolean = type() == Dialect.MYSQL

    /** SQLite 主机（插件目录下 `<database.sqlite.file>`） */
    fun sqliteHost(): HostSQLite = HostSQLite(resolveSource() as File)

    /**
     * MySQL 主机。
     *
     * 显式读键而不复用 `HostSQL(ConfigurationSection)` 重载：
     * 后者内部取的键名与 datasource.yml 的字段布局不完全对应，
     * 显式构造能确保端口/库名/密码逐项落在我们自己的配置上。
     */
    fun mysqlHost(): HostSQL = (resolveSource() as ConfigurationSection).let { s ->
        HostSQL(
            s.getString("host") ?: "127.0.0.1",
            s.getString("port") ?: "3306",
            s.getString("user") ?: "root",
            s.getString("password") ?: "",
            s.getString("database") ?: "indra"
        )
    }

    /** 关闭连接池（关服时调用） */
    fun close() {
        synchronized(this) {
            runCatching { (backing as? AutoCloseable)?.close() }
            backing = null
            source = null
            dialect = null
        }
    }

    private fun createDataSource(): DataSource = when (type()) {
        Dialect.MYSQL -> mysqlHost().createDataSource()
        Dialect.SQLITE -> sqliteHost().createDataSource()
    }

    private fun resolveSource(): Any = source ?: synchronized(this) {
        source ?: db("datasource.yml", "database", "data.db").also { source = it }
    }
}
