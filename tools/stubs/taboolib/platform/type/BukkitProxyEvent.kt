// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  真实类型：taboolib.platform.type.BukkitProxyEvent（继承 org.bukkit.event.Event）
//    提供 call() 与 isCancelled，供自定义事件使用。
// ============================================================================

package taboolib.platform.type

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

open class BukkitProxyEvent : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    /** 手动派发；返回 false 表示被取消 */
    open fun call(): Boolean {
        BukkitProxyEventBus.fire(this)
        return !cancelled
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList: HandlerList = HandlerList()
    }
}

/** 桩环境下的极简派发：没有真实 PluginManager，仅保证 call() 不炸 */
internal object BukkitProxyEventBus {
    fun fire(event: Event) {
        runCatching {
            org.bukkit.Bukkit.getPluginManager().callEvent(event)
        }
    }
}
