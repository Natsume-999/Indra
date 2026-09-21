package com.indra.rpg.ban

/**
 * 被封禁玩家记录（自动解封扫描用）。
 *
 * 与 [BanPlayer] 的区别：多一个 playerID（数据表主键），
 * 因为自动解封需要按玩家主键回写解封状态。
 */
data class BannedPlayer(
    val playerID: String,
    val isBanned: Boolean,
    val banReason: String,
    val banDuration: String,
    val banTime: String,
    val unbanTime: String,
    val banningAdmin: String,
    val isWhitelisted: Boolean,
    val whitelistTime: String,
    val whitelistingAdmin: String
) {
    /** 转换为 [BanPlayer]（[BanApi] 的写入路径需要）。 */
    fun toBanPlayer(): BanPlayer = BanPlayer(
        isBanned = isBanned,
        banReason = banReason,
        banDuration = banDuration,
        banTime = banTime,
        unbanTime = unbanTime,
        banningAdmin = banningAdmin,
        isWhitelisted = isWhitelisted,
        whitelistTime = whitelistTime,
        whitelistingAdmin = whitelistingAdmin
    )
}
