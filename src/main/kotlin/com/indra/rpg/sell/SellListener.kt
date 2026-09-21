package com.indra.rpg.sell

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/**
 * 出售界面交互监听。
 *
 * ── 用 Bukkit Listener 还是 TabooLib @SubscribeEvent ──────────
 *   本项目 ban 模块用的是 `@SubscribeEvent` 注解（TabooLib 风格，自动注册）。
 *   这里保持一致，便于统一管理生命周期。
 *
 * ── 必须拦掉的三种操作 ────────────────────────────────────────
 *   1. Shift+点击：会把物品**直接插进快捷栏/背包**，绕过我们的槽位控制
 *   2. 数字键（HOTBAR_SWAP）：把物品换到快捷栏，同理
 *   3. 拖拽（InventoryDragEvent）：一次动多个槽位，需要逐槽判断
 *   另外双击（COLLECT_TO_CURSOR）会跨容器收集同类物品，也要拦。
 */
object SellListener {

    /** 取出售界面的 holder；不是我们的界面则返回 null */
    private fun holderOf(view: org.bukkit.inventory.InventoryView): SellGui? =
        view.topInventory.holder as? SellGui

    @SubscribeEvent(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val gui = holderOf(event.view) ?: return
        val player = event.whoClicked as? Player ?: return

        val rawSlot = event.rawSlot
        val isTop = rawSlot in 0 until gui.table.size
        val isPlayerInv = rawSlot >= event.view.topInventory.size

        // ── 玩家背包区：只允许「Shift 点击把可售物品移入」，其余照常 ──
        if (isPlayerInv) {
            if (event.isShiftClick) {
                // 让出给默认行为会直接把东西塞进界面所有空位；
                // 这里我们自己来，确保只进「玩家物品槽」
                event.isCancelled = true
                shiftIntoGui(gui, event)
            }
            return
        }

        if (!isTop) return

        val def = gui.table.iconAt(rawSlot)

        // ── 界面内的按钮：拦截默认行为，执行绑定的动作 ──
        if (def != null) {
            event.isCancelled = true
            when (def.bind) {
                SellTable.Bind.SELL -> doSell(gui, player)
                SellTable.Bind.CLOSE -> player.closeInventory()
                SellTable.Bind.PUT -> {
                    val moved = gui.doPut()
                    if (moved == 0) {
                        player.sendMessage(com.indra.rpg.util.Msg.color("§7[§bIndra§7] §f没有可放入的物品。"))
                    }
                }
                null -> { /* 纯装饰，仅拦截 */ }
            }
            return
        }

        // ── 玩家物品槽：禁止那些会「逃出」界面的操作 ──
        when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD -> {
                event.isCancelled = true
            }
            else -> {
                // 允许在界面内自由摆放。放完刷新一次按钮统计。
                if (gui.table.autoSell) {
                    // 自动出售：投入即结算（延迟 1 tick，等物品落位）
                    taboolib.common.platform.function.submit(delay = 1L) {
                        doSell(gui, player, silentEmpty = true)
                    }
                } else {
                    taboolib.common.platform.function.submit {
                        gui.refreshButtons(gui.preview())
                    }
                }
            }
        }
    }

    /** 拖拽：只要碰到界面里的「按钮」或越界槽位一律取消 */
    @SubscribeEvent(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val gui = holderOf(event.view) ?: return
        val affected = event.rawSlots.filter { it < event.view.topInventory.size }
        if (affected.isEmpty()) return

        // 拖拽落在按钮上 → 取消
        if (affected.any { gui.table.iconAt(it) != null }) {
            event.isCancelled = true
            return
        }
        // 落在玩家物品槽 → 放行，之后刷新统计
        taboolib.common.platform.function.submit {
            gui.refreshButtons(gui.preview())
        }
    }

    /** 关闭界面 → 把剩余物品还给玩家 */
    @SubscribeEvent
    fun onClose(event: InventoryCloseEvent) {
        val gui = holderOf(event.view) ?: return
        gui.returnItems()
    }

    // ── 内部工具 ────────────────────────────────────────────────

    /** Shift 点击玩家背包物品 → 单个搬进界面的玩家物品槽 */
    private fun shiftIntoGui(gui: SellGui, event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        if (item.type.isAir) return
        val inv = gui.inventory
        val target = gui.table.playerSlots().firstOrNull { inv.getItem(it).let { s -> s == null || s.type.isAir } }
            ?: return
        inv.setItem(target, item.clone())
        event.currentItem = null
        taboolib.common.platform.function.submit {
            gui.refreshButtons(gui.preview())
        }
    }

    private fun doSell(gui: SellGui, player: Player, silentEmpty: Boolean = false) {
        gui.doSell { result ->
            when {
                result.error != null ->
                    player.sendMessage(com.indra.rpg.util.Msg.color("§7[§bIndra§7] §c出售失败：${result.error}"))

                result.amount == 0 && !silentEmpty ->
                    player.sendMessage(com.indra.rpg.util.Msg.color("§7[§bIndra§7] §f界面里没有可出售的物品。"))

                result.amount > 0 -> {
                    player.sendMessage(
                        com.indra.rpg.util.Msg.color(
                            "§7[§bIndra§7] §a已售出 §f${result.amount} §a个物品，" +
                                "获得 §e${result.money.toInt()} §a金币" +
                                if (result.point != 0) " §7+ §b${result.point} §7点券" else ""
                        )
                    )
                    if (result.unmatched > 0) {
                        player.sendMessage(
                            com.indra.rpg.util.Msg.color(
                                "§7[§bIndra§7] §7另有 §f${result.unmatched} §7个物品没有匹配的回收规则，已留在界面中。"
                            )
                        )
                    }
                    // 落审计
                    SellTradeLog.record(player, gui.table.id, result)
                }
            }
        }
    }
}
