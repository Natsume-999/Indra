package com.indra.rpg.ban

import com.indra.rpg.common.util.TimeUtil.formatToLocalDateTime
import com.indra.rpg.common.util.TimeUtil.formatToString
import com.indra.rpg.common.util.TimeUtil.parseTime
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 封禁模块配置（plugins/Indra/ban.yml 与 ban-messages.yml）。
 *
 *  · 模块设置（白名单 / 主键策略 / 警告 / 时间格式）在 ban.yml
 *  · 消息格式（踢出 / 封禁 / 白名单 / 警告 四组）在 ban-messages.yml
 *
 * 注入方式沿用 Indra 既有的 `@Config` + `lateinit var`，
 * 不移植 Phoenix 的 ConfigRegistry（那是为 10+ 模块设计的注册表，
 * Indra 模块数量少，用框架原生注入更直接）。
 */
object BanConfig {

    @Config("ban.yml", autoReload = true)
    lateinit var settings: Configuration
        private set

    @Config("ban-messages.yml", autoReload = true)
    lateinit var messages: Configuration
        private set

    // ── 模块开关与策略 ──────────────────────────────────
    /** 是否启用白名单（启用后不在白名单的玩家无法进入） */
    fun isEnabledWhitelist() = settings.getBoolean("Whitelist", false)

    /**
     * 封禁主键策略：
     *   name → 按玩家名（改名会绕过封禁，但可读性好）
     *   uuid → 按 UUID（推荐，改名无效）
     */
    fun getPlayerID() = settings.getString("Player-ID", "uuid") ?: "uuid"

    fun getDefaultKickReason() = settings.getString("Default-Value.Kick-Reason") ?: "管理员未填写请出原因"
    fun getDefaultBanReason() = settings.getString("Default-Value.Ban-Reason") ?: "管理员未填写封禁原因"
    fun getDefaultWarnReason() = settings.getString("Default-Value.Warn-Reason") ?: "管理员未填写警告原因"

    // ── 警告自动封禁 ────────────────────────────────────
    /** 警告次数达到该值自动封禁；0 = 不启用 */
    fun getWarnAutoBanThreshold() = settings.getInt("Warning.Auto-Ban-Threshold", 0)
    fun getWarnAutoBanDuration() = settings.getString("Warning.Auto-Ban-Duration") ?: "1d"
    fun getWarnAutoBanReason() = settings.getString("Warning.Auto-Ban-Reason") ?: "警告次数过多"

    // ── 自动解封 ────────────────────────────────────────
    fun isAutoUnbanEnabled() = settings.getBoolean("Auto-Unban.Enabled", true)
    fun getAutoUnbanIntervalSeconds() =
        settings.getLong("Auto-Unban.Interval-Seconds", 30).coerceAtLeast(1)

    /** 自动解封后执行的 Kether 脚本（可为 null） */
    fun getAutoUnbanActions() = settings["Auto-Unban.Actions"]

    // ── 消息格式 ────────────────────────────────────────
    fun getKickFormat() = messages.getStringList("Message-Format.Kick")
    fun getBanFormat() = messages.getStringList("Message-Format.Ban")
    fun getWhitelistFormat() = messages.getStringList("Message-Format.Whitelist")
    fun getWarnFormat() = messages.getStringList("Message-Format.Warn")

    // ── 时间格式（缺失时补默认值，防旧配置缺节导致空指针）──
    fun getTimeFormat(): ConfigurationSection {
        settings.getConfigurationSection("Time-Format")?.let { return it }
        val section = settings.createSection("Time-Format")
        section.set("Date", "yyyy-MM-dd")
        section.set("Time", "yyyy-MM-dd HH:mm:ss")
        section.createSection("Duration").run {
            set("Second", "s")
            set("Minute", "m")
            set("Hour", "H")
            set("Day", "d")
            set("Week", "w")
            set("Month", "M")
            set("Year", "y")
        }
        section.set("Permanent", "10y")
        return section
    }

    /** 按封禁时间 + 时长算出解封时间；无时长（永久）时返回空串 */
    fun getUnbanTime(banTime: String, banDuration: String): String {
        if (banTime.isBlank()) return ""
        val timeSection = getTimeFormat()
        return banTime.formatToLocalDateTime(BanMessages.getTimeFormat())!!
            .plusSeconds(
                banDuration.parseTime(
                    second = timeSection.getString("Duration.Second", "s") ?: "s",
                    minute = timeSection.getString("Duration.Minute", "m") ?: "m",
                    hour = timeSection.getString("Duration.Hour", "H") ?: "H",
                    day = timeSection.getString("Duration.Day", "d") ?: "d",
                    week = timeSection.getString("Duration.Week", "w") ?: "w",
                    month = timeSection.getString("Duration.Month", "M") ?: "M",
                    year = timeSection.getString("Duration.Year", "y") ?: "y"
                )
            ).formatToString(BanMessages.getTimeFormat())
    }
}
