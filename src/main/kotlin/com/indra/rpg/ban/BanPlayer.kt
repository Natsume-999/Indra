package com.indra.rpg.ban

/**
 * 封禁玩家数据（表 indra_ban 一行）。
 *
 * 与 Phoenix 版本一致：纯数据类，不挂 @Serializable，
 * 避免把 kotlinx-serialization 运行时拖进产物（400+ 类无人调用）。
 */
data class BanPlayer(
    var isBanned: Boolean = false, // 玩家当前是否被封禁
    var banReason: String = "", // 封禁原因
    var banDuration: String = "", // 封禁时长（原始字符串，如 "7d"）
    var banTime: String = "", // 封禁发生时间
    var unbanTime: String = "", // 预计解封时间（仅有时效封禁时存在）
    var banningAdmin: String = "", // 执行封禁的管理员
    var isWhitelisted: Boolean = false, // 是否在白名单
    var whitelistTime: String = "", // 加入白名单的时间
    var whitelistingAdmin: String = "" // 执行白名单操作的管理员
)
