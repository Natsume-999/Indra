package com.indra.rpg.sell

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.function.submit

/**
 * 出售指令集：`/indra sell ...`
 *
 * ── 命令树 ──────────────────────────────────────────────
 *   /indra sell [界面]                     打开出售界面
 *   /indra sell open <玩家> [界面]          为他人打开
 *   /indra sell list                       列出全部界面与规则数
 *   /indra sell reload                     重载规则与界面
 *   /indra sell log <玩家> [条数]           查看成交记录
 *
 * 与 Phoenix 的差异：Phoenix 只有 `open`/`reload`；这里补上 `list` 与 `log`，
 * 因为 Indra 把审计从文本文件改成了数据库（见 [SellTradeLog]），需要查询入口。
 *
 * ⚠️ 与封禁命令一致：定义在 **CommandBase** 上、写成顶层扩展，
 *    这样 `/indra sell ...` 不会多一层。
 */
fun CommandBase.sellChildren() {
    literal("sell") { sellBody() }
}

private fun CommandComponent.sellBody() {
    // 无参数：直接打开（单界面时）或提示
    execute<CommandSender> { sender, _, _ ->
        val player = sender as? Player ?: run {
            sender.sendMessage(com.indra.rpg.util.Msg.color("§c该命令只能由玩家执行。"))
            return@execute
        }
        openSell(player, null)
    }

    literal("open") { openBody() }
    literal("list") { listBody() }
    literal("reload") { reloadBody() }
    literal("log") { logBody() }

    // 带界面名：/indra sell <界面>
    dynamic("table") {
        suggestion<CommandSender> { _, _ -> SellConfig.tables.keys.toList() }
        execute<CommandSender> { sender, ctx, _ ->
            val player = sender as? Player ?: run {
                sender.sendMessage(com.indra.rpg.util.Msg.color("§c该命令只能由玩家执行。"))
                return@execute
            }
            openSell(player, ctx["table"])
        }
    }
}

// ══ 打开 ══════════════════════════════════════════════════

private fun CommandComponent.openBody() {
    dynamic("player") {
        suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        execute<CommandSender> { sender, ctx, _ ->
            if (!sender.hasPermission("indra.admin")) {
                sender.sendMessage(com.indra.rpg.util.Msg.color("§c你没有权限执行该操作。"))
                return@execute
            }
            val target = Bukkit.getPlayer(ctx["player"]) ?: run {
                sender.sendMessage(com.indra.rpg.util.Msg.color("§c玩家不在线：${ctx["player"]}"))
                return@execute
            }
            openSell(target, null)
            sender.sendMessage(
                com.indra.rpg.util.Msg.color("§7[§bIndra§7] §f已为 §e${target.name} §f打开出售界面。")
            )
        }
    }.dynamic("table") {
        suggestion<CommandSender> { _, _ -> SellConfig.tables.keys.toList() }
        execute<CommandSender> { sender, ctx, _ ->
            if (!sender.hasPermission("indra.admin")) {
                sender.sendMessage(com.indra.rpg.util.Msg.color("§c你没有权限执行该操作。"))
                return@execute
            }
            val target = Bukkit.getPlayer(ctx["player"]) ?: run {
                sender.sendMessage(com.indra.rpg.util.Msg.color("§c玩家不在线：${ctx["player"]}"))
                return@execute
            }
            openSell(target, ctx["table"])
        }
    }
}

// ══ 列表 ══════════════════════════════════════════════════

private fun CommandComponent.listBody() {
    execute<CommandSender> { sender, _, _ ->
        if (!sender.hasPermission("indra.admin")) {
            sender.sendMessage(com.indra.rpg.util.Msg.color("§c你没有权限执行该操作。"))
            return@execute
        }
        val prefix = com.indra.rpg.util.Msg.color("§7[§bIndra§7] ")
        sender.sendMessage(prefix + com.indra.rpg.util.Msg.color("§f出售系统：§e${SellConfig.rules.size} §f条规则 / §e${SellConfig.tables.size} §f个界面"))

        if (SellConfig.tables.isEmpty()) {
            sender.sendMessage(prefix + com.indra.rpg.util.Msg.color("§7（未加载到界面，检查 plugins/Indra/table/）"))
        } else {
            for ((id, table) in SellConfig.tables) {
                val ruleCount = SellConfig.rulesFor(id).size
                val size = "${table.size / 9} 行"
                val auto = if (table.autoSell) " §a[自动出售]" else ""
                sender.sendMessage(prefix + com.indra.rpg.util.Msg.color("  §e$id §7(${size}, §f$ruleCount §7条规则)$auto"))
            }
        }
    }
}

// ══ 重载 ══════════════════════════════════════════════════

private fun CommandComponent.reloadBody() {
    execute<CommandSender> { sender, _, _ ->
        if (!sender.hasPermission("indra.admin")) {
            sender.sendMessage(com.indra.rpg.util.Msg.color("§c你没有权限执行该操作。"))
            return@execute
        }
        val (rules, tables) = SellConfig.load()
        sender.sendMessage(
            com.indra.rpg.util.Msg.color(
                "§7[§bIndra§7] §a出售配置已重载：§e$rules §a条规则 / §e$tables §a个界面。"
            )
        )
    }
}

// ══ 成交记录 ══════════════════════════════════════════════

private fun CommandComponent.logBody() {
    dynamic("player") {
        suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        execute<CommandSender> { sender, ctx, _ -> sendLog(sender, ctx["player"], 10) }
    }.dynamic("limit") {
        suggestion<CommandSender> { _, _ -> listOf("10", "20", "50") }
        execute<CommandSender> { sender, ctx, _ ->
            sendLog(sender, ctx["player"], ctx["limit"].toIntOrNull()?.coerceIn(1, 100) ?: 10)
        }
    }
}

private fun sendLog(sender: CommandSender, playerName: String, limit: Int) {
    if (!sender.hasPermission("indra.admin")) {
        sender.sendMessage(com.indra.rpg.util.Msg.color("§c你没有权限执行该操作。"))
        return
    }
    // 查询是 IO → 异步执行，结果回主线程发送
    submit(async = true) {
        val lines = SellTradeLog.recent(playerName, limit)
        submit {
            val prefix = com.indra.rpg.util.Msg.color("§7[§bIndra§7] ")
            sender.sendMessage(prefix + com.indra.rpg.util.Msg.color("§f§e$playerName §f最近 §e${lines.size} §f条成交记录："))
            if (lines.isEmpty()) {
                sender.sendMessage(prefix + com.indra.rpg.util.Msg.color("§7（无记录）"))
            } else {
                lines.forEach { sender.sendMessage("  $it") }
            }
        }
    }
}

// ══ 共用 ══════════════════════════════════════════════════

private fun openSell(player: Player, tableName: String?) {
    val prefix = com.indra.rpg.util.Msg.color("§7[§bIndra§7] ")

    if (SellConfig.tables.isEmpty() && SellConfig.autoReleaseDefaults) {
        SellConfig.releaseDefaults()
        SellConfig.load()
    }

    val table = when {
        tableName != null -> SellConfig.table(tableName) ?: run {
            player.sendMessage(prefix + com.indra.rpg.util.Msg.color("§c找不到出售界面：§f$tableName"))
            return
        }
        SellConfig.tables.size == 1 -> SellConfig.tables.values.first()
        SellConfig.directWhenSingle && SellConfig.tables.isEmpty() -> {
            player.sendMessage(prefix + com.indra.rpg.util.Msg.color("§c没有任何出售界面，请检查 plugins/Indra/table/"))
            return
        }
        else -> {
            player.sendMessage(prefix + com.indra.rpg.util.Msg.color("§f可用界面：§e${SellConfig.tables.keys.joinToString("§7, §e")}"))
            return
        }
    }

    val gui = SellGui(player, table)
    player.openInventory(gui.inventory)
    submit(delay = 1L) { gui.refreshButtons(gui.preview()) }
}
