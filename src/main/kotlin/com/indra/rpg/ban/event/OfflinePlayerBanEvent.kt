package com.indra.rpg.ban.event

import taboolib.platform.type.BukkitProxyEvent

class OfflinePlayerBanEvent(
    val playerName: String,
    var banReason: String = "",
    var banDuration: String = "",
    var banTime: String = "",
    var banningAdmin: String = ""
) : BukkitProxyEvent()
