// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib MenuBuilder 离线桩
//  ⚠️ 文件名必须是 MenuBuilder.kt：真包把 buildMenu / openMenu 放在
//     MenuBuilder.kt 里，编译后的类名是 **MenuBuilderKt**。
//     桩若把它们写进 Menu.kt（→ MenuKt），虽然源码 import 写的是
//     `taboolib.module.ui.openMenu`（包级函数，不涉及类名），
//     但为了让「桩 ↔ 真包」的文件结构一致、避免将来加 @JvmName 时踩坑，
//     这里保持同名同结构。
//
//  签名已 javap 核实（bukkit-ui-6.3.0-f9483b9.jar）：
//    buildMenu<T : Menu>(title: String, builder: T.() -> Unit): Inventory
//    openMenu<T : Menu>(sender: HumanEntity, title: String, builder: T.() -> Unit)
//    openMenu(sender: HumanEntity, inventory: Inventory, close: Boolean = true)
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.module.ui

import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.Inventory

inline fun <reified T : Menu> buildMenu(title: String, builder: T.() -> Unit): Inventory = error("stub")

inline fun <reified T : Menu> openMenu(
    sender: HumanEntity,
    title: String,
    builder: T.() -> Unit
): Unit = error("stub")

fun openMenu(sender: HumanEntity, inventory: Inventory, close: Boolean = true): Unit = error("stub")
