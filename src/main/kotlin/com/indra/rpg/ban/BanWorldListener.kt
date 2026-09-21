package com.indra.rpg.ban

import org.bukkit.event.world.WorldSaveEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * 世界保存时同步封禁数据。
 *
 * 直连模式下每次写操作即时落库，这里只是与服务器存档节奏对齐，
 * 保证「世界回档」与「封禁数据」不会跨版本错位。
 */
object BanWorldListener {

    @SubscribeEvent
    fun onWorldSave(event: WorldSaveEvent) {
        BanDatabaseManager.getDatabase().save()
    }
}
