package com.indra.rpg.ban

import com.indra.rpg.Indra
import com.indra.rpg.ban.BanMessages.getPlayerID
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.command.component.CommandComponent

/**
 * 封禁指令集：`/indra ban ...`
 *
 * ── 命令树 ──────────────────────────────────────────────
 *   /indra ban <玩家> [时长] [原因]        封禁（在线/离线自动分流）
 *   /indra unban <玩家>                    解封
 *   /indra kick <玩家> [原因]              踢出
 *   /indra whitelist <玩家> <true|false>   白名单
 *   /indra warn <玩家> [原因]              警告
 *   /indra delwarn <玩家> <序号|all>       删除警告
 *   /indra warnings <玩家>                 查看警告列表
 *   /indra banstatus <玩家>                查看封禁状态
 *   /indra banhistory <玩家>               查看操作历史
 *
 * 移植自 Phoenix 的 command/BanCommands.kt（v1.16.0），
 * 消息由 i18n `sendLang` 改为 Indra 的 [Msg] 直发（Indra 无语言文件）。
 *
 * ⚠️ TabooLib 6.3 指令 DSL：
 *    · 子命令用 `literal(name)` / `dynamic(name)`，链式 `.dynamic()` 追加参数
 *    · `execute<CommandSender> { sender, ctx, _ -> }` 三参形式
 *    · 补全用 `suggestion<T> { _, _ -> List<String> }`
 */
/**
 * 挂到 /indra 根下。
 *
 * ⚠️ 定义在 **CommandBase** 上（不是 CommandComponent）：
 *    `command("indra") { ... }` 的 lambda 接收者是 CommandBase，
 *    这样才能得到 `/indra ban <玩家>` 而不是 `/indra x ban <玩家>`。
 *
 * ⚠️ 刻意写成**文件顶层扩展**而不是 `object BanCommands { ... }`：
 *    成员扩展函数需要「dispatch receiver + extension receiver」两个作用域，
 *    调用方得写成 `BanCommands.run { root.banChildren() }` 才不歧义。
 *    顶层扩展直接在 CommandBase 的 lambda 里 `banChildren()` 即可。
 */
fun CommandBase.banChildren() {
    literal("ban") { banBody() }
    literal("unban") { unbanBody() }
    literal("kick") { kickBody() }
    literal("whitelist") { whitelistBody() }
    literal("warn") { warnBody() }
    literal("delwarn") { delwarnBody() }
    literal("warnings") { warningsBody() }
    literal("banstatus") { statusBody() }
    literal("banhistory") { historyBody() }
}

    // ══ 封禁 ══════════════════════════════════════════

    private fun CommandComponent.banBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                banExecute(
                    sender, ctx["player"],
                    BanConfig.getDefaultBanReason(),
                    BanConfig.getTimeFormat().getString("Permanent") ?: "10y"
                )
            }
        }.dynamic("duration") {
            suggestion<CommandSender> { _, _ -> listOf("1h", "1d", "7d", "30d", "10y") }
            execute<CommandSender> { sender, ctx, _ ->
                banExecute(sender, ctx["player"], BanConfig.getDefaultBanReason(), ctx["duration"])
            }
        }.dynamic("reason") {
            execute<CommandSender> { sender, ctx, _ ->
                banExecute(sender, ctx["player"], ctx["reason"], ctx["duration"])
            }
        }
    }

    private fun banExecute(sender: CommandSender, playerName: String, banReason: String, banDuration: String) {
        val banTime = BanMessages.nowText()
        val banningAdmin = sender.name
        val online = Bukkit.getPlayer(playerName)
        if (online != null) {
            BanApi.banOnlinePlayer(online, banReason, banDuration, banTime, banningAdmin)
        } else {
            BanApi.banOfflinePlayer(playerName, banReason, banDuration, banTime, banningAdmin)
        }
        Msg.send(sender, "&a已封禁 &e$playerName &7（时长 &e$banDuration&7，原因 &e$banReason&7）")
    }

    // ══ 解封 ══════════════════════════════════════════

    private fun CommandComponent.unbanBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                BanApi.unbanPlayer(ctx["player"].playerID())
                Msg.send(sender, "&a已解封 &e${ctx["player"]}")
            }
        }
    }

    // ══ 踢出 ══════════════════════════════════════════

    private fun CommandComponent.kickBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                kickExecute(sender, ctx["player"], BanConfig.getDefaultKickReason())
            }
        }.dynamic("reason") {
            execute<CommandSender> { sender, ctx, _ ->
                kickExecute(sender, ctx["player"], ctx["reason"])
            }
        }
    }

    private fun kickExecute(sender: CommandSender, playerName: String, reason: String) {
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            Msg.send(sender, "&c玩家不在线：$playerName")
            return
        }
        BanApi.kickPlayer(target, reason, BanMessages.nowText(), sender.name)
        Msg.send(sender, "&a已请出 &e$playerName")
    }

    // ══ 白名单 ════════════════════════════════════════

    private fun CommandComponent.whitelistBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        }.dynamic("value") {
            suggestion<CommandSender> { _, _ -> listOf("true", "false") }
            execute<CommandSender> { sender, ctx, _ ->
                val flag = ctx["value"].toBooleanStrictOrNull()
                if (flag == null) {
                    Msg.send(sender, "&c值只能是 true 或 false")
                    return@execute
                }
                BanApi.whitelistPlayer(
                    ctx["player"].playerID(), flag, BanMessages.nowText(), sender.name
                )
                Msg.send(
                    sender,
                    if (flag) "&a已将 &e${ctx["player"]} &a加入白名单"
                    else "&a已将 &e${ctx["player"]} &a移出白名单"
                )
            }
        }
    }

    // ══ 警告 ══════════════════════════════════════════

    private fun CommandComponent.warnBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        }.dynamic("reason") {
            execute<CommandSender> { sender, ctx, _ ->
                val playerName = ctx["player"]
                val playerID = playerName.playerID()
                val reason = ctx["reason"].ifBlank { BanConfig.getDefaultWarnReason() }
                val record = BanApi.warnPlayer(playerID, reason, sender.name)
                val count = BanApi.getPlayerWarningCount(playerID)
                val threshold = BanConfig.getWarnAutoBanThreshold()

                // 通知被警告玩家
                Bukkit.getPlayer(playerName)?.sendMessage(
                    BanMessages.getWarnFormat(Bukkit.getPlayer(playerName)!!, reason, record.time, sender.name)
                )
                Msg.send(sender, "&a已警告 &e$playerName &7（当前 &e$count &7次）")
                if (threshold > 0 && count >= threshold) {
                    Msg.send(sender, "&c警告已达阈值（$threshold），该玩家已被自动封禁")
                }
            }
        }
    }

    private fun CommandComponent.delwarnBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
        }.dynamic("index") {
            suggestion<CommandSender> { _, _ -> listOf("1", "all") }
            execute<CommandSender> { sender, ctx, _ ->
                val playerName = ctx["player"]
                val playerID = playerName.playerID()
                val index = ctx["index"]

                if (index.equals("all", ignoreCase = true)) {
                    val removed = BanApi.clearWarnings(playerID)
                    if (removed > 0) Msg.send(sender, "&a已清空 &e$playerName &a的 $removed 条警告")
                    else Msg.send(sender, "&7$playerName 没有警告记录")
                    return@execute
                }

                val number = index.toIntOrNull()
                val warnings = BanApi.getPlayerWarnings(playerID)
                if (number == null || number < 1 || number > warnings.size) {
                    Msg.send(sender, "&c序号无效：$index（共 ${warnings.size} 条）")
                    return@execute
                }
                BanApi.removeWarning(warnings[number - 1].id)
                Msg.send(sender, "&a已删除第 $number 条警告，剩余 &e${warnings.size - 1} &a条")
            }
        }
    }

    private fun CommandComponent.warningsBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                val playerName = ctx["player"]
                val list = BanApi.getPlayerWarnings(playerName.playerID())
                if (list.isEmpty()) {
                    Msg.send(sender, "&7$playerName 没有警告记录")
                    return@execute
                }
                Msg.send(sender, "&7$playerName 的警告（共 ${list.size} 条）：")
                list.forEachIndexed { i, r ->
                    Msg.raw(sender, "  &8${i + 1}. &7${r.reason} &8| &7${r.operator} &8| &7${r.time}")
                }
            }
        }
    }

    // ══ 状态 / 历史 ═══════════════════════════════════

    private fun CommandComponent.statusBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                val playerName = ctx["player"]
                val data = runCatching {
                    BanDatabaseManager.getDatabase().getPlayerByName(playerName.playerID())
                }.getOrNull()
                if (data == null) {
                    Msg.send(sender, "&c查询失败，请查看控制台日志")
                    return@execute
                }
                Msg.send(sender, "&7──── &e$playerName &7────")
                Msg.send(sender, "&7封禁状态：${if (data.isBanned) "&c已封禁" else "&a正常"}")
                if (data.isBanned) {
                    Msg.send(sender, "&7  原因：&f${data.banReason}")
                    Msg.send(sender, "&7  时长：&f${data.banDuration}")
                    Msg.send(sender, "&7  时间：&f${data.banTime}")
                    Msg.send(sender, "&7  解封：&f${data.unbanTime.ifBlank { "永久" }}")
                    Msg.send(sender, "&7  操作：&f${data.banningAdmin}")
                }
                Msg.send(sender, "&7白名单：${if (data.isWhitelisted) "&a是" else "&c否"}")
                Msg.send(sender, "&7警告数：&e${BanApi.getPlayerWarningCount(playerName.playerID())}")
            }
        }
    }

    private fun CommandComponent.historyBody() {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, ctx, _ ->
                val playerName = ctx["player"]
                val records = BanApi.getPlayerHistory(playerName.playerID(), 20)
                if (records.isEmpty()) {
                    Msg.send(sender, "&7$playerName 没有操作记录")
                    return@execute
                }
                Msg.send(sender, "&7$playerName 的操作记录（最近 ${records.size} 条）：")
                records.forEach { r ->
                    Msg.raw(sender, "  &8[&7${r.time}&8] &e${actionText(r.action)} &7${r.reason} &8| &7${r.operator}")
                }
            }
        }
    }

    private fun actionText(action: String): String = when (action) {
        "BAN" -> "&c封禁"
        "UNBAN" -> "&a解封"
        "KICK" -> "&e请出"
        "WHITELIST" -> "&a加白名单"
        "UNWHITELIST" -> "&c移白名单"
        "WARN" -> "&6警告"
        else -> "&7其他"
    }

    /** 按配置的 Player-ID 策略取主键（BanMessages 的成员扩展，需 import 后才能点调用） */
    private fun String.playerID(): String = this.getPlayerID()
