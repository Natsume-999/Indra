package com.indra.rpg.ban

/**
 * 封禁数据源。
 *
 * 与 Phoenix 不同：Phoenix 固定 SQLite，这里走 Indra 的 [com.indra.rpg.common.db.IndraDb]，
 * 由 datasource.yml 的 `database.enable` 决定 SQLite / MySQL。
 */
object BanDatabaseManager {

    private var database: BanDatabase? = null

    fun getDatabase(): BanDatabase = database ?: BanDatabaseSql().also { database = it }

    /** 关服 / 重载时释放，下次访问会按当前配置重新建连 */
    fun reset() {
        database = null
    }
}
