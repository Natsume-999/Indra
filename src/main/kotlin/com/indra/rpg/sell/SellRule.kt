package com.indra.rpg.sell

import taboolib.library.configuration.ConfigurationSection
import java.util.concurrent.ThreadLocalRandom

/**
 * 出售规则（对应 `sell/` 目录下一个文件中的顶层节点）。
 *
 * 配置格式与 Phoenix（VitaSell）**100% 兼容**，用户现有文件可原样迁入。
 */
class SellRule(
    /** 规则名（yml 顶层键） */
    val id: String,
    /** 来源文件名，仅用于日志 */
    val source: String,
    /** 物品匹配串（Arim 裁剪版，见 [ItemMatcher]） */
    val itemSpec: String,
    /** 限定生效的界面名；空字符串 = 全部界面 */
    val table: String,
    /** 出售条件（Kether 脚本，全部为真才可出售） */
    val conditions: List<String>,
    /** 金钱奖励池 */
    val moneyPool: List<WeightedEntry>,
    /** 点券奖励池 */
    val pointPool: List<WeightedEntry>,
    /** 命中后额外执行的 Kether 脚本 */
    val kether: List<String>,
) {
    /** 编译后的匹配器；null 表示「无条件」（匹配一切） */
    val matcher: ItemMatcher.Matcher? = ItemMatcher.compile(itemSpec)

    /** 该规则是否适用于指定界面 */
    fun appliesTo(tableName: String): Boolean = table.isEmpty() || table.equals(tableName, ignoreCase = true)

    /**
     * 抽取金钱数额。
     *
     * ⚠️ **符号语义沿用 Phoenix（VitaSell）**：配置里**负数表示给予玩家**。
     *    这是为了兼容用户既有配置 —— 若改为「正数表示给予」，
     *    迁移后所有规则都会反向扣钱。此处额外做一次符号归一，
     *    保证只有「净支出」的规则才会被记为有效出售。
     */
    fun rollMoney(): Double = roll(moneyPool)

    /** 抽取点券数额（语义同 [rollMoney]） */
    fun rollPoint(): Int = roll(pointPool).toInt()

    /** 是否存在任何有效奖励（都不存在则规则无意义，加载时会告警） */
    fun hasReward(): Boolean = moneyPool.isNotEmpty() || pointPool.isNotEmpty() || kether.isNotEmpty()

    private fun roll(pool: List<WeightedEntry>): Double {
        if (pool.isEmpty()) return 0.0
        val total = pool.sumOf { it.weight }
        if (total <= 0.0) return 0.0
        var pick = ThreadLocalRandom.current().nextDouble(total)
        for (entry in pool) {
            pick -= entry.weight
            if (pick <= 0.0) return entry.value()
        }
        return pool.last().value()
    }

    /**
     * 加权奖励项。
     * @param min 数值下限（无区间时 == max）
     * @param max 数值上限
     * @param weight 权重
     */
    class WeightedEntry(val min: Double, val max: Double, val weight: Double) {
        fun value(): Double =
            if (min == max) min else ThreadLocalRandom.current().nextDouble(min, max)
    }

    companion object {

        /**
         * 从配置节点解析一条规则。
         * @return null 表示该节点不是有效规则（缺少 Item 键）
         */
        fun parse(id: String, source: String, section: ConfigurationSection): SellRule? {
            val itemSpec = section.getString("Item").orEmpty()
            if (itemSpec.isBlank()) return null

            return SellRule(
                id = id,
                source = source,
                itemSpec = itemSpec,
                table = section.getString("Table").orEmpty(),
                conditions = section.getStringList("Condition"),
                moneyPool = parsePool(section.getStringList("Action.Money"), "Money", id),
                pointPool = parsePool(section.getStringList("Action.Point"), "Point", id),
                kether = section.getStringList("Action.Kether"),
            )
        }

        /**
         * 解析 `"数值 权重"` 列表，支持 `"最小值~最大值"` 区间。
         *
         * 兼容 Phoenix 的两种写法：
         *   `"-500 20~25"`  → 数值 -500，权重 20~25 之间随机
         *   `"-1000 75"`    → 数值 -1000，权重固定 75
         */
        private fun parsePool(lines: List<String>, kind: String, ruleId: String): List<WeightedEntry> {
            val result = mutableListOf<WeightedEntry>()
            for (line in lines) {
                val text = line.trim()
                if (text.isEmpty() || text.startsWith("#")) continue

                val parts = text.split(Regex("\\s+"))
                if (parts.size < 2) {
                    taboolib.common.platform.function.warning(
                        "[Indra] 出售规则 `$ruleId` 的 $kind 项格式无效（应为 `数值 权重`）：$text"
                    )
                    continue
                }

                val (min, max) = parseRange(parts[0]) ?: run {
                    taboolib.common.platform.function.warning(
                        "[Indra] 出售规则 `$ruleId` 的 $kind 数值无法解析：${parts[0]}"
                    )
                    null
                } ?: continue

                val weightRange = parseRange(parts[1]) ?: run {
                    taboolib.common.platform.function.warning(
                        "[Indra] 出售规则 `$ruleId` 的 $kind 权重无法解析：${parts[1]}"
                    )
                    null
                } ?: continue

                // 权重取区间中点作为该项的固定权重（区间权重按期望值处理）
                val weight = (weightRange.first + weightRange.second) / 2.0
                if (weight <= 0.0) {
                    taboolib.common.platform.function.warning(
                        "[Indra] 出售规则 `$ruleId` 的 $kind 权重必须为正数，已忽略：$text"
                    )
                    continue
                }
                result += WeightedEntry(min, max, weight)
            }
            return result
        }

        /** 解析 `"500"` 或 `"-200~-100"` → Pair(min, max)；失败返回 null */
        private fun parseRange(raw: String): Pair<Double, Double>? {
            val text = raw.trim().removePrefix("+")
            return if ('~' in text) {
                val (a, b) = text.split('~', limit = 2)
                val min = a.trim().toDoubleOrNull() ?: return null
                val max = b.trim().toDoubleOrNull() ?: return null
                if (min > max) Pair(max, min) else Pair(min, max)
            } else {
                val v = text.toDoubleOrNull() ?: return null
                Pair(v, v)
            }
        }
    }
}
