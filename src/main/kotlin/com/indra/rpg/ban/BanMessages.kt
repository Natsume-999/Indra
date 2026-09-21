package com.indra.rpg.ban

import com.indra.rpg.common.util.TimeUtil.formatToString
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.util.replaceWithOrder
import taboolib.module.chat.colored
import java.time.LocalDateTime

/**
 * 封禁消息渲染。
 *
 * 消息模板用 `{0} {1} ...` 占位，由 TabooLib 的 `replaceWithOrder` 按顺序填充，
 * 再用 `colored()` 把 `&` 颜色码转成组件。
 *
 * 占位顺序（与 ban-messages.yml 对应）：
 *   {0} 玩家名 / {1} UUID / {2} 原因 / {3} 操作管理员 / {4} 时间 / {5} 解封时间 / {6} 时长
 */
@Suppress("DEPRECATION")
object BanMessages {

    fun getKickFormat(
        player: Player,
        kickReason: String = "",
        kickTime: String = "",
        kickingAdmin: String = ""
    ): String = BanConfig.getKickFormat().joinToString(separator = "")
        .replaceWithOrder(player.name, player.uniqueId, kickReason, kickingAdmin, kickTime)
        .colored()

    fun getWhitelistFormat(player: OfflinePlayer): String =
        BanConfig.getWhitelistFormat().joinToString(separator = "")
            .replaceWithOrder(player.name ?: "", player.uniqueId)
            .colored()

    fun getBanFormat(
        player: Player,
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        unbanTime: String = "",
        banningAdmin: String = ""
    ): String = BanConfig.getBanFormat().joinToString(separator = "")
        .replaceWithOrder(
            player.name, player.uniqueId, banReason, banningAdmin, banTime, unbanTime, banDuration
        ).colored()

    fun getBanFormat(
        player: OfflinePlayer,
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        unbanTime: String = "",
        banningAdmin: String = ""
    ): String = BanConfig.getBanFormat().joinToString(separator = "")
        .replaceWithOrder(
            player.name ?: "", player.uniqueId, banReason, banningAdmin, banTime, unbanTime, banDuration
        ).colored()

    /** 警告消息：{0} 玩家名 {1} UUID {2} 原因 {3} 操作管理员 {4} 时间 */
    fun getWarnFormat(
        player: Player,
        warnReason: String = "",
        warnTime: String = "",
        warningAdmin: String = ""
    ): String = BanConfig.getWarnFormat().joinToString(separator = "")
        .replaceWithOrder(player.name, player.uniqueId, warnReason, warningAdmin, warnTime)
        .colored()

    fun getTimeFormat(): String = BanConfig.getTimeFormat().getString("Time") ?: "yyyy-MM-dd HH:mm:ss"

    /** 当前时间文本（按配置的格式） */
    fun nowText(): String = LocalDateTime.now().formatToString(getTimeFormat())

    // ── 玩家主键解析 ────────────────────────────────────

    fun Player.getPlayerID(): String =
        if (BanConfig.getPlayerID().equals("name", true)) this.name else this.uniqueId.toString()

    fun OfflinePlayer.getPlayerID(): String? =
        if (BanConfig.getPlayerID().equals("name", true)) this.name else this.uniqueId.toString()

    fun String.getPlayerID(): String = Bukkit.getOfflinePlayer(this).getPlayerID() ?: this

    fun String.isPlayerOnline(): Boolean = Bukkit.getOfflinePlayer(this).isOnline
}
