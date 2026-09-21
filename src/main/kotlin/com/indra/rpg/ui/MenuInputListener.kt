package com.indra.rpg.ui

import com.indra.rpg.util.Msg
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 搜索输入监听 —— 把玩家的下一条聊天消息当作搜索关键词。
 *
 * ══ 为什么用 Paper 的 AsyncChatEvent 而不是 Bukkit 的 AsyncPlayerChatEvent ══
 *   `org.bukkit.event.player.AsyncPlayerChatEvent` 在 Paper 上**已弃用**
 *   （Paper 26.3 编译会出 warning，且未来会被移除）。
 *   替代品是 `io.papermc.paper.event.player.AsyncChatEvent`，它传递的是
 *   Adventure `Component` 而非 String。
 *
 *   ⚠️ 二者不是「同一个事件的两个名字」：
 *      旧事件仍然会被触发（Paper 做了兼容），但如果两个都监听，
 *      同一个搜索词会被处理两次。这里**只**监听新事件。
 *
 * ══ 文本提取 ══════════════════════════════════════════════════════
 *   `event.message()` 是 Component；要出纯文本必须序列化。
 *   `PlainTextComponentSerializer.plainText()` 会**丢弃**所有样式与颜色，
 *   正是搜索需要的（否则玩家输入 §c 之类会让关键词匹配不上）。
 *   不要用 `Component.toString()` —— 那输出的是类似
 *   `TextComponentImpl{content="abc"}` 的调试串，拿去匹配永远失败。
 *
 * ══ 生命周期 ══════════════════════════════════════════════════════
 *   [await] 登记一次等待，[AsyncChatEvent] 到达时消费。
 *   等待是**一次性的**：消费后立即移除，不会把之后每条聊天都吃掉。
 */
object MenuInputListener : Listener {

    /** uuid → 消费回调。一次性的，触发即移除。 */
    private val waiting = ConcurrentHashMap<UUID, (String) -> Unit>()

    /**
     * 登记「下一条聊天作为输入」。
     *
     * ⚠️ 必须在关闭面板**之后**调用：面板还开着时玩家发不出聊天
     *    （界面占据输入焦点），而且聊天事件到达时面板的
     *    InventoryClickEvent 栈还没退干净，重开界面会被丢弃。
     */
    fun await(player: Player, hint: String = "&7请在聊天栏输入搜索关键词（输入 &f- &7清除）", onInput: (String) -> Unit) {
        waiting[player.uniqueId] = onInput
        Msg.raw(player, hint)
        Msg.raw(player, "&8（本次输入不会发送给其他玩家）")
    }

    /** 玩家是否正在等待输入（面板侧用它决定要不要吞掉点击） */
    fun isWaiting(player: Player): Boolean = waiting.containsKey(player.uniqueId)

    /** 取消等待（玩家中途退服 / 面板被关） */
    fun cancel(player: Player) {
        waiting.remove(player.uniqueId)
    }

    /**
     * ⚠️ 用 `ignoreCancelled = false`：别的插件可能默认取消聊天，
     *    但我们作为「输入通道」必须仍然拿到内容。
     * ⚠️ 优先级用 LOWEST：抢在其他插件改写 / 取消之前读到原始文本。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val consumer = waiting.remove(player.uniqueId) ?: return

        // 吞掉这条消息：不让它变成公开聊天
        event.isCancelled = true

        val text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        // 空输入 / "-" 视为清除过滤
        val keyword = if (text.isEmpty() || text == "-") "" else text

        // 聊天事件是异步的，而重开界面必须在主线程 —— 切回去
        taboolib.common.platform.function.submit {
            consumer(keyword)
            if (keyword.isEmpty()) Msg.send(player, "&7已清除搜索过滤。")
            else Msg.send(player, "&7搜索：&f$keyword")
        }
    }
}
