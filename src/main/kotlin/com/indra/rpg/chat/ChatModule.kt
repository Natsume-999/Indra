package com.indra.rpg.chat

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.module.IndraModule
import com.indra.rpg.util.Msg
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import taboolib.platform.util.bukkitPlugin

/**
 * 聊天模块 —— 给聊天消息加上 RPG 风格的前缀（等级、称号）。
 *
 * 使用 Paper 的 `AsyncChatEvent` + Adventure Component。
 * 若服务端是纯 Spigot（无 Paper 扩展），需改回 `AsyncPlayerChatEvent`。
 */
object ChatModule : IndraModule {

    override val name = "Chat"

    private var listener: Listener? = null

    override fun onEnable() {
        if (!IndraConfig.enableChatFormat) return

        val l = object : Listener {
            @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
            fun onChat(e: AsyncChatEvent) {
                val player = e.player
                val level = DataManager.get(player)?.level ?: IndraConfig.defaultLevel

                // 取纯文本消息内容（去掉 Component 的样式信息）
                val plain = PlainTextComponentSerializer.plainText().serialize(e.message())

                // 渲染成带等级前缀的新消息
                val formatted = Msg.format(
                    IndraConfig.chatFormat,
                    "player" to player.name,
                    "level" to level,
                    "message" to plain
                )
                e.renderer { _, _, _, _ -> Component.text(formatted) }
            }
        }
        listener = l
        org.bukkit.Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)
    }

    override fun onDisable() {
        listener?.let { org.bukkit.event.HandlerList.unregisterAll(it) }
        listener = null
    }
}
