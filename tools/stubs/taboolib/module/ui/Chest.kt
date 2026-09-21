// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib bukkit-ui / Chest 离线桩
//  ⚠️ 仅当 verify.sh 找不到真实 TabooLib jar 时编译。签名已 javap 核实。
//     注意 `onClick` / `set(..., onClick)` 的回调是**值参数 lambda**
//     `(ClickEvent) -> Unit`（字节码 `Function1<? super ClickEvent, Unit>`），
//     **不是**接收者 lambda `ClickEvent.() -> Unit`。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.module.ui.type

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryCloseEvent
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.Menu
import taboolib.module.ui.MenuHolder
import taboolib.platform.util.ItemBuilder

/** 箱子菜单接口（真包是接口，实现类 ChestImpl 由 Menu.Companion 注册） */
interface Chest : Menu {
    val rows: Int
    val virtualized: Boolean
    val virtualizedStorageContents: List<ItemStack>
    val items: java.util.concurrent.ConcurrentHashMap<Char, ItemStack>
    val slots: java.util.concurrent.CopyOnWriteArrayList<List<Char>>
    val handLocked: Boolean
    val isOpened: Boolean

    fun virtualize(storageContents: List<ItemStack>? = null)
    fun hidePlayerInventory()

    fun rows(rows: Int)
    fun handLocked(lock: Boolean)
    fun holder(holder: (Chest) -> MenuHolder)
    fun onBuild(async: Boolean = false, run: (Player, Inventory) -> Unit)
    fun onFinalBuild(async: Boolean = false, run: (Player, Inventory) -> Unit)
    fun onInventoryCreate(run: (Inventory) -> Unit)
    fun onClose(once: Boolean, skipOnUpdateTitle: Boolean, run: (InventoryCloseEvent) -> Unit)

    fun onClick(slot: Int, run: (ClickEvent) -> Unit)
    fun onClick(slot: Char, run: (ClickEvent) -> Unit)
    fun onClick(handled: Boolean, run: (ClickEvent) -> Unit)

    fun map(vararg map: String)
    fun set(slot: Char, itemStack: ItemStack)
    fun set(slot: Int, itemStack: ItemStack)
    fun set(slot: Char, itemStack: () -> ItemStack)
    fun set(slot: Int, itemStack: () -> ItemStack)
    fun set(slot: Char, material: XMaterial, itemBuilder: ItemBuilder.() -> Unit = {})
    fun set(slot: Int, material: XMaterial, itemBuilder: ItemBuilder.() -> Unit = {})
    fun set(slot: Char, itemStack: ItemStack, onClick: (ClickEvent) -> Unit = {})
    fun set(slot: Int, itemStack: ItemStack, onClick: (ClickEvent) -> Unit = {})

    fun getSlot(slot: Int): Char
    fun getSlots(slot: Char): List<Int>
    fun getFirstSlot(slot: Char): Int
    fun updateTitle(title: String)
}

/** 分页箱子接口（Indra 当前未直接用，保留以对齐真包） */
interface PageableChest<T> : Chest {
    val page: Int
    fun menuLocked(lock: Boolean)
    fun page(page: Int)
    fun slots(slots: List<Int>)
    fun slotsBy(slot: Char)
    fun elements(elements: () -> List<T>)
    fun onGenerate(async: Boolean = false, run: (Player, T, Int, Int) -> ItemStack)
    fun onClick(run: (ClickEvent, T) -> Unit)
    fun onPageChange(run: (Player) -> Unit)
    val hasPreviousPage: Boolean
    val hasNextPage: Boolean
    fun resetElementsCache()
}
