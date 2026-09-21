package com.indra.rpg.ban

interface BanDatabase {

    fun getPlayerByName(name: String): BanPlayer
    fun updatePlayer(name: String, value: BanPlayer)
    fun save()

    /** 查询所有当前被封禁的记录（含 playerID，自动解封扫描用） */
    fun getBannedPlayers(): List<BannedPlayer>

    /** 查询已封禁且解封时间早于指定时间戳的记录（自动解封用） */
    fun getExpiredBans(nowTimestamp: Long): List<BannedPlayer>

    // ── 警告系统 ──────────────────────────────────────
    fun addWarning(playerID: String, reason: String, operator: String, time: String): Long
    fun getWarnings(playerID: String): List<WarningRecord>
    fun getWarningCount(playerID: String): Int
    fun removeWarningById(warningId: Long): Boolean
    fun clearWarnings(playerID: String): Int

    // ── 操作历史 ──────────────────────────────────────
    fun addHistory(playerID: String, action: String, reason: String, duration: String, operator: String, time: String)
    fun getHistory(playerID: String, limit: Int): List<HistoryRecord>

    /**
     * 删除某玩家的封禁主记录（表 indra_ban 整行）。
     *
     * ── 为什么要单独有这个方法 ─────────────────────────────
     *   Phoenix 原版只有「改状态」没有「删记录」：解封是把 isBanned 置 false，
     *   行仍在表里。清不掉的后果是排行榜、统计、名单永远带着已撤销的历史。
     *   面板的「清除记录」按钮需要真正的删除能力。
     *
     * ⚠️ 只删主记录，**不动** indra_ban_warn / indra_ban_history：
     *    警告与审计日志是独立生命周期，删掉主记录不代表「从没发生过」。
     *    需要连警告一起清，调 [clearWarnings]。
     *
     * @return 是否真的删掉了行（false = 该 playerID 本来就没有记录）
     */
    fun deleteBanRecord(playerID: String): Boolean
}
