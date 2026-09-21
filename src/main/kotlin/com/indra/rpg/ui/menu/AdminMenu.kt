package com.indra.rpg.ui.menu

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.module.ModuleManager
import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuManager
import com.indra.rpg.ui.MenuState
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.util.bukkitPlugin
import taboolib.module.ui.ClickEvent

/**
 * 管理工具面板 —— 服务器状态、模块列表、存储信息、手动运维。
 *
 * 需要 `indra.admin` 权限。
 *
 * ══ 面板化 /indra status + /indra modules ═══════════════
 *   指令版是「打印一屏文本」，面板版是「一屏看完 + 一键操作」。
 *   两者并存：控制台只能用指令，玩家在游戏里用面板更直观。
 *   **数据来源是同一个**（[ModuleManager] / [DataManager] / [IndraConfig]），
 *   不存在两份真相。
 */
class AdminMenu(player: Player) : AbstractMenu(player) {

    override val menuId: String = "admin"
    override val defaultTitle: String = "&8管理工具"
    override val defaultRows: Int = 5
    override val permission: String? = "indra.admin"

    override fun MenuCtx.render() {
        val online = Bukkit.getOnlinePlayers().size
        val max = Bukkit.getMaxPlayers()

        // ══ 运行状态 ═══════════════════════════════════════
        put(
            4, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "status.material", "NETHER_STAR"),
                MenuConfig.str(menuId, "status.name", "&b&l服务器状态", player),
                listOf(
                    "&7插件版本：&f${bukkitPlugin.pluginMeta.version}",
                    "&7在线人数：&f$online &7/ &f$max",
                    "&7存储模式：&f${if (DataManager.isMySQL()) "MySQL" else "本地 SQLite"}",
                    "&7已启用模块：&f${ModuleManager.modules.size} &7个"
                ),
                where = "$menuId.status"
            )
        )

        // ══ 模块列表 ═══════════════════════════════════════
        val moduleLore = buildList {
            add("&7共 ${ModuleManager.modules.size} 个模块：")
            ModuleManager.modules.forEach { add("  &8- &a${it.name}") }
            add("")
            add("&8模块由 ModuleManager 统一注册")
        }
        put(
            19, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "modules.material", "BOOKSHELF"),
                MenuConfig.str(menuId, "modules.name", "&a已加载模块", player),
                moduleLore,
                where = "$menuId.modules"
            )
        )

        // ══ 玩法参数 ═══════════════════════════════════════
        put(
            21, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "params.material", "COMPARATOR"),
                MenuConfig.str(menuId, "params.name", "&e核心参数", player),
                listOf(
                    "&7等级上限：&f${IndraConfig.maxLevel}",
                    "&7每级经验基数：&f${IndraConfig.expPerLevel}",
                    "&7每级点数：&f${IndraConfig.pointsPerLevel}",
                    "&7击杀经验系数：&f${IndraConfig.expPerMobLevel}",
                    "&7禁止原版生成：&f${IndraConfig.disableVanillaSpawn}",
                    "&7死亡惩罚：&f${IndraConfig.enableDeathPenalty}",
                    "&8（修改请编辑 config.yml）"
                ),
                where = "$menuId.params"
            )
        )

        // ══ 重载配置 ═══════════════════════════════════════
        put(
            29, "act.reload",
            MenuIcons.icon(
                MenuConfig.str(menuId, "reload.material", "REDSTONE_TORCH"),
                MenuConfig.str(menuId, "reload.name", "&e重载配置", player),
                listOf(
                    "&7重新读取 config.yml / menu.yml",
                    "&7并刷新怪物生成缓存",
                    "",
                    "&8等价于 /indra reload"
                ),
                where = "$menuId.reload"
            )
        )

        // ══ 面板状态自检 ═══════════════════════════════════
        val sessions = MenuManager.ids().count { MenuState.has(player.uniqueId, it) }
        put(
            31, "act.diag",
            MenuIcons.icon(
                MenuConfig.str(menuId, "diag.material", "SPYGLASS"),
                MenuConfig.str(menuId, "diag.name", "&b面板自检", player),
                listOf(
                    "&7可用面板：&f${MenuManager.ids().joinToString(", ")}",
                    "&7你的活动会话：&f$sessions",
                    "",
                    "&8点击输出诊断信息到聊天栏"
                ),
                where = "$menuId.diag"
            )
        )

        // ══ 玩家数据榜单预览 ═══════════════════════════════
        put(
            23, "act.top",
            MenuIcons.icon(
                MenuConfig.str(menuId, "top.material", "GOLDEN_HELMET"),
                MenuConfig.str(menuId, "top.name", "&6打开排行榜", player),
                listOf("&7跳转到等级排行榜"),
                where = "$menuId.top"
            )
        )
    }

    override fun onAction(event: ClickEvent, key: String) {
        when (key) {
            "act.reload" -> {
                runCatching {
                    IndraConfig.load()
                    com.indra.rpg.mob.MobSpawnModule.refreshCache()
                }
                Msg.send(event.clicker, "&a配置已重载。")
                refresh()
            }
            "act.diag" -> {
                val p = event.clicker
                Msg.raw(p, "&b──── Indra 面板自检 ────")
                Msg.raw(p, "&7插件版本：&f${bukkitPlugin.pluginMeta.version}")
                Msg.raw(p, "&7可用面板：&f${MenuManager.ids().joinToString(", ")}")
                Msg.raw(p, "&7各面板启用状态：")
                MenuManager.ids().forEach { id ->
                    val on = MenuConfig.bool(id, "enabled", true)
                    Msg.raw(p, "  &8- &f$id &8(&f${MenuManager.label(id)}&8) → ${if (on) "&a启用" else "&c禁用"}")
                }
                Msg.raw(p, "&7menu.yml 已加载：&f${MenuConfig.conf.getKeys(false).contains("menus")}")
            }
            "act.top" -> MenuManager.open(event.clicker, "top")
            "back" -> MenuManager.openRoot(event.clicker)
        }
    }
}
