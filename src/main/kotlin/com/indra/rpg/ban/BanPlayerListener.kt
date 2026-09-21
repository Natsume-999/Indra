package com.indra.rpg.ban

import com.indra.rpg.ban.BanMessages.getPlayerID
import com.indra.rpg.common.util.TimeUtil.formatToLocalDateTime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import taboolib.common.platform.event.SubscribeEvent
import java.time.LocalDateTime

/**
 * 登录拦截：封禁未过期 → 拒绝进入；封禁已过期 → 自动解封放行。
 *
 * ⚠️ 用 `AsyncPlayerPreLoginEvent` 而不是 `PlayerLoginEvent`：
 *    登录前事件在异步线程触发，数据库查询不会卡主线程。
 *
 * 移植自 Phoenix 的 ban/BanPlayerListener.kt（v1.16.0），并修复一处绕过漏洞：
 *
 * ⚠️ Phoenix 原版在 `unbanTime` 为空（永久封禁）时只看白名单开关，
 *    白名单关闭 → 直接 `event.allow()`，**永久封禁的玩家能正常进服**。
 *    这里改为先判 `data.isBanned`，封禁优先于白名单。
 *
 * 同时移除了原版一个空的 PlayerJoinEvent 占位监听。
 */
object BanPlayerListener {

    /**
     * 把 `BanMessages` 产出的 § 色码字符串转成 Adventure `Component`。
     *
     * ⚠️ Paper 26.3 弃用了 `disallow(Result, String)`，必须用
     *    `disallow(Result, Component)`；而封禁文案里有用户自定义文本，
     *    统一走 legacy 序列化器最稳（支持 § 与十六进制写法）。
     */
    private fun comp(text: String): Component =
        LegacyComponentSerializer.legacySection().deserialize(text)

    @SubscribeEvent
    fun onPlayerLogin(event: AsyncPlayerPreLoginEvent) {
        val offlinePlayer = Bukkit.getOfflinePlayer(event.uniqueId)
        val db = BanDatabaseManager.getDatabase()
        val data = db.getPlayerByName(event.name.getPlayerID())
        db.save()

        // 无时效封禁（永久封禁 / 从未封禁）走白名单分支
        if (data.unbanTime.isBlank()) {
            if (!BanConfig.isEnabledWhitelist() || data.isWhitelisted) {
                // 未开启白名单，或已在白名单 → 放行
                if (data.isBanned) {
                    event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        comp(
                            BanMessages.getBanFormat(
                                offlinePlayer,
                                data.banReason,
                                data.banDuration,
                                data.banTime,
                                data.unbanTime,
                                data.banningAdmin
                            )
                        )
                    )
                } else {
                    event.allow()
                }
            } else {
                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    comp(BanMessages.getWhitelistFormat(offlinePlayer))
                )
            }
            return
        }

        // 有时效封禁：判断是否已到期
        val now = LocalDateTime.now()
        val unbanTime = data.unbanTime.formatToLocalDateTime(BanMessages.getTimeFormat())
        if (unbanTime == null || now.isAfter(unbanTime)) {
            BanApi.unbanPlayer(event.name.getPlayerID())
            event.allow()
        } else if (data.isBanned) {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                comp(
                    BanMessages.getBanFormat(
                        offlinePlayer,
                        data.banReason,
                        data.banDuration,
                        data.banTime,
                        data.unbanTime,
                        data.banningAdmin
                    )
                )
            )
        }
    }
}
