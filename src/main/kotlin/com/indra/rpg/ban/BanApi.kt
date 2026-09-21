package com.indra.rpg.ban

import com.indra.rpg.ban.BanMessages.getPlayerID
import com.indra.rpg.ban.BanMessages.nowText
import com.indra.rpg.common.util.TimeUtil.formatToLocalDateTime
import com.indra.rpg.ban.event.OfflinePlayerBanEvent
import com.indra.rpg.ban.event.OnlinePlayerBanEvent
import com.indra.rpg.ban.event.PlayerKickEvent
import com.indra.rpg.ban.event.PlayerUnbanEvent
import com.indra.rpg.ban.event.PlayerWhitelistEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import taboolib.platform.type.BukkitProxyEvent
import java.time.LocalDateTime

/**
 * 把 § 色码字符串转成 Adventure `Component`。
 *
 * ⚠️ Paper 26.3 弃用了 `Player.kickPlayer(String)`，替代是 `kick(Component)`。
 *    封禁文案允许服主自定义，统一走 legacy 序列化器（兼容 § 与十六进制色）。
 */
private fun comp(text: String): Component =
    LegacyComponentSerializer.legacySection().deserialize(text)

/**
 * 封禁对外 API。
 *
 * 所有写操作都先抛出对应的 BukkitProxyEvent，被取消则不执行，
 * 方便其他插件挂钩扩展（例如把封禁同步到 Discord / 跨服）。
 *
 * 移植自 Phoenix 的 ban/BanApi.kt（v1.16.0），
 * 补齐了 Phoenix 缺失的一环：警告累计到阈值后自动封禁
 * （Phoenix 只把警告入库，阈值配置存在但没有触发点）。
 */
object BanApi {

    private inline fun <reified T : BukkitProxyEvent> callPluginEvent(event: T, action: T.() -> Unit) {
        event.call()
        if (event.isCancelled) return
        action(event)
    }

    private fun recordHistory(
        playerID: String,
        action: String,
        reason: String = "",
        duration: String = "",
        operator: String = ""
    ) {
        runCatching {
            BanDatabaseManager.getDatabase().addHistory(
                playerID = playerID,
                action = action,
                reason = reason,
                duration = duration,
                operator = operator,
                time = nowText()
            )
        }
    }

    // ══ 踢出 ══════════════════════════════════════

    private fun kick(
        player: Player,
        kickReason: String = "",
        kickTime: String = "",
        kickingAdmin: String = ""
    ) {
        callPluginEvent(PlayerKickEvent(player, kickReason, kickTime, kickingAdmin)) {
            this.player.kick(
                comp(BanMessages.getKickFormat(this.player, this.kickReason, this.kickTime, this.kickingAdmin))
            )
            recordHistory(this.player.getPlayerID(), "KICK", this.kickReason, operator = this.kickingAdmin)
        }
    }

    // ══ 白名单 ════════════════════════════════════

    private fun whitelist(
        playerID: String,
        isWhitelisted: Boolean = false,
        whitelistTime: String = "",
        whitelistingAdmin: String = ""
    ) {
        callPluginEvent(PlayerWhitelistEvent(playerID, isWhitelisted, whitelistTime, whitelistingAdmin)) {
            setPlayerWhitelistData(
                playerID = this.playerID,
                isWhitelisted = this.isWhitelisted,
                whitelistTime = this.whitelistTime,
                whitelistingAdmin = this.whitelistingAdmin
            )
            recordHistory(
                this.playerID,
                if (this.isWhitelisted) "WHITELIST" else "UNWHITELIST",
                operator = this.whitelistingAdmin
            )
        }
    }

    // ══ 封禁 ══════════════════════════════════════

    private fun onlineBan(
        player: Player,
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        banningAdmin: String = ""
    ) {
        callPluginEvent(OnlinePlayerBanEvent(player, banReason, banDuration, banTime, banningAdmin)) {
            setPlayerData(
                playerID = this.player.getPlayerID(),
                isBanned = true,
                banReason = this.banReason,
                banningAdmin = this.banningAdmin,
                banTime = this.banTime,
                banDuration = this.banDuration
            )
            player.kick(
                comp(
                    BanMessages.getBanFormat(
                        this.player,
                        this.banReason,
                        this.banDuration,
                        this.banTime,
                        BanConfig.getUnbanTime(this.banTime, this.banDuration),
                        this.banningAdmin
                    )
                )
            )
            recordHistory(this.player.getPlayerID(), "BAN", this.banReason, this.banDuration, this.banningAdmin)
        }
    }

    private fun offlineBan(
        playerName: String = "",
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        banningAdmin: String = ""
    ) {
        callPluginEvent(OfflinePlayerBanEvent(playerName, banReason, banDuration, banTime, banningAdmin)) {
            setPlayerData(
                playerID = playerName.getPlayerID(),
                isBanned = true,
                banReason = this.banReason,
                banningAdmin = this.banningAdmin,
                banTime = this.banTime,
                banDuration = this.banDuration
            )
            recordHistory(playerName.getPlayerID(), "BAN", this.banReason, this.banDuration, this.banningAdmin)
        }
    }

    private fun unban(playerID: String) {
        callPluginEvent(PlayerUnbanEvent(playerID)) {
            setPlayerData(
                playerID = playerID,
                isBanned = false,
                banReason = "",
                banningAdmin = "",
                banTime = "",
                banDuration = ""
            )
            recordHistory(playerID, "UNBAN")
        }
    }

    // ══ 数据写入 ══════════════════════════════════

    private fun setPlayerData(
        playerID: String,
        isBanned: Boolean = false,
        banReason: String = "",
        banningAdmin: String = "",
        banTime: String = "",
        banDuration: String = ""
    ) {
        val db = BanDatabaseManager.getDatabase()
        val data = db.getPlayerByName(playerID)
        data.isBanned = isBanned
        data.banTime = banTime
        data.unbanTime = BanConfig.getUnbanTime(banTime, banDuration)
        data.banDuration = banDuration
        data.banReason = banReason
        data.banningAdmin = banningAdmin
        db.updatePlayer(playerID, data)
        db.save()
    }

    private fun setPlayerWhitelistData(
        playerID: String,
        isWhitelisted: Boolean = false,
        whitelistingAdmin: String = "",
        whitelistTime: String = ""
    ) {
        val db = BanDatabaseManager.getDatabase()
        val data = db.getPlayerByName(playerID)
        data.isWhitelisted = isWhitelisted
        data.whitelistingAdmin = whitelistingAdmin
        data.whitelistTime = whitelistTime
        db.updatePlayer(playerID, data)
        db.save()
    }

    // ══ 对外方法 ══════════════════════════════════

    fun kickPlayer(player: Player, kickReason: String, kickTime: String, kickingAdmin: String) {
        kick(player, kickReason, kickTime, kickingAdmin)
    }

    fun banOnlinePlayer(
        player: Player,
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        banningAdmin: String = ""
    ) {
        onlineBan(player, banReason, banDuration, banTime, banningAdmin)
    }

    fun banOfflinePlayer(
        playerName: String = "",
        banReason: String = "",
        banDuration: String = "",
        banTime: String = "",
        banningAdmin: String = ""
    ) {
        offlineBan(playerName, banReason, banDuration, banTime, banningAdmin)
    }

    fun unbanPlayer(playerID: String) {
        unban(playerID)
    }

    fun whitelistPlayer(
        playerID: String,
        isWhitelisted: Boolean = false,
        whitelistTime: String = "",
        whitelistingAdmin: String = ""
    ) {
        whitelist(playerID, isWhitelisted, whitelistTime, whitelistingAdmin)
    }

    /** 是否已封禁（且未过期） */
    fun isBanned(playerID: String): Boolean {
        val data = runCatching { BanDatabaseManager.getDatabase().getPlayerByName(playerID) }.getOrNull()
            ?: return false
        if (!data.isBanned) return false
        if (data.unbanTime.isBlank()) return true
        val unban = data.unbanTime.formatToLocalDateTime(BanMessages.getTimeFormat()) ?: return true
        return unban.isAfter(LocalDateTime.now())
    }

    // ══ 警告系统 ══════════════════════════════════

    /**
     * 追加一条警告，返回新记录。
     *
     * ⚠️ 与 Phoenix 的差异：这里在写入后检查 [BanConfig.getWarnAutoBanThreshold]，
     *    达到阈值自动封禁（Phoenix 有该配置但没有触发点，属于遗留缺口）。
     */
    fun warnPlayer(playerID: String, reason: String = "", operator: String = ""): WarningRecord {
        val time = nowText()
        val id = BanDatabaseManager.getDatabase().addWarning(playerID, reason, operator, time)
        recordHistory(playerID, "WARN", reason, operator = operator)
        checkWarnAutoBan(playerID, operator)
        return WarningRecord(id, playerID, reason, operator, time)
    }

    private fun checkWarnAutoBan(playerID: String, operator: String) {
        val threshold = BanConfig.getWarnAutoBanThreshold()
        if (threshold <= 0) return
        val count = getPlayerWarningCount(playerID)
        if (count < threshold) return
        val duration = BanConfig.getWarnAutoBanDuration()
        val reason = BanConfig.getWarnAutoBanReason()
        val banTime = nowText()
        runCatching {
            setPlayerData(
                playerID = playerID,
                isBanned = true,
                banReason = reason,
                banningAdmin = if (operator.isBlank()) "系统(警告自动封禁)" else operator,
                banTime = banTime,
                banDuration = duration
            )
            recordHistory(playerID, "BAN", reason, duration, "系统(警告自动封禁)")
        }
    }

    fun getPlayerWarnings(playerID: String): List<WarningRecord> =
        BanDatabaseManager.getDatabase().getWarnings(playerID)

    fun getPlayerWarningCount(playerID: String): Int =
        BanDatabaseManager.getDatabase().getWarningCount(playerID)

    fun removeWarning(warningId: Long): Boolean =
        BanDatabaseManager.getDatabase().removeWarningById(warningId)

    fun clearWarnings(playerID: String): Int =
        BanDatabaseManager.getDatabase().clearWarnings(playerID)

    fun getPlayerHistory(playerID: String, limit: Int = 20): List<HistoryRecord> =
        BanDatabaseManager.getDatabase().getHistory(playerID, limit)

    // ══ 记录清除（面板「清除记录」按钮）══════════════

    /**
     * 删除某玩家的封禁主记录整行。
     *
     * ── 与 [unbanPlayer] 的语义区别（别混淆）────────────────
     *   unbanPlayer  → 置 isBanned=false，**行保留**，历史可追溯
     *   deletePlayer → 整行删除，该玩家在封禁表里彻底不存在
     *
     *   面板上这是两个不同的按钮：解封是日常操作，删除是数据清理。
     *   误删后只能靠 indra_ban_history 的审计记录人工恢复 —— 所以这里
     *   在删除**之前**先写审计（写失败也不阻断删除，但会记日志），
     *   保证「删过谁、谁删的、什么时候」一定留痕。
     *
     * ⚠️ 不删警告与历史：那是独立的审计数据，见 [BanDatabase.deleteBanRecord] 的注释。
     *
     * @param playerID 玩家主键（按 BanConfig 的 Player-ID 策略可能是 uuid 或名字）
     * @param operator 执行清除的管理员名（写入审计）
     * @return 是否真的删掉了行
     */
    fun deletePlayerRecord(playerID: String, operator: String = ""): Boolean {
        // 先审计后删除：顺序不能反 —— 先删再写审计的话，
        // 一旦审计写入抛异常，我们就丢掉了「谁删的」这条唯一线索。
        recordHistory(playerID, "DELETE", operator = operator)
        return runCatching {
            BanDatabaseManager.getDatabase().deleteBanRecord(playerID)
        }.getOrElse { e ->
            taboolib.common.platform.function.warning(
                "[Indra] 删除封禁记录失败（$playerID）：${e.message}"
            )
            false
        }
    }
}
