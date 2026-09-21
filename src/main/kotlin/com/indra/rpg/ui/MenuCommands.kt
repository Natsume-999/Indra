package com.indra.rpg.ui

import com.indra.rpg.ui.menu.PlayerInfoMenu
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.command.component.CommandComponent

/**
 * 面板指令集 —— 挂在 `/indra` 根下。
 *
 * ── 命令树 ──────────────────────────────────────────────
 *   /indra menu [面板]              打开面板（无参 = 主菜单）
 *   /indra panel <面板> [玩家]      打开指定面板（可指定目标玩家）
 *   /indra banlist                  快捷打开封禁管理面板
 *
 * ── 为什么不另开根指令 `/menu` ═══════════════════════════
 *   TabooLib 的每个 `command(...)` 调用会注册一个**独立的根指令**，
 *   需要各自处理权限、别名、冲突。Indra 的功能已经足够多，
 *   再铺 `/menu` `/banlist` `/panel` 三个根指令会让指令表变乱
 *   （也更容易与服务端其他插件撞名）。
 *   统一收在 `/indra` 下，用补全引导，实际体验并不差。
 *
 * ⚠️ 与 [BanCommands] 一样写成 **CommandBase 上的顶层扩展**，理由见那边的注释：
 *    成员扩展需要双 receiver 作用域，调用方得写 `Xxx.run { ... }` 才不歧义。
 */
fun CommandBase.menuChildren() {
    literal("menu") { menuBody() }
    literal("panel") { panelBody() }
    literal("banlist") { banlistBody() }
}

// ══ /indra menu [面板] ═══════════════════════════════════

private fun CommandComponent.menuBody() {
    // 无参 → 主菜单
    execute<CommandSender> { sender, _, _ ->
        val player = requirePlayer(sender) ?: return@execute
        MenuManager.openRoot(player)
    }
    // 带面板名 → 直接打开该面板
    dynamic("面板") {
        suggestion<CommandSender> { _, _ -> MenuManager.ids() }
        execute<CommandSender> { sender, ctx, _ ->
            val player = requirePlayer(sender) ?: return@execute
            MenuManager.open(player, ctx["面板"])
        }
    }
}

// ══ /indra panel <面板> [玩家] ═══════════════════════════

private fun CommandComponent.panelBody() {
    dynamic("面板") {
        suggestion<CommandSender> { _, _ -> MenuManager.ids() }
        execute<CommandSender> { sender, ctx, _ ->
            val player = requirePlayer(sender) ?: return@execute
            MenuManager.open(player, ctx["面板"])
        }
    }.dynamic("玩家") {
        suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        execute<CommandSender> { sender, ctx, _ ->
            // 为他人打开需要管理员权限 —— 否则玩家能强制把面板推到别人屏幕上
            if (!sender.hasPermission("indra.admin")) {
                Msg.send(sender, "&c你没有权限为其他玩家打开面板。")
                return@execute
            }
            val target = Bukkit.getPlayerExact(ctx["玩家"])
            if (target == null) {
                Msg.send(sender, "&c玩家不在线：${ctx["玩家"]}")
                return@execute
            }
            val menuId = ctx["面板"]
            // 「玩家信息」面板支持指定目标；其他面板忽略该参数
            if (menuId.equals("player", ignoreCase = true)) {
                MenuManager.openFor(target, playerInfoFactory(target))
            } else {
                MenuManager.open(target, menuId)
            }
            Msg.send(sender, "&a已为 &f${target.name} &a打开面板 &f${MenuManager.label(menuId)}&a。")
        }
    }
}

// ══ /indra banlist ══════════════════════════════════════

private fun CommandComponent.banlistBody() {
    execute<CommandSender> { sender, _, _ ->
        val player = requirePlayer(sender) ?: return@execute
        MenuManager.open(player, "ban")
    }
}

// ══ 工具 ════════════════════════════════════════════════

/**
 * 面板只能开给玩家 —— 控制台没有 inventory。
 * 返回 null 表示已经给 sender 发了提示，调用方直接 return 即可。
 */
private fun requirePlayer(sender: CommandSender): Player? {
    val player = sender as? Player
    if (player == null) Msg.send(sender, "&c面板只能由游戏内玩家打开（控制台没有界面）。")
    return player
}

/**
 * 「为他人打开玩家信息面板」的工厂。
 *
 * ⚠️ 这是个**特例**：其他面板的构造函数只接收「谁在看」，
 *    玩家信息面板还需要「看谁」。为这一个特例在 [MenuManager]
 *    里开一个通用参数通道不划算，所以单独提供一个工厂。
 */
private fun playerInfoFactory(target: Player): (Player) -> AbstractMenu = { viewer ->
    PlayerInfoMenu(viewer, target)
}
