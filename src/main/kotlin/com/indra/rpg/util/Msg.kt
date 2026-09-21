package com.indra.rpg.util

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * 文本工具：颜色码转换 + 变量占位符替换。
 *
 * 占位符格式 `{name}`，与 config.yml / messages 中的写法保持一致。
 *
 * 说明：这里用 `§` 传统颜色码而非 Adventure Component，
 * 因为 `player.sendMessage(String)` 在 Bukkit / Spigot / Paper 上通用，
 * 对指令、控制台、配置文件都最省事。
 */
object Msg {

    private const val PREFIX = "&b[Indra] &r"

    /** 转换 & 颜色码为 § 格式 */
    fun color(text: String): String = text.replace('&', '§')

    /** 发送带前缀的消息 */
    fun send(target: CommandSender, text: String) {
        target.sendMessage(color(PREFIX + text))
    }

    /** 发送不带前缀的原始消息 */
    fun raw(target: CommandSender, text: String) {
        target.sendMessage(color(text))
    }

    /** 发送 ActionBar 消息 */
    fun actionBar(target: Player, text: String) {
        target.sendActionBar(net.kyori.adventure.text.Component.text(color(text)))
    }

    /**
     * 占位符替换。
     * 用法：`Msg.format("&a你好 {player}", "player" to "Steve")`
     */
    fun format(template: String, vararg placeholders: Pair<String, Any?>): String {
        var result = template
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value?.toString() ?: "")
        }
        return color(result)
    }
}
