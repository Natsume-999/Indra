package com.indra.rpg.ban

/** 一条警告记录（表 indra_ban_warn）。 */
data class WarningRecord(
    val id: Long,
    val playerID: String,
    val reason: String,
    val operator: String,
    val time: String
)

/**
 * 一条管理操作历史（表 indra_ban_history）。
 * action 取值：BAN / UNBAN / KICK / WHITELIST / UNWHITELIST / WARN / DELETE
 */
data class HistoryRecord(
    val id: Long,
    val playerID: String,
    val action: String,
    val reason: String,
    val duration: String,
    val operator: String,
    val time: String
)
