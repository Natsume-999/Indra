package com.indra.rpg.ui

import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.platform.util.buildItem

/**
 * 图标工厂 —— 统一「材质名 → ItemStack」的解析与容错。
 *
 * ── 为什么需要它 ────────────────────────────────────────
 *   menu.yml 里的材质是**字符串**（服主手写），而 `XMaterial` 是枚举。
 *   `XMaterial.valueOf("STONE")` 在材质名写错时抛 `IllegalArgumentException`，
 *   一个字母打错就会让整个面板打开失败并刷一行报错。
 *   这里全部走「解析失败 → 用兜底材质 + 控制台提示一次」，
 *   让配错材质变成「图标没变好看」而不是「面板打不开」。
 *
 * ── 为什么用 XMaterial 而不是 org.bukkit.Material ──────
 *   XMaterial 自带跨版本名称映射（如 `SKULL_ITEM` → 新版的
 *   `PLAYER_HEAD`），服主从老教程里抄来的材质名在新版本仍然能用。
 */
object MenuIcons {

    /** 已经提示过的错误材质，避免每次开面板刷一屏日志 */
    private val warned = mutableSetOf<String>()

    /** 兜底材质：写错了就显示这个，至少玩家知道「这里有东西」 */
    private val FALLBACK = XMaterial.STONE

    /**
     * 解析材质名。
     *
     * @param raw    menu.yml 里写的字符串
     * @param where  出错的配置路径（仅用于日志定位）
     */
    fun material(raw: String?, where: String = ""): XMaterial {
        if (raw.isNullOrBlank()) return FALLBACK
        val name = raw.trim().uppercase()
        return runCatching { XMaterial.valueOf(name) }.getOrElse {
            if (warned.add(name)) {
                taboolib.common.platform.function.warning(
                    "[Indra] menu.yml 中的材质 '$raw' 无法识别（$where），已改用 $FALLBACK。"
                )
            }
            FALLBACK
        }
    }

    /**
     * 构建一个纯展示图标（无点击行为）。
     *
     * @param material 材质名（XMaterial 名称）
     * @param name     显示名，支持 & 色码
     * @param lore     描述文本，支持 & 色码
     * @param amount   数量（默认 1）
     * @param shiny    是否附魔闪光（用来表达「选中 / 可点」状态）
     */
    fun icon(
        material: String,
        name: String,
        lore: List<String> = emptyList(),
        amount: Int = 1,
        shiny: Boolean = false,
        where: String = ""
    ): ItemStack = buildItem(material(material, where)) {
        this.amount = amount.coerceIn(1, 64)
        // TabooLib 的 ItemBuilder.name / lore 接收**已上色**的字符串，
        // 这里统一用 § 转换（buildItem 不做自动 & → § 转换）
        this.name = color(name)
        if (lore.isNotEmpty()) this.lore.addAll(lore.map { color(it) })
        if (shiny) shiny()
    }

    /**
     * 填充容器 —— 铺满所有槽位的背景板。
     *
     * @param slots 需要铺的槽位（通常由 AbstractMenu 传空槽位进来）
     */
    fun filler(slots: Iterable<Int>, material: String = "GRAY_STAINED_GLASS_PANE"): List<Pair<Int, ItemStack>> {
        val item = icon(material, " ", where = "filler")
        return slots.map { it to item }
    }

    /** & → §（与 [com.indra.rpg.util.Msg] 同一套规则，但不加前缀） */
    fun color(text: String): String = text.replace('&', '§')
}
