package taboolib.common.platform.event

/**
 * ⚠️ 已对真实 TabooLib 6.3 核实：
 *   `@SubscribeEvent` 的 `priority` 参数类型是 **taboolib.common.platform.event.EventPriority**，
 *   **不是** org.bukkit.event.EventPriority。
 *   两者是不同枚举，直接传 Bukkit 的会编译失败（实测踩过）。
 *   TabooLib 内部的 EventPriority 与 Bukkit 的取值一一对应：
 *     LOWEST / LOW / NORMAL / HIGH / HIGHEST / MONITOR
 *   并且多一个 `OBSERVE`（不取消、只观察）。
 */
enum class EventPriority {
    LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR, OBSERVE
}

@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent(
    val priority: EventPriority = EventPriority.NORMAL,
    val ignoreCancelled: Boolean = false
)
