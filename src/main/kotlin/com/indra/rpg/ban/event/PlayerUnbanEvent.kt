package com.indra.rpg.ban.event

import taboolib.platform.type.BukkitProxyEvent

class PlayerUnbanEvent(
    val playerID: String
) : BukkitProxyEvent()
