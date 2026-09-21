package com.indra.rpg.command

import com.indra.rpg.ban.banChildren
import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.mob.MobLevelModule
import com.indra.rpg.mob.MobSpawnModule
import com.indra.rpg.module.IndraModule
import com.indra.rpg.module.ModuleManager
import com.indra.rpg.player.PlayerModule
import com.indra.rpg.sell.sellChildren
import com.indra.rpg.ui.menuChildren
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.command
import taboolib.common.platform.command.int
import taboolib.platform.util.bukkitPlugin

/**
 * 指令模块 —— /indra 主指令。
 *
 * ── TabooLib 6.3 指令 API（已按真实签名重写）────────────────────────
 *   入口：`mainCommand { ... }`，lambda 接收者是 `CommandBase`
 *   字面量：`literal("status") { ... }`
 *   动态参数：`dynamic("player") { suggestion<CommandSender> { ... } }`
 *   执行体：`execute<T> { sender, ctx, args -> ... }`（Function3）
 *   取参：`ctx.int("amount")`（CommandContext 的 int 扩展）
 *   ⚠️ 旧的 `command(...)` / `subCommand("x") { }` 写法在 6.3 已不存在。
 *
 * 权限节点：
 *   indra.admin  → 全部管理指令
 *   indra.player → 玩家自助指令（查看等级、排行）
 */
object CommandModule : IndraModule {

    override val name = "Command"

    override fun onEnable() {
        registerCommands()
    }

    override fun onDisable() = Unit

    private fun registerCommands() {
        // ⚠️ TabooLib 6.3：指令名 / 别名 / 权限全部是 `command(...)` 的**函数参数**，
        //    CommandBase 上没有可写的 name/aliases/description 属性（只有 optional 可写）。
        //    参数顺序（已从 Kotlin 元数据核实）：
        //      name, aliases, description, usage, permission, permissionMessage, ...
        command(
            name = "indra",
            aliases = listOf("ind"),
            description = "Indra RPG 主指令",
            permission = "indra.admin"
        ) {
            // ── /indra status ─────────────────────────────
            literal("status") {
                execute<CommandSender> { sender, _, _ ->
                    Msg.send(sender, "&7插件版本：&e${bukkitPlugin.pluginMeta.version}")
                    Msg.send(sender, "&7存储模式：&e${if (DataManager.isMySQL()) "MySQL" else "本地 SQLite"}")
                    Msg.send(sender, "&7已启用模块：&e${ModuleManager.modules.joinToString(", ") { it.name }}")
                    Msg.send(sender, "&7禁原版生成：&e${IndraConfig.disableVanillaSpawn}")
                    Msg.send(sender, "&7在线缓存：&e${Bukkit.getOnlinePlayers().size} 人")
                }
            }

            // ── /indra reload ─────────────────────────────
            literal("reload") {
                execute<CommandSender> { sender, _, _ ->
                    IndraConfig.load()
                    MobSpawnModule.refreshCache()
                    Msg.send(sender, "&a配置已重载。")
                }
            }

            // ── /indra modules ────────────────────────────
            literal("modules") {
                execute<CommandSender> { sender, _, _ ->
                    Msg.send(sender, "&7已加载模块（${ModuleManager.modules.size}）：")
                    ModuleManager.modules.forEach {
                        Msg.raw(sender, "  &8- &a${it.name}")
                    }
                }
            }

            // ── /indra level [玩家] ───────────────────────
            literal("level") {
                // 无参 → 看自己
                execute<CommandSender> { sender, _, _ ->
                    val player = sender as? Player
                    if (player == null) {
                        Msg.send(sender, "&c控制台请指定玩家名：/indra level <玩家>")
                        return@execute
                    }
                    showLevel(sender, player)
                }
                dynamic("player") {
                    suggestion<CommandSender> { _, _ ->
                        Bukkit.getOnlinePlayers().map { it.name }
                    }
                    execute<CommandSender> { sender, ctx, _ ->
                        val target = Bukkit.getPlayerExact(ctx["player"])
                        if (target == null) {
                            Msg.send(sender, "&c玩家不在线：${ctx["player"]}")
                            return@execute
                        }
                        showLevel(sender, target)
                    }
                }
            }

            // ── /indra exp add <玩家> <数量> ──────────────
            literal("exp") {
                literal("add") {
                    dynamic("player") {
                        suggestion<CommandSender> { _, _ ->
                            Bukkit.getOnlinePlayers().map { it.name }
                        }
                        dynamic("amount") {
                            suggestion<CommandSender> { _, _ -> listOf("100", "500", "1000") }
                            execute<CommandSender> { sender, ctx, _ ->
                                val target = Bukkit.getPlayerExact(ctx["player"])
                                if (target == null) {
                                    Msg.send(sender, "&c玩家不在线：${ctx["player"]}")
                                    return@execute
                                }
                                val amount = ctx.int("amount")
                                if (amount <= 0) {
                                    Msg.send(sender, "&c数量必须是正整数。")
                                    return@execute
                                }
                                PlayerModule.giveExp(target, amount)
                                Msg.send(sender, "&a已为 &e${target.name}&a 增加 &e$amount &a点经验。")
                            }
                        }
                    }
                }
            }

            // ── /indra top [数量] ─────────────────────────
            literal("top") {
                execute<CommandSender> { sender, _, _ -> showTop(sender, 10) }
                dynamic("size") {
                    suggestion<CommandSender> { _, _ -> listOf("5", "10", "20") }
                    execute<CommandSender> { sender, ctx, _ ->
                        showTop(sender, ctx.int("size"))
                    }
                }
            }

            // ── /indra mob level ──────────────────────────
            literal("mob") {
                literal("level") {
                    execute<Player> { player, _, _ ->
                        val target = player.getTargetEntity(8) as? org.bukkit.entity.LivingEntity
                        if (target == null) {
                            Msg.send(player, "&c请将准星对准一个生物（8 格内）。")
                            return@execute
                        }
                        val level = MobLevelModule.getLevel(target)
                        Msg.send(player, "&7目标：&e${target.type.name}&7 等级：&eLv.$level")
                    }
                }
            }

            // ── 封禁指令集（/indra ban ... 等）────────────
            //    banChildren() 是 CommandBase 上的顶层扩展，直接调用即可
            banChildren()

            // ── 出售指令集（/indra sell ...）──────────────
            sellChildren()

            // ── 面板指令集（/indra menu /panel /banlist）──
            menuChildren()

            // ── 无参：帮助 ────────────────────────────────
            execute<CommandSender> { sender, _, _ ->
                Msg.raw(sender, "&b&lIndra &7- RPG 基础功能")
                Msg.raw(sender, "  &e/indra status &8- &7查看运行状态")
                Msg.raw(sender, "  &e/indra reload &8- &7重载配置")
                Msg.raw(sender, "  &e/indra modules &8- &7模块列表")
                Msg.raw(sender, "  &e/indra level [玩家] &8- &7查看等级")
                Msg.raw(sender, "  &e/indra exp add <玩家> <数量> &8- &7增加经验")
                Msg.raw(sender, "  &e/indra top [数量] &8- &7等级排行")
                Msg.raw(sender, "  &e/indra mob level &8- &7查看怪物等级")
                Msg.raw(sender, "  &b── 封禁 ──")
                Msg.raw(sender, "  &e/indra ban <玩家> [时长] [原因] &8- &7封禁")
                Msg.raw(sender, "  &e/indra unban <玩家> &8- &7解封")
                Msg.raw(sender, "  &e/indra kick <玩家> [原因] &8- &7请出")
                Msg.raw(sender, "  &e/indra warn <玩家> [原因] &8- &7警告")
                Msg.raw(sender, "  &e/indra warnings <玩家> &8- &7警告列表")
                Msg.raw(sender, "  &e/indra banstatus <玩家> &8- &7封禁状态")
                Msg.raw(sender, "  &e/indra banhistory <玩家> &8- &7操作历史")
                Msg.raw(sender, "  &b── 出售 ──")
                Msg.raw(sender, "  &e/indra sell [界面] &8- &7打开出售界面")
                Msg.raw(sender, "  &e/indra sell open <玩家> [界面] &8- &7为他人打开")
                Msg.raw(sender, "  &e/indra sell list &8- &7列出界面与规则数")
                Msg.raw(sender, "  &e/indra sell reload &8- &7重载规则与界面")
                Msg.raw(sender, "  &e/indra sell log <玩家> [条数] &8- &7成交记录")
                Msg.raw(sender, "  &b── 面板 ──")
                Msg.raw(sender, "  &e/indra menu [面板] &8- &7打开图形面板")
                Msg.raw(sender, "  &e/indra panel <面板> [玩家] &8- &7打开指定面板")
                Msg.raw(sender, "  &e/indra banlist &8- &7快捷打开封禁管理面板")
                Msg.raw(sender, "  &7可用面板：&f${com.indra.rpg.ui.MenuManager.ids().joinToString(", ")}")
            }
        }
    }

    private fun showLevel(sender: CommandSender, player: Player) {
        val data = DataManager.get(player) ?: DataManager.load(player)
        Msg.raw(sender, "&b${player.name} &7的等级信息：")
        Msg.raw(sender, "  &7等级：&eLv.${data.level} &8/ &7${IndraConfig.maxLevel}")
        Msg.raw(sender, "  &7经验：&e${data.exp} &8/ &7${data.expToNextLevel()} &8(${(data.progress() * 100).toInt()}%)")
        Msg.raw(sender, "  &7总经验：&e${data.expTotal}")
        Msg.raw(sender, "  &7击杀 / 死亡：&a${data.killCount} &8/ &c${data.deathCount}")
        Msg.raw(sender, "  &7可用点数：&e${data.points}")
    }

    private fun showTop(sender: CommandSender, size: Int) {
        val list = DataManager.topLevel(size.coerceIn(1, 50))
        if (list.isEmpty()) {
            Msg.send(sender, "&7暂无数据。")
            return
        }
        Msg.raw(sender, "&b&l等级排行榜 &7(Top ${list.size})")
        list.forEachIndexed { i, data ->
            val medal = when (i) {
                0 -> "&6①"; 1 -> "&7②"; 2 -> "&c③"
                else -> "&8${i + 1}."
            }
            Msg.raw(sender, "  $medal &f${data.name} &8- &eLv.${data.level} &8(&7${data.expTotal} exp&8)")
        }
    }
}
