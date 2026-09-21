package com.indra.rpg.sell

import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import taboolib.common.platform.function.submit

/**
 * 出售界面（原生 Bukkit Inventory + InventoryHolder）。
 *
 * ── 为什么不用 TabooLib 的 BukkitUI ──────────────────────────
 *   1. BukkitUI 的菜单模型面向「静态图标 + 点击回调」，
 *      而出售界面需要**玩家自由放置物品**并统计这些槽位的内容；
 *   2. 需要精确控制「哪些槽位放物品、哪些槽位是按钮」，
 *      原生 `Inventory` + `InventoryClickEvent` 语义最直白；
 *   3. 少一层抽象 = 少一处随 TabooLib 版本变动的风险面。
 *   （`bukkit-ui` 模块仍在 env 清单里，A5 统一面板系统会用到它。）
 *
 * ── holder 的意义 ────────────────────────────────────────────
 *   把 [SellTable] 挂在 holder 上，点击事件里可 O(1) 判断
 *   「这个 Inventory 是不是我们的出售界面」，不用靠标题字符串比对
 *   （标题可能被玩家或插件改，不可靠）。
 */
class SellGui(
    val player: Player,
    val table: SellTable,
) : InventoryHolder {

    private val inventory: Inventory = Bukkit.createInventory(
        this,
        table.size,
        // ⚠️ Paper 26.3：`createInventory(holder, size, String)` 已弃用，
        //    标题应传 Adventure Component（String 重载只做有损转换）。
        adv(Msg.color(table.title)),
    )

    init {
        renderDecorations()
    }

    override fun getInventory(): Inventory = inventory

    /** 铺满所有「非玩家槽位」的装饰图标 */
    private fun renderDecorations() {
        for (slot in 0 until table.size) {
            val def = table.iconAt(slot) ?: continue
            inventory.setItem(slot, table.renderIcon(def))
        }
    }

    /**
     * 重新渲染按钮上的动态文本（例如「本次预计收入」）。
     * 装饰性图标不变，只刷新按钮，避免把玩家放进去的物品覆盖掉。
     */
    fun refreshButtons(preview: SellEngine.Result? = null) {
        for ((slot, def) in table.buttonSlots()) {
            inventory.setItem(slot, renderButton(def, preview))
        }
    }

    private fun renderButton(def: SellTable.IconDef, preview: SellEngine.Result?): org.bukkit.inventory.ItemStack {
        val item = table.renderIcon(def)
        if (def.bind != SellTable.Bind.SELL || preview == null) return item
        val meta = item.itemMeta ?: return item
        val extra = mutableListOf<String>()
        extra += "§7本次预计：§e${preview.money.toInt()} §7金币"
        if (preview.point != 0) extra += "§7点券：§b${preview.point}"
        extra += "§7可售物品：§f${preview.amount} §7个"
        if (preview.unmatched > 0) extra += "§c未匹配：§f${preview.unmatched} §7个"
        val existing = meta.lore() ?: emptyList()
        // Paper 26.3：lore 只接受 List<Component>
        meta.lore(existing + extra.map { adv(it) })
        item.itemMeta = meta
        return item
    }

    /** 统计当前界面内容（用于按钮预览，不产生任何副作用） */
    fun preview(): SellEngine.Result {
        val rules = SellConfig.rulesFor(table.id)
        var amount = 0
        var money = 0.0
        var point = 0
        var unmatched = 0
        for (slot in table.playerSlots()) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            val rule = rules.firstOrNull { it.matcher?.matches(item) == true }
            if (rule == null) {
                unmatched += item.amount
                continue
            }
            // 预览用期望值（权重池的数学期望），不掷骰 —— 否则每次刷新数字都在跳
            amount += item.amount
            money += -rule.moneyPool.expectedValue() * item.amount
            point += (-rule.pointPool.expectedValue()).toInt() * item.amount
        }
        return SellEngine.Result(amount, money.coerceAtLeast(0.0), point, unmatched, null, false)
    }

    /** 把界面里剩余物品还给玩家（关闭界面时调用） */
    fun returnItems() {
        for (slot in table.playerSlots()) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            inventory.setItem(slot, null)
            val leftover = player.inventory.addItem(item)
            // 背包满了 → 掉在脚下，绝不静默吞掉
            for ((_, rest) in leftover) {
                player.world.dropItemNaturally(player.location, rest)
            }
        }
    }

    /** 执行一次出售（在主线程调度，保证线程安全） */
    fun doSell(onDone: (SellEngine.Result) -> Unit) {
        submit {
            val result = SellEngine.sell(player, inventory, table)
            refreshButtons(result)
            onDone(result)
        }
    }

    /** 一键放入 */
    fun doPut(): Int {
        val putIcon = table.buttonSlots().firstOrNull { it.second.bind == SellTable.Bind.PUT }?.second
        val moved = SellEngine.putItems(player, inventory, table, putIcon)
        refreshButtons(preview())
        return moved
    }

    companion object {
        /** 期望值：Σ(值 × 权重) / Σ权重 —— 用于按钮预览，避免数字乱跳 */
        private fun List<SellRule.WeightedEntry>.expectedValue(): Double {
            if (isEmpty()) return 0.0
            val total = sumOf { it.weight }
            if (total <= 0.0) return 0.0
            return sumOf { ((it.min + it.max) / 2.0) * it.weight } / total
        }
    }
}

/**
 * String → Adventure Component（§ 颜色码由 legacy 序列化器解析）。
 *
 * ⚠️ 定义为**文件顶层私有函数**而不是类成员：
 *    它在 [SellGui] 的**属性初始化器**里就要用到（inventory 的标题），
 *    放类内虽能工作（成员函数不受声明顺序限制），但放在属性之前更清晰。
 */
private fun adv(text: String): net.kyori.adventure.text.Component =
    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
        .legacySection().deserialize(text)
