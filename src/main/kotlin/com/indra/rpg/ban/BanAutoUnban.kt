package com.indra.rpg.ban

import com.indra.rpg.Indra
import com.indra.rpg.common.script.KetherRunner
import org.bukkit.Bukkit
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor.PlatformTask

/**
 * 封禁到期自动解封。
 *
 * 启动时立即扫描一次（清掉积压的过期封禁），之后按
 * `Auto-Unban.Interval-Seconds`（秒）周期扫描。
 *
 * ── 解封后动作（Kether 脚本）─────────────────────────────
 *   配置项 `Auto-Unban.Actions`，可用变量：
 *   {player} {uuid} {reason} {duration} {ban_time} {unban_time} {banning_admin}
 *
 * 移植自 Phoenix 的 ban/BanAutoUnban.kt（v1.16.0），
 * 脚本执行改走 [KetherRunner]（不再依赖 Phoenix 的 KetherActions 包装层）。
 */
object BanAutoUnban {

    private var task: PlatformTask? = null

    /** 启动并立即扫描一次 */
    fun start() {
        stop()
        if (!BanConfig.isAutoUnbanEnabled()) return
        val intervalSeconds = BanConfig.getAutoUnbanIntervalSeconds()
        // 立即扫描一次（启动时清掉积压的过期封禁）
        scanNow()
        task = submit(delay = intervalSeconds * 20L, period = intervalSeconds * 20L) { scanNow() }
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /** 扫描一次：把已到期的封禁全部解封并执行动作 */
    fun scanNow() {
        val now = System.currentTimeMillis()
        val expired = runCatching {
            BanDatabaseManager.getDatabase().getExpiredBans(now)
        }.getOrElse {
            Indra.logger.warning("自动解封扫描失败：${it.javaClass.simpleName}（${it.message}）")
            return
        }
        if (expired.isEmpty()) return

        var succeeded = 0
        for (ban in expired) {
            try {
                unbanExpired(ban)
                succeeded++
            } catch (e: Exception) {
                Indra.logger.warning("自动解封 ${ban.playerID} 失败：${e.javaClass.simpleName}（${e.message}）")
            }
        }
        if (succeeded > 0) {
            Indra.logger.info("自动解封：已处理 $succeeded 条到期封禁")
        }
    }

    private fun unbanExpired(ban: BannedPlayer) {
        val playerID = ban.playerID
        // 1. 数据库解封（BanApi 会更新 isBanned=false 并写历史）
        BanApi.unbanPlayer(playerID)

        // 2. Kether 解封动作（离线玩家也执行，以支持公告类动作）
        val actions = BanConfig.settings.getStringList("Auto-Unban.Actions")
        if (actions.isEmpty()) return

        val offline = Bukkit.getOfflinePlayer(playerID)
        val variables = mapOf(
            "player" to (offline.name ?: playerID),
            "uuid" to offline.uniqueId.toString(),
            "reason" to ban.banReason,
            "duration" to ban.banDuration,
            "ban_time" to ban.banTime,
            "unban_time" to ban.unbanTime,
            "banning_admin" to ban.banningAdmin
        )
        val online = KetherRunner.onlinePlayerOrNull(playerID)
        KetherRunner.run(online, actions, variables)
    }
}
