package com.indra.rpg.data

import com.indra.rpg.config.IndraConfig
import taboolib.expansion.Alias
import taboolib.expansion.Id
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import taboolib.expansion.Length
import taboolib.expansion.ColumnType
import taboolib.module.database.ColumnTypeSQL

/**
 * 玩家主数据实体（PTC Object）。
 *
 * ── TabooLib 6.3 的 PTC Object 约定（已按真实 API 核对）────────────
 *   · 注解来自 `taboolib.expansion` 包，**不是** `taboolib.module.database`
 *   · 表结构由 PTC 从注解自动生成（Container.init() 内部会调
 *     `Table.createTable()`），**无需手写建表 DSL**
 *   · `@Id` = 自增主键；`@UniqueKey` = 唯一索引（用于 uuid 去重）
 *   · `@TableName` 可显式指定表名，否则用类名
 *   · `@ColumnType(sql = ...)` / `@Length(n)` 控制 MySQL 列类型与长度
 *
 * 列名策略：Java 字段用驼峰，PTC 会映射为列；这里统一用
 * `@Alias` 显式固定列名，避免不同版本映射规则变动带来的隐性坑。
 */
@TableName("indra_player")
class IndraPlayerData {

    @Id
    var id: Int = 0

    /** 玩家 UUID（唯一键，去重靠它） */
    @UniqueKey
    @Length(36)
    @Alias("uuid")
    var uuid: String = ""

    @Length(32)
    @Alias("name")
    var name: String = ""

    /** 当前等级 */
    @Alias("level")
    var level: Int = 1

    /** 当前等级内经验 */
    @Alias("exp")
    var exp: Int = 0

    /** 累计总经验 */
    @Alias("exp_total")
    var expTotal: Int = 0

    /** 未分配点数 */
    @Alias("points")
    var points: Int = 0

    /** 击杀数 */
    @Alias("kill_count")
    var killCount: Int = 0

    /** 死亡数 */
    @Alias("death_count")
    var deathCount: Int = 0

    /** 游戏币（对接 Vault 前的预留字段） */
    @Alias("coins")
    @ColumnType(sql = ColumnTypeSQL.BIGINT)
    var coins: Long = 0L

    @Alias("first_join")
    @ColumnType(sql = ColumnTypeSQL.BIGINT)
    var firstJoin: Long = 0L

    @Alias("last_seen")
    @ColumnType(sql = ColumnTypeSQL.BIGINT)
    var lastSeen: Long = 0L

    // ── 业务方法 ─────────────────────────────────────────

    /** 升级所需经验（线性公式，基数可在 config.yml 调整） */
    fun expToNextLevel(): Int = IndraConfig.expPerLevel * level

    /** 增加经验，返回是否升级 */
    fun addExp(amount: Int): Boolean {
        if (level >= IndraConfig.maxLevel) return false

        exp += amount
        expTotal += amount

        var leveled = false
        while (level < IndraConfig.maxLevel && exp >= expToNextLevel()) {
            exp -= expToNextLevel()
            level++
            points += IndraConfig.pointsPerLevel
            leveled = true
        }
        // 满级后经验不再溢出
        if (level >= IndraConfig.maxLevel) exp = exp.coerceAtMost(expToNextLevel())
        return leveled
    }

    /** 当前等级进度百分比 0.0 ~ 1.0 */
    fun progress(): Double {
        val need = expToNextLevel()
        return if (need <= 0) 1.0 else (exp.toDouble() / need).coerceIn(0.0, 1.0)
    }

    /** 消耗点数 */
    fun spendPoint(cost: Int = 1): Boolean {
        if (points < cost) return false
        points -= cost
        return true
    }
}
