package com.indra.rpg.ui.menu

import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuManager

import com.indra.rpg.ban.BanDatabaseManager
import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.module.ModuleManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.module.ui.ClickEvent
import taboolib.platform.util.bukkitPlugin

/**
 * 主菜单 —— 所有面板的入口。
 *
 * 布局（6 行 × 9 列）：
 * ```
 *   0  1  2  3  4  5  6  7  8
 *  ┌──────────────────────────┐
 *  │        [ 玩家信息 ]       │  玩家自己的等级 / 经验 / 统计
 *  │ [等级排行] [封禁管理] [管理]│  分别需要不同权限
 *  │        [ 信标设置 ]       │  A4 未开发 → 灰色占位
 *  └──────────────────────────┘
 * ```
 *
 * ══ 权限设计 ═════════════════════════════════════════════
 *   主菜单本身**不需要权限**（任何玩家都能看自己的信息、看排行榜）。
 *   需要权限的入口在这里做**视觉降级**：无权时图标变灰色 + 提示，
 *   而不是直接隐藏 —— 隐藏会让玩家以为服务器没有这个功能。
 *   真正的权限拦截在各自面板的 [AbstractMenu.permission] 里（双保险，
 *   因为玩家可能通过 `/indra menu ban` 直接跳进来）。
 */
class MainMenu(player: Player) : AbstractMenu(player) {

    override val menuId: String = MenuManager.ROOT_ID
    override val defaultTitle: String = "&8Indra 主菜单"
    override val defaultRows: Int = 6

    override fun MenuCtx.render() {
        // ══ 玩家信息（第 2 行第 5 格）══════════════════════
        val selfSlot = MenuConfig.int(menuId, "buttons.player.slot", 13)
        val online = Bukkit.getOnlinePlayers().size
        put(
            selfSlot, "nav.player",
            MenuIcons.icon(
                MenuConfig.str(menuId, "buttons.player.material", "PLAYER_HEAD"),
                MenuConfig.str(menuId, "buttons.player.name", "&b我的信息", player),
                MenuConfig.list(
                    menuId, "buttons.player.lore",
                    listOf(
                        "&7等级：&eLv.{level}",
                        "&7经验：&e{exp} &8/ &7{expNext}",
                        "&7击杀 / 死亡：&a{kills} &8/ &c{deaths}",
                        "&7可用点数：&e{points}",
                        "",
                        "&8点击查看详情"
                    ),
                    player
                ).map { fillSelf(it) },
                where = "$menuId.buttons.player"
            )
        )

        // ══ 等级排行（第 4 行第 2 格）══════════════════════
        val topSlot = MenuConfig.int(menuId, "buttons.top.slot", 28)
        put(
            topSlot, "nav.top",
            MenuIcons.icon(
                MenuConfig.str(menuId, "buttons.top.material", "GOLDEN_HELMET"),
                MenuConfig.str(menuId, "buttons.top.name", "&6等级排行榜", player),
                MenuConfig.list(
                    menuId, "buttons.top.lore",
                    listOf("&7查看服务器等级前 50 名", "&7支持升序 / 降序切换", "", "&8点击查看"),
                    player
                ),
                where = "$menuId.buttons.top"
            )
        )

        // ══ 封禁管理（第 4 行第 4 格）—— 需要权限 ═════════
        val banSlot = MenuConfig.int(menuId, "buttons.ban.slot", 30)
        val banPerm = "indra.command.ban"
        val canBan = player.hasPermission(banPerm)
        put(
            banSlot, if (canBan) "nav.ban" else null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "buttons.ban.material", "IRON_DOOR"),
                MenuConfig.str(menuId, "buttons.ban.name", "&c封禁管理", player),
                if (canBan) MenuConfig.list(
                    menuId, "buttons.ban.lore",
                    listOf("&7封禁名单 / 解封 / 警告", "&7支持搜索与排序", "", "&8点击管理"),
                    player
                ) else listOf("&c需要权限：&f$banPerm"),
                where = "$menuId.buttons.ban"
            )
        )

        // ══ 管理工具（第 4 行第 6 格）—— 需要权限 ═════════
        val adminSlot = MenuConfig.int(menuId, "buttons.admin.slot", 32)
        val adminPerm = "indra.admin"
        val canAdmin = player.hasPermission(adminPerm)
        put(
            adminSlot, if (canAdmin) "nav.admin" else null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "buttons.admin.material", "COMMAND_BLOCK"),
                MenuConfig.str(menuId, "buttons.admin.name", "&e管理工具", player),
                if (canAdmin) MenuConfig.list(
                    menuId, "buttons.admin.lore",
                    listOf("&7运行状态 / 模块列表", "&7存储信息 / 手动重载", "", "&8点击查看"),
                    player
                ) else listOf("&c需要权限：&f$adminPerm"),
                where = "$menuId.buttons.admin"
            )
        )

        // ══ 信标设置（第 4 行第 8 格）—— A4 未开发 ═════════
        //   ⚠️ 故意显示为**灰色不可点**而不是隐藏：
        //      服主装完插件看到的应该是「有这个功能但还没做」，
        //      而不是「功能列表里有信标但菜单里找不到」。
        val beaconSlot = MenuConfig.int(menuId, "buttons.beacon.slot", 34)
        put(
            beaconSlot, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "buttons.beacon.material", "BEACON"),
                MenuConfig.str(menuId, "buttons.beacon.name", "&7信标设置 &8(未启用)", player),
                MenuConfig.list(
                    menuId, "buttons.beacon.lore",
                    listOf("&8功能开发中（见 docs/功能清单.md A4）", "&8当前版本暂不可用"),
                    player
                ),
                where = "$menuId.buttons.beacon"
            )
        )

        // ══ 顶部信息条（第 1 行）══════════════════════════
        val infoSlot = MenuConfig.int(menuId, "info-slot", 4)
        put(
            infoSlot, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "info-material", "NETHER_STAR"),
                MenuConfig.str(menuId, "info-name", "&b&lIndra &7RPG 系统", player),
                listOf(
                    "&7版本：&f${taboolib.platform.util.bukkitPlugin.pluginMeta.version}",
                    "&7存储：&f${if (DataManager.isMySQL()) "MySQL" else "本地 SQLite"}",
                    "&7模块：&f${ModuleManager.modules.size} &7个",
                    "&7在线：&f$online &7人"
                ),
                where = "$menuId.info"
            )
        )
    }

    override fun onAction(event: ClickEvent, key: String) {
        when (key) {
            "nav.player" -> MenuManager.open(event.clicker, "player")
            "nav.top" -> MenuManager.open(event.clicker, "top")
            "nav.ban" -> MenuManager.open(event.clicker, "ban")
            "nav.admin" -> MenuManager.open(event.clicker, "admin")
            // back 不会出现在主菜单（AbstractMenu 对 ROOT_ID 不画返回键），
            // 但保留分支以防服主手动配了返回键槽位
            "back" -> event.clicker.closeInventory()
        }
    }

    /** 把主菜单预览里的 {level} 等占位符换成真实数值 */
    private fun fillSelf(line: String): String {
        val data = DataManager.get(player) ?: return line
        return line
            .replace("{level}", data.level.toString())
            .replace("{exp}", data.exp.toString())
            .replace("{expNext}", data.expToNextLevel().toString())
            .replace("{kills}", data.killCount.toString())
            .replace("{deaths}", data.deathCount.toString())
            .replace("{points}", data.points.toString())
            .replace("{maxLevel}", IndraConfig.maxLevel.toString())
    }
}
