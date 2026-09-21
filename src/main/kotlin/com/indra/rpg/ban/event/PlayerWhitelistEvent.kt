package com.indra.rpg.ban.event

import taboolib.platform.type.BukkitProxyEvent

class PlayerWhitelistEvent(
    val playerID: String,
    var isWhitelisted: Boolean = false,
    var whitelistTime: String = "",
    var whitelistingAdmin: String = ""
) : BukkitProxyEvent()
