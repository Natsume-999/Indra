package com.indra.rpg.ui

import org.bukkit.entity.Player
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 面板配置绑定 —— 全部面板的外观都从 `menu.yml` 读。
 *
 * ── 设计原则 ────────────────────────────────────────────
 *   1. **图标三件套**统一走 `material → name → lore` 三段式，
 *      路径写法：`menus.<菜单ID>.icons.<图标ID>.material`
 *   2. **缺配置不崩**：任何一项缺失都退回代码内的默认值，
 *      服主把 menu.yml 写坏一半也只会看到默认外观，不会看到报错刷屏。
 *   3. **不缓存**：每次 `menu.yml` 被外部改动（autoReload = true）
 *      配置对象自动刷新，读取方法直接穿透到 Configuration，
 *      因此改完文件执行 `/indra reload` 立刻生效。
 *
 * ── 与 config.yml 的分工 ─────────────────────────────────
 *   config.yml   → 玩法数值（等级、经验、生成控制）
 *   menu.yml     → 纯外观（标题、材质、文案）
 *   把外观独立出来是为了让服主改皮时不必碰玩法数值文件。
 */
object MenuConfig {

    @Config("menu.yml", autoReload = true)
    lateinit var conf: Configuration

    // ══ 通用取值 ═══════════════════════════════════════════

    /**
     * 取字符串，支持 `{player}` 等占位符。
     *
     * ⚠️ 占位符替换放在这里而不是各面板里，是为了保证「同一套占位符规则」
     *    在所有面板一致 —— 否则玩家信息面板支持 {player}、排行榜不支持，
     *    服主会以为是 bug。
     */
    fun str(menu: String, path: String, def: String, player: Player? = null): String {
        val raw = conf.getString("menus.$menu.$path") ?: def
        return raw.replace("{player}", player?.name ?: "")
    }

    /** 取整数（行数、页码等） */
    fun int(menu: String, path: String, def: Int): Int =
        conf.getInt("menus.$menu.$path", def)

    /** 取布尔 */
    fun bool(menu: String, path: String, def: Boolean): Boolean =
        conf.getBoolean("menus.$menu.$path", def)

    /** 取字符串列表（描述文本） */
    fun list(menu: String, path: String, def: List<String>, player: Player? = null): List<String> {
        val raw = conf.getStringList("menus.$menu.$path").ifEmpty { def }
        return raw.map { it.replace("{player}", player?.name ?: "") }
    }

    /**
     * 取按钮区的槽位列表。
     *
     * 支持两种写法，取到哪种用哪种（服主不必记住内部约定）：
     *   `slots: [0..8]`          区间展开
     *   `slots: [0, 2, 4]`       逐个列举
     */
    fun slots(menu: String, path: String, def: List<Int>): List<Int> {
        val raw = conf.getStringList("menus.$menu.$path").ifEmpty { return def }
        return raw.flatMap { expandSlotToken(it) }.distinct().filter { it in 0..53 }
    }

    private fun expandSlotToken(token: String): List<Int> {
        val t = token.trim()
        val range = Regex("^(\\d+)\\s*\\.\\.\\s*(\\d+)$").find(t)
        if (range != null) {
            val a = range.groupValues[1].toInt()
            val b = range.groupValues[2].toInt()
            return if (a <= b) (a..b).toList() else (b..a).toList()
        }
        return t.toIntOrNull()?.let { listOf(it) } ?: emptyList()
    }

    /** 面板标题（自动套色码） */
    fun title(menu: String, def: String, player: Player? = null): String =
        str(menu, "title", def, player)

    /** 面板行数（1..6，超出会被裁到合法范围） */
    fun rows(menu: String, def: Int): Int = int(menu, "rows", def).coerceIn(1, 6)
}
