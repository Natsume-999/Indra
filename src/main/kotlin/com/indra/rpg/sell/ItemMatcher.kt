package com.indra.rpg.sell

import org.bukkit.inventory.ItemStack

/**
 * Arim 物品匹配串裁剪版。
 *
 * ── 为什么不用完整 Arim ──────────────────────────────────────
 *   Phoenix 的出售规则用的是 Arim（TabooLib 的物品匹配 DSL），
 *   但完整 Arim 依赖 TabooLib 的 `taboolib.library.` 一系列重定位库，
 *   而 Indra 禁用了后处理、**不提供 relocate 能力**（见 README 第二节）。
 *   因此这里只实现 Phoenix 出售规则里**实际会用到**的子集，
 *   语法与 Arim 保持一致，用户现有配置文件可原样迁入。
 *
 * ── 支持的语法（分号分隔，多条件 AND）────────────────────────
 *   name:<文本>              显示名**完全等于**
 *   name:contains(<文本>)    显示名**包含**
 *   name:!contains(<文本>)   显示名**不包含**
 *   lore:<文本>              Lore 任一行完全等于
 *   lore:contains(<文本>)    Lore 任一行包含
 *   lore:!contains(<文本>)   Lore 任一行都不包含
 *   type:<Material>          材质名（可用逗号分隔多个：type:STONE,DIRT）
 *   amount:<n>               **至少** n 个（堆叠数下限）
 *   amount:>=<n> / >n / =n / <n / <=n / !=n
 *   custommodeldata:<n>      自定义模型数据（1.14+）
 *   unbreakable:true|false   是否不可破坏
 *
 * ── 颜色码处理 ───────────────────────────────────────────────
 *   配置里的 `&a` 与物品上的 `§a` 是同一颜色，匹配前统一归一化，
 *   否则用户写 `&f示例材料` 永远匹配不到显示名为 `§f示例材料` 的物品。
 *
 * ── 不支持（遇到会警告并视为「不匹配」）──────────────────────
 *   nbt: / tag: / itemmodel: / 各类插件物品（ItemsAdder、MMOItems 等）
 *   —— 需要这些请改用 `Condition: [ Kether 条件 ]` 自行实现。
 */
object ItemMatcher {

    /**
     * 编译后的单个条件。
     *
     * ⚠️ 这里**不能**用 `sealed interface`：条件是用匿名对象实现的，
     *    而 Kotlin 不允许匿名对象继承 sealed 类型（sealed 要求子类在
     *    同一文件/模块内**具名**声明）。用普通 interface 即可。
     *
     * ⚠️ 也**不能**标 `private`：它作为 [Matcher] 的构造参数类型，
     *    而 [Matcher] 是 public —— 否则报
     *    「'public' function exposes its 'private-in-class' parameter type」。
     *    `internal` 同理（会对跨模块调用方报错）。故用 public 但加 @PublishedApi 语义说明。
     */
    interface Predicate {
        fun test(item: ItemStack): Boolean
    }

    /** 一个匹配串编译出的全部条件（AND 关系） */
    class Matcher internal constructor(private val predicates: List<Predicate>, val raw: String) {
        fun matches(item: ItemStack?): Boolean {
            if (item == null || item.type.isAir) return false
            return predicates.all { it.test(item) }
        }
    }

    // ── 颜色归一化 ──────────────────────────────────────────────

    private val COLOR_CODES = listOf(
        "&0" to "§0", "&1" to "§1", "&2" to "§2", "&3" to "§3", "&4" to "§4",
        "&5" to "§5", "&6" to "§6", "&7" to "§7", "&8" to "§8", "&9" to "§9",
        "&a" to "§a", "&b" to "§b", "&c" to "§c", "&d" to "§d", "&e" to "§e",
        "&f" to "§f", "&k" to "§k", "&l" to "§l", "&m" to "§m", "&n" to "§n",
        "&o" to "§o", "&r" to "§r",
    )

    /** 把 `&x` 与传统码统一成 `§x`，并去掉首尾空白 */
    fun normalizeColors(text: String): String {
        var result = text.trim()
        for ((from, to) in COLOR_CODES) {
            result = result.replace(from, to, ignoreCase = true)
        }
        // 同时兼容 § 已被写在配置里的情况（统一为小写 §）
        return result.replace('&', '§')
    }

    // ── 编译 ────────────────────────────────────────────────────

    /**
     * 把匹配串编译为 [Matcher]。
     * @return null 表示串为空（调用方应视为「无条件」）
     */
    fun compile(spec: String): Matcher? {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) return null

        val predicates = mutableListOf<Predicate>()
        // ⚠️ 不能简单地按 ';' split —— `lore:contains(a;b)` 里可能含分号。
        //    这里做括号感知的分割。
        for (segment in splitTopLevel(trimmed, ';')) {
            val token = segment.trim()
            if (token.isEmpty()) continue
            val idx = token.indexOf(':')
            if (idx <= 0) {
                taboolib.common.platform.function.warning(
                    "[Indra] 出售匹配串片段无法解析（缺少 `键:` 前缀），已忽略：$token"
                )
                continue
            }
            val key = token.substring(0, idx).trim().lowercase()
            val value = token.substring(idx + 1).trim()
            val predicate = buildPredicate(key, value)
                ?: run {
                    taboolib.common.platform.function.warning(
                        "[Indra] 出售匹配串使用了不支持的键 `$key`（片段：$token），该项将永不匹配。"
                            + "如需复杂条件请改用 Condition 的 Kether 脚本。"
                    )
                    return null
                }
            predicates += predicate
        }
        return if (predicates.isEmpty()) null else Matcher(predicates, spec)
    }

    /** 括号感知的分割：`a(b;c);d` → ["a(b;c)", "d"] */
    private fun splitTopLevel(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in text) {
            when {
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; sb.append(ch) }
                ch == delimiter && depth == 0 -> { parts += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        return parts
    }

    private fun buildPredicate(key: String, value: String): Predicate? = when (key) {
        "name" -> textPredicate(value) { itemName(it) }
        "lore" -> textPredicate(value) { itemLore(it) }
        "type" -> typePredicate(value)
        "amount" -> amountPredicate(value)
        "custommodeldata" -> customModelDataPredicate(value)
        "unbreakable" -> unbreakablePredicate(value)
        else -> null
    }

    // ── 文本类条件 ──────────────────────────────────────────────

    /** 文本来源：null 表示「不适用」（例如纯 type 匹配的物品没有名字） */
    private fun textPredicate(value: String, source: (ItemStack) -> List<String>?): Predicate {
        val negated = value.startsWith("!contains(") || value.startsWith("!")
        val contains = value.contains("contains(")

        val inner = when {
            negated && contains -> value.substringAfter("!contains(").substringBeforeLast(")")
            contains -> value.substringAfter("contains(").substringBeforeLast(")")
            value.startsWith("!") -> value.substring(1)
            else -> value
        }
        val expected = normalizeColors(inner)

        return object : Predicate {
            override fun test(item: ItemStack): Boolean {
                val lines = source(item)
                return when {
                    // 物品没有该文本属性 → 负向条件视为满足，正向条件不满足
                    lines == null -> negated
                    contains -> lines.any { normalizeColors(it).contains(expected) }.let { if (negated) !it else it }
                    else -> lines.any { normalizeColors(it) == expected }.let { if (negated) !it else it }
                }
            }
        }
    }

    /**
     * 取物品显示名（渲染为 § 传统码文本）。
     *
     * ⚠️ Paper 26.3 起 `ItemMeta.getDisplayName()`（返回 String）**已弃用**，
     *    显示名的唯一事实来源是 `displayName()`（返回 Adventure Component）。
     *    继续用弃用 API 会**读不到**物品的真实名字
     *    （26.3 的物品名内部是 Component，String 版只做有损转换），
     *    因此这里改为取 Component 再序列化成 § 文本。
     */
    private fun itemName(item: ItemStack): List<String>? {
        val meta = runCatching { item.itemMeta }.getOrNull()
        if (meta == null) {
            // 无 meta 的方块/材料：退回材质名，便于 `name:STONE` 这类写法也能命中
            return listOf(item.type.name)
        }
        val legacy = runCatching {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(meta.displayName() ?: net.kyori.adventure.text.Component.empty())
        }.getOrNull()

        return when {
            legacy.isNullOrEmpty() -> listOf(item.type.name)
            else -> listOf(legacy)
        }
    }

    /**
     * 取物品 Lore（渲染为 § 传统码文本）。
     *
     * ⚠️ 同上：`getLore()`（返回 List&lt;String&gt;）已弃用，改用 `lore()`（Component 列表）。
     */
    private fun itemLore(item: ItemStack): List<String>? {
        val meta = runCatching { item.itemMeta }.getOrNull() ?: return null
        val components = runCatching { meta.lore() }.getOrNull() ?: return null
        if (components.isEmpty()) return null
        return components.map {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(it)
        }
    }

    // ── 材质 ────────────────────────────────────────────────────

    private fun typePredicate(value: String): Predicate? {
        val names = value.split(',', '|').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return null
        val types = names.mapNotNull { name ->
            runCatching { org.bukkit.Material.valueOf(name) }.getOrNull()
        }
        if (types.isEmpty()) {
            taboolib.common.platform.function.warning(
                "[Indra] 出售匹配串的 type 全部无效：$value（请检查 Material 名称是否为 Paper 26.3 的写法）"
            )
            return null
        }
        return object : Predicate {
            override fun test(item: ItemStack): Boolean = item.type in types
        }
    }

    // ── 数量 ────────────────────────────────────────────────────

    private fun amountPredicate(raw: String): Predicate? {
        val value = raw.trim()
        // 无运算符 → 视为「至少 n 个」
        val match = Regex("^(>=|<=|!=|>|<|=)?\\s*(\\d+)$").find(value) ?: run {
            taboolib.common.platform.function.warning("[Indra] 出售匹配串的 amount 格式无效：$raw")
            return null
        }
        val op = match.groupValues[1].ifEmpty { ">=" }
        val target = match.groupValues[2].toInt()
        return object : Predicate {
            override fun test(item: ItemStack): Boolean {
                val n = item.amount
                return when (op) {
                    ">=" -> n >= target
                    ">" -> n > target
                    "<=" -> n <= target
                    "<" -> n < target
                    "!=" -> n != target
                    else -> n == target
                }
            }
        }
    }

    // ── 其他 ────────────────────────────────────────────────────

    private fun customModelDataPredicate(raw: String): Predicate? {
        val target = raw.trim().toIntOrNull() ?: run {
            taboolib.common.platform.function.warning("[Indra] 出售匹配串的 custommodeldata 不是整数：$raw")
            return null
        }
        return object : Predicate {
            override fun test(item: ItemStack): Boolean = runCatching {
                val meta = item.itemMeta ?: return false
                // ⚠️ `getCustomModelData()` 已弃用，改用 `getCustomModelDataComponent()`。
                //    26.3 起 CMD 是「浮点列表 + 字符串列表」的组件结构，
                //    这里按最常见的用法取第一项 float 作为整数比较，
                //    同时兼容旧数据（component 的 floats 里存的就是原 int）。
                if (!meta.hasCustomModelDataComponent()) return false
                val component = meta.customModelDataComponent
                val floats = component.floats
                if (floats.isEmpty()) return false
                floats.first().toInt() == target
            }.getOrDefault(false)
        }
    }

    private fun unbreakablePredicate(raw: String): Predicate {
        val expect = raw.trim().equals("true", ignoreCase = true)
        return object : Predicate {
            override fun test(item: ItemStack): Boolean =
                runCatching { item.itemMeta?.isUnbreakable == expect }.getOrDefault(!expect)
        }
    }
}
