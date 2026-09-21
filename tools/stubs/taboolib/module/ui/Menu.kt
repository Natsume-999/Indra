// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib bukkit-ui 离线桩
//  ---------------------------------------------------------------------------
//  ⚠️ 仅在 `tools/verify.sh` 找不到真实 TabooLib jar 时才会被编译。
//     正常路径下 verify.sh 优先用 Gradle 缓存里的真包（MODE=real），
//     因为桩与真包之间的**签名漂移会制造假阴性**。
//
//  本文件签名已于 2026-09-20 用 `javap` 逐条对照
//  bukkit-ui-6.3.0-f9483b9.jar 核实。已知的易踩的点：
//
//    1. `Chest.set(int, ItemStack, onClick)` / `Chest.onClick(...)` 的 onClick
//       是 **值参数 lambda** `(ClickEvent) -> Unit`
//       （字节码上是 `Function1<? super ClickEvent, Unit>`），
//       **不是**接收者 lambda `ClickEvent.() -> Unit`。
//       实证：真包上写 `onClick { println(this.rawSlot) }` 报
//       `unresolved reference 'rawSlot'`；写 `onClick { println(it.rawSlot) }` 才通过。
//
//    2. `Chest.onClick` 的三个重载**都带首参**，没有单参形式：
//       `onClick(slot: Int, ...)` / `onClick(slot: Char, ...)` / `onClick(handled: Boolean, ...)`
//
//    3. `Chest.onClose(once, skipOnUpdateTitle, callback)` 的形参**不是命名参数**
//       （写成 `onClose(once = true, skipOnUpdateTitle = true)` 在真包上会报
//       "no parameter with name 'skipOnUpdateTitle' found"），必须位置传参。
//
//  ⚠️ 不要为了让源码「编译通过」而把桩写得更宽松 ——
//     那正是桩最危险的地方。桩必须和真包一样严格。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.module.ui

import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.type.Chest
import taboolib.platform.util.ItemBuilder

/** 菜单基接口。实现由 `Menu.Companion` 的静态块注册（真包行为）。 */
interface Menu {
    var title: String
    fun build(): Inventory
}

/** 点击类型（真包是枚举，仅三个值） */
enum class ClickType { CLICK, DRAG, VIRTUAL }

/** 菜单 holder —— 用于把菜单实例挂到 Inventory 上 */
open class MenuHolder(private val chest: Chest) : InventoryHolder {
    override fun getInventory(): Inventory = error("stub")
}

/**
 * 点击事件。
 * 方法名与真包一致；真包里还有 clickEvent()/dragEvent() 等便捷访问器，
 * 桩只保留 Indra 实际用到的部分。
 */
class ClickEvent(
    private val event: InventoryInteractEvent,
    val clickType: ClickType,
    val slot: Char,
    val builder: Chest,
) {
    val clicker: Player get() = error("stub")
    val inventory: Inventory get() = error("stub")
    val view: InventoryView get() = error("stub")
    val affectItems: List<ItemStack> get() = error("stub")
    val rawSlot: Int get() = error("stub")
    val hotbarKey: Int get() = error("stub")
    val currentItem: ItemStack? get() = error("stub")
    val cursorItem: ItemStack? get() = error("stub")

    var isCancelled: Boolean = false

    fun onCancel(consumer: java.util.function.Consumer<Array<StackTraceElement>?>): ClickEvent = this

    fun getItem(slot: Char): ItemStack? = error("stub")
    fun getItems(slot: Char): List<ItemStack> = error("stub")
    fun clickEvent(): InventoryClickEvent = error("stub")
    fun clickEventOrNull(): InventoryClickEvent? = error("stub")
    fun dragEvent(): InventoryDragEvent = error("stub")
    fun dragEventOrNull(): InventoryDragEvent? = error("stub")
    fun onClick(callback: (InventoryClickEvent) -> Unit): ClickEvent = this
    fun onDrag(callback: (InventoryDragEvent) -> Unit): ClickEvent = this
}
