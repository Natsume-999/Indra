package com.indra.rpg.ui.menu

import com.indra.rpg.ban.BanApi
import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.data.IndraPlayerData
import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuManager
import com.indra.rpg.ui.MenuState
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.module.ui.ClickEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 玩家信息面板 —— 查看一位玩家的完整数据（等级 / 经验 / 统计 / 封禁 / 警告）。
 *
 * ══ 能看谁 ══════════════════════════════════════════════
 *   默认看自己；管理员用 `/indra panel player` 看别人
 *   （在指令层把 target 换成目标玩家）。
 *
 *   ⚠️ 「看谁」是**构造参数**而不是会话状态：
 *      它是「本次打开」的参数，不该跨刷新存活 —— 否则玩家关掉面板
 *      再打开时会莫名其妙又跳回上次看的人。
 *
 * ══ 数据来源 ════════════════════════════════════════════
 *   在线玩家 → [DataManager] 的内存缓存（零延迟）
 *   离线玩家 → 查不到缓存，此时等级类字段显示占位符，
 *              但封禁 / 警告信息仍可查（那部分走 BanDatabase，与在线无关）
 */
class PlayerInfoMenu(
    player: Player,
    private val target: Player = player,
) : AbstractMenu(player) {

    override val menuId: String = "player"
    override val defaultTitle: String = "&8玩家信息 &7- &f{player}"
    override val defaultRows: Int = 5

    /** 自己的信息人人可看；看别人由指令层的权限拦住 */
    override val permission: String? = null

    /** 目标玩家的数据（离线时为 null） */
    private val data: IndraPlayerData? = runCatching {
        if (Bukkit.getPlayer(target.uniqueId) != null) DataManager.get(target) ?: DataManager.load(target)
        else null
    }.getOrNull()

    override fun MenuCtx.render() {
        // ══ 头颅：整张面板的视觉中心 ═══════════════════════
        put(
            13, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "head.material", "PLAYER_HEAD"),
                MenuConfig.str(menuId, "head.name", "&b{player}", target),
                buildLore(),
                where = "$menuId.head"
            )
        )

        // ══ 进度条 ═════════════════════════════════════════
        val pct = ((data?.progress() ?: 0.0) * 100).toInt()
        put(
            21, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "progress.material", "EXPERIENCE_BOTTLE"),
                MenuConfig.str(menuId, "progress.name", "&e升级进度 &f$pct%", target),
                listOf(
                    bar(pct),
                    "&7当前：&e${data?.exp ?: 0} &8/ &7${data?.expToNextLevel() ?: 0}",
                    "&7累计总经验：&e${data?.expTotal ?: 0}"
                ),
                where = "$menuId.progress"
            )
        )

        // ══ 封禁 / 警告状态 ════════════════════════════════
        val banned = runCatching { BanApi.isBanned(target.name) }.getOrDefault(false)
        val warns = runCatching { BanApi.getPlayerWarningCount(target.name) }.getOrDefault(0)
        put(
            23, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, if (banned) "ban.banned-material" else "ban.normal-material",
                    if (banned) "REDSTONE_BLOCK" else "EMERALD_BLOCK"),
                MenuConfig.str(menuId, if (banned) "ban.banned-name" else "ban.normal-name",
                    if (banned) "&c已封禁" else "&a状态正常", target),
                listOf(
                    "&7警告次数：&e$warns",
                    if (banned) "&7在封禁管理面板可查看详情" else "&7无封禁记录"
                ),
                where = "$menuId.ban"
            )
        )

        // ══ 管理员：一键跳到封禁管理（预置搜索词）═══════════
        if (player.hasPermission("indra.command.ban")) {
            put(
                31, "act.banview",
                MenuIcons.icon(
                    MenuConfig.str(menuId, "banview.material", "IRON_DOOR"),
                    MenuConfig.str(menuId, "banview.name", "&c在封禁管理中查看", target),
                    listOf("&7打开封禁面板并自动搜索 &f${target.name}"),
                    where = "$menuId.banview"
                )
            )
        }

        // ══ 离线提示 ═══════════════════════════════════════
        if (data == null) {
            put(
                25, null,
                MenuIcons.icon(
                    "BARRIER",
                    MenuConfig.str(menuId, "offline.name", "&7玩家离线", target),
                    listOf("&8等级 / 经验数据未缓存，无法显示", "&8封禁与警告信息仍可查看"),
                    where = "$menuId.offline"
                )
            )
        }
    }

    override fun onAction(event: ClickEvent, key: String) {
        when (key) {
            "act.banview" -> {
                // 预置搜索词后打开封禁面板 —— 复用封禁面板自己的搜索通道，
                // 不另做一套「按玩家过滤」，避免两套过滤逻辑行为不一致
                val s = MenuState.get(event.clicker.uniqueId, "ban")
                s.keyword = target.name
                s.page = 1
                MenuManager.open(event.clicker, "ban")
            }
            "back" -> MenuManager.openRoot(event.clicker)
        }
    }

    /** 构建头颅的描述文本 */
    private fun buildLore(): List<String> {
        val online = Bukkit.getPlayer(target.uniqueId) != null
        val uuid = target.uniqueId.toString()
        return MenuConfig.list(
            menuId, "head.lore",
            listOf(
                "&7状态：{status}",
                "&7等级：&eLv.{level} &8/ &7{maxLevel}",
                "&7经验：&e{exp} &8/ &7{expNext}",
                "&7总经验：&e{expTotal}",
                "&7击杀数：&a{kills}",
                "&7死亡数：&c{deaths}",
                "&7可用点数：&e{points}",
                "&7首次进入：&f{firstJoin}",
                "&7最后在线：&f{lastSeen}",
                "",
                "&8UUID: &7{uuid}"
            ),
            target
        ).map {
            it.replace("{status}", if (online) "&a在线" else "&7离线")
                .replace("{level}", (data?.level ?: 0).toString())
                .replace("{maxLevel}", IndraConfig.maxLevel.toString())
                .replace("{exp}", (data?.exp ?: 0).toString())
                .replace("{expNext}", (data?.expToNextLevel() ?: 0).toString())
                .replace("{expTotal}", (data?.expTotal ?: 0).toString())
                .replace("{kills}", (data?.killCount ?: 0).toString())
                .replace("{deaths}", (data?.deathCount ?: 0).toString())
                .replace("{points}", (data?.points ?: 0).toString())
                .replace("{firstJoin}", fmtTime(data?.firstJoin ?: 0L))
                .replace("{lastSeen}", fmtTime(data?.lastSeen ?: 0L))
                .replace("{uuid}", uuid)
                .replace("{player}", target.name)
        }
    }

    /** 用 ▉ / ░ 拼一条 20 格宽的进度条 */
    private fun bar(pct: Int): String {
        val filled = pct.coerceIn(0, 100) / 5
        val color = when {
            pct >= 90 -> "&a"
            pct >= 50 -> "&e"
            else -> "&6"
        }
        return "$color" + "▉".repeat(filled) + "&8" + "▉".repeat(20 - filled)
    }

    private fun fmtTime(millis: Long): String =
        if (millis <= 0) "&7未知"
        else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
