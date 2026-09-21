package com.indra.rpg.sell

import com.indra.rpg.util.Msg
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.library.configuration.ConfigurationSection

/**
 * 出售界面模板（对应 `table/` 目录下一个文件中的顶层节点）。
 *
 * 配置格式与 Phoenix（VitaSell）**100% 兼容**。
 *
 * ── 布局语义（关键，与 VitaSell 一致）────────────────────────
 *   `Layout` 是每行 9 字符的网格，**字符与 `Icon` 的键对应**：
 *     · `Icon` 里**定义了**该字符  → 该槽位放这个图标（装饰或按钮）
 *     · `Icon` 里**没定义**该字符  → 该槽位是**玩家可放入物品的空位**（空格通常如此）
 *   因此 `'         '`（9 个空格）表示一整行都是可放物品的区域。
 *
 * ── Bind 类型 ────────────────────────────────────────────────
 *   Sell  点击出售当前界面内的物品
 *   Close 关闭界面
 *   Put   一键放入匹配 `Put-Match` 的物品（从背包搬进界面）
 */
class SellTable(
    /** 界面名（yml 顶层键） */
    val id: String,
    /** 来源文件名，仅用于日志 */
    val source: String,
    /** 界面标题（含颜色码） */
    val title: String,
    /** 6 行 × 9 列的布局网格，元素为字符 */
    val layout: List<List<Char>>,
    /** 自动出售：物品放入即结算 */
    val autoSell: Boolean,
    /** 字符 → 图标定义 */
    val icons: Map<Char, IconDef>,
) {
    /** 界面大小（必须是 9 的倍数，Bukkit 限制） */
    val size: Int = (layout.size * 9).coerceIn(9, 54)

    /** 该槽位是否可放入物品（即：该字符没有对应图标定义） */
    fun isPlayerSlot(slot: Int): Boolean {
        val ch = charAt(slot) ?: return false
        return ch !in icons
    }

    /** 取槽位上的字符；越界返回 null */
    fun charAt(slot: Int): Char? {
        if (slot < 0 || slot >= size) return null
        val row = slot / 9
        val col = slot % 9
        return layout.getOrNull(row)?.getOrNull(col)
    }

    /** 取槽位上的图标定义（无则 null） */
    fun iconAt(slot: Int): IconDef? = charAt(slot)?.let { icons[it] }

    /** 渲染某个图标的 ItemStack */
    fun renderIcon(def: IconDef): ItemStack = def.toItemStack()

    /** 该界面所有「按钮」槽位（绑定了 Bind 的） */
    fun buttonSlots(): List<Pair<Int, IconDef>> =
        (0 until size).mapNotNull { slot ->
            iconAt(slot)?.takeIf { it.bind != null }?.let { slot to it }
        }

    /**
     * 该界面所有「玩家物品槽」——出售时只统计这些位置的物品。
     * 这样可防止玩家用装饰图标占位规避，也避免误售界面按钮。
     */
    fun playerSlots(): List<Int> = (0 until size).filter { isPlayerSlot(it) }

    /** 图标定义 */
    class IconDef(
        val material: Material,
        val data: Int,
        val name: String,
        val lore: List<String>,
        val bind: Bind?,
        val putMatch: String?,
        /** 原始字符，便于日志 */
        val ch: Char,
    ) {
        val putMatcher: ItemMatcher.Matcher? = putMatch?.let { ItemMatcher.compile(it) }

        fun toItemStack(): ItemStack {
            val item = ItemStack(material)
            val meta = item.itemMeta ?: return item
            // Paper 26.3 起 displayName / lore 只接受 Adventure Component
            // （String 重载已废弃，见 README 第四节 API 变更表）
            meta.displayName(adv(Msg.color(name)))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { adv(Msg.color(it)) })
            }
            // Data（损伤值）在 1.13+ 材质扁平化后语义已变：仅对少量材质仍有意义。
            // VitaSell 用它区分染色玻璃的 16 色；这里保留读取，但不强行套用，
            // 因为 26.3 的 GRAY_STAINED_GLASS_PANE 等已经是独立材质。
            item.itemMeta = meta
            return item
        }

        /** String → Adventure Component（§ 颜色码由 legacy 序列化器解析） */
        private fun adv(text: String): net.kyori.adventure.text.Component =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(text)
    }

    enum class Bind { SELL, CLOSE, PUT }

    companion object {

        fun parse(id: String, source: String, section: ConfigurationSection): SellTable? {
            val layoutRaw = section.getStringList("Layout")
            if (layoutRaw.isEmpty()) {
                taboolib.common.platform.function.warning("[Indra] 出售界面 `$id` 缺少 Layout，已跳过")
                return null
            }

            // 统一为 9 列：不足补空格，超出截断（Bukkit 界面固定 9 列）
            val layout = layoutRaw.map { row ->
                val chars = row.toCharArray().toMutableList()
                while (chars.size < 9) chars += ' '
                chars.take(9)
            }

            val iconsSection = runCatching { section.getConfigurationSection("Icon") }.getOrNull()
            val icons = mutableMapOf<Char, IconDef>()
            if (iconsSection != null) {
                for (key in iconsSection.getKeys(false)) {
                    if (key.length != 1) {
                        taboolib.common.platform.function.warning(
                            "[Indra] 出售界面 `$id` 的 Icon 键 `$key` 必须是单个字符，已跳过"
                        )
                        continue
                    }
                    val ch = key[0]
                    val node = iconsSection.getConfigurationSection(key) ?: continue
                    val materialName = node.getString("Type") ?: "STONE"
                    val material = runCatching { Material.valueOf(materialName.uppercase()) }.getOrElse {
                        taboolib.common.platform.function.warning(
                            "[Indra] 出售界面 `$id` 的图标 `$ch` 材质无效：$materialName（回退 STONE）"
                        )
                        Material.STONE
                    }
                    val bindRaw = node.getString("Bind").orEmpty()
                    val bind = when (bindRaw.lowercase()) {
                        "sell" -> Bind.SELL
                        "close" -> Bind.CLOSE
                        "put" -> Bind.PUT
                        "" -> null
                        else -> {
                            taboolib.common.platform.function.warning(
                                "[Indra] 出售界面 `$id` 的图标 `$ch` 绑定了未知动作 `$bindRaw`（可用：Sell/Close/Put）"
                            )
                            null
                        }
                    }
                    icons[ch] = IconDef(
                        material = material,
                        data = node.getInt("Data"),
                        name = node.getString("Name").orEmpty(),
                        lore = node.getStringList("Lore"),
                        bind = bind,
                        putMatch = node.getString("Put-Match"),
                        ch = ch,
                    )
                }
            }

            val title = section.getString("Title").orEmpty().ifBlank { "§6出售界面" }
            return SellTable(id, source, title, layout, section.getBoolean("Auto-Sell"), icons)
        }
    }
}
