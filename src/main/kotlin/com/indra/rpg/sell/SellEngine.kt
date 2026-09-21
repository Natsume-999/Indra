package com.indra.rpg.sell

import com.indra.rpg.common.economy.VaultBridge
import com.indra.rpg.common.script.KetherRunner
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning

/**
 * 出售结算引擎。
 *
 * ── 结算流程 ─────────────────────────────────────────────────
 *  1. 遍历界面里的「玩家物品槽」，跳过代表玩家自己的快捷栏/背包栏位
 *  2. 对每个物品，在**该界面可用的规则**里找**第一条匹配的**规则
 *     （顺序 = 文件名字母序 + 文件内顶层键顺序，与用户预期一致）
 *  3. 按规则的权重池抽取金钱/点券，累加
 *  4. 物品从界面清空 —— **只有全部结算成功才清除**，避免「物品没了钱也没到」
 *  5. 入账走 [VaultBridge]；失败则**回滚物品**并提示
 *
 * ── 为什么「先扣物再给钱」而不是反过来 ───────────────────────
 *   Vault 的 deposit 可能失败（离线、超限、经济插件异常）。
 *   这里采取**两阶段**：先算出总收益，再尝试入账；入账成功后才清空物品。
 *   若入账失败，物品原样保留在界面里，玩家可重试 —— 不会产生物品/货币不对等。
 *
 * ── 不支持的物品 ─────────────────────────────────────────────
 *   没有任何规则匹配的物品会被**留在界面里**并计入「未售出」统计，
 *   不会静默丢弃。玩家能直观看到哪些东西卖不掉。
 */
object SellEngine {

    /** 单次出售的结算结果 */
    data class Result(
        /** 实际售出的物品总数 */
        val amount: Int,
        /** 获得的金钱（玩家视角为正数） */
        val money: Double,
        /** 获得的点券（玩家视角为正数） */
        val point: Int,
        /** 未匹配任何规则的物品数 */
        val unmatched: Int,
        /** 失败原因；null 表示成功 */
        val error: String?,
        /** 已成功入账 */
        val settled: Boolean,
    )

    /**
     * 结算界面里的物品。
     *
     * @param inventory 出售界面（**只会动 [SellTable.playerSlots] 列出的槽位**）
     * @param table 当前界面模板
     */
    fun sell(player: Player, inventory: org.bukkit.inventory.Inventory, table: SellTable): Result {
        val rules = SellConfig.rulesFor(table.id)
        if (rules.isEmpty()) {
            return Result(0, 0.0, 0, 0, "当前界面没有可用出售规则", false)
        }

        var amount = 0
        var money = 0.0
        var point = 0
        var unmatched = 0
        val matchedRules = mutableSetOf<SellRule>()
        val soldSlots = mutableListOf<Int>()

        for (slot in table.playerSlots()) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue

            val rule = rules.firstOrNull { it.matcher?.matches(item) == true }
            if (rule == null) {
                unmatched += item.amount
                continue
            }

            // 条件脚本（Kether）—— 任一为假则本物品不可售
            if (!checkConditions(player, rule)) {
                unmatched += item.amount
                continue
            }

            amount += item.amount
            // ⚠️ Phoenix/VitaSell 语义：配置里负数 = 给予玩家。取反得到玩家视角收益。
            money += -rule.rollMoney() * item.amount
            point += -rule.rollPoint() * item.amount
            matchedRules += rule
            soldSlots += slot
        }

        if (amount == 0) {
            return Result(0, 0.0, 0, unmatched, null, false)
        }

        // ── 阶段二：入账 ────────────────────────────────────────
        val netMoney = money.coerceAtLeast(0.0)
        if (netMoney > 0.0) {
            val tx = VaultBridge.depositResult(player, netMoney)
            if (!tx.success) {
                // 入账失败 → 物品原样保留（本次不做任何清除）
                return Result(0, 0.0, 0, unmatched, "经济入账失败：${tx.error.ifBlank { "未知原因" }}", false)
            }
        }

        // ── 阶段三：入账成功，清除物品 ──────────────────────────
        for (slot in soldSlots) {
            inventory.setItem(slot, null)
        }

        // ── 阶段四：脚本回调 ────────────────────────────────────
        val variables = mapOf(
            "player" to player.name,
            "uuid" to player.uniqueId.toString(),
            "amount" to amount.toString(),
            "money" to netMoney.toString(),
            "point" to point.toString(),
        )
        matchedRules.forEach { rule ->
            if (rule.kether.isNotEmpty()) {
                KetherRunner.run(player, rule.kether, variables)
            }
        }

        return Result(amount, netMoney, point, unmatched, null, true)
    }

    /**
     * 检查规则的 Kether 条件。
     *
     * 条件为空 → 通过。非空 → 交给 [KetherRunner.evalBooleanBlocking] 求值。
     *
     * ⚠️ **保守策略**：求值返回 null（主线程、超时、异常）或非 true，
     *    一律视为**不满足**，该物品不可售。
     *    宁可卖不掉（玩家能看见、可重试），也不让带条件的规则被无条件触发
     *    （那样会造成刷钱漏洞）。
     */
    private fun checkConditions(player: Player, rule: SellRule): Boolean {
        if (rule.conditions.isEmpty()) return true
        val result = KetherRunner.evalBooleanBlocking(
            player,
            rule.conditions,
            mapOf("player" to player.name, "uuid" to player.uniqueId.toString()),
        )
        if (result == null) {
            warning("[Indra] 出售规则 `${rule.id}` 的条件无法求值（详见上一条日志），该物品按「不可售」处理。")
            return false
        }
        return result
    }

    /**
     * 一键放入：把玩家背包里匹配任一规则（或匹配指定 Put-Match）的物品搬进界面。
     * @return 实际放入的槽位数
     */
    fun putItems(player: Player, inventory: org.bukkit.inventory.Inventory, table: SellTable, icon: SellTable.IconDef?): Int {
        val rules = SellConfig.rulesFor(table.id)
        val explicit = icon?.putMatcher

        val targets = table.playerSlots().filter { inventory.getItem(it).let { s -> s == null || s.type.isAir } }
        if (targets.isEmpty()) return 0

        var moved = 0
        val storage = player.inventory

        for (slot in storage.contents.indices) {
            if (moved >= targets.size) break
            val item = storage.getItem(slot) ?: continue
            if (item.type.isAir) continue

            val ok = when {
                explicit != null -> explicit.matches(item)
                else -> rules.any { it.matcher?.matches(item) == true }
            }
            if (!ok) continue

            inventory.setItem(targets[moved], item.clone())
            storage.setItem(slot, null)
            moved++
        }
        return moved
    }
}
