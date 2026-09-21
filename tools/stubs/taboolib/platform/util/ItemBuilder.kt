// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib ItemBuilder / XMaterial / buildItem 离线桩
//  ⚠️ 仅当 verify.sh 找不到真实 TabooLib jar 时编译。签名已 javap 核实：
//
//     · ItemBuilder 的 `name` / `lore` 是 **var 属性**（对应 getName/setName
//       / getLore），不是方法。写成 `this.name = "..."` 才对。
//       `shiny()` 是方法（附魔闪光）。
//     · `buildItem(XMaterial, builder)` / `buildItem(Material, builder)`
//       / `buildItem(ItemStack, builder)` 三个重载都在 ItemBuilderKt 里，
//       是真包的**文件顶层函数**。
//     · XMaterial 是**枚举**（Java 写的），有 ~1669 个常量，
//       宿主是 platform-bukkit-impl 而不是 bukkit-xseries。
//       桩里只放 Indra 用到的几个常量 —— 桩不参与运行，
//       只需保证 Indra 源码里出现的名字能被解析。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.platform.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial

class ItemBuilder {
    constructor(material: Material)
    constructor(material: XMaterial)
    constructor(itemStack: ItemStack)

    var material: Material
        get() = error("stub")
        set(_) {}

    var amount: Int
        get() = error("stub")
        set(_) {}

    var name: String
        get() = error("stub")
        set(_) {}

    var itemName: String
        get() = error("stub")
        set(_) {}

    val lore: ArrayList<String> get() = error("stub")

    var color: org.bukkit.Color
        get() = error("stub")
        set(_) {}

    var unbreakable: Boolean
        get() = error("stub")
        set(_) {}

    var customModelData: Int
        get() = error("stub")
        set(_) {}

    var itemModel: org.bukkit.NamespacedKey?
        get() = error("stub")
        set(_) {}

    var hideTooltip: Boolean
        get() = error("stub")
        set(_) {}

    var skullOwner: String
        get() = error("stub")
        set(_) {}

    // ⚠️ 桩里必须有方法体 —— 无体的 `fun shiny()` 会被 Kotlin 判定为
    //    "function without a body must be abstract"（这是接口里才合法的语法）。
    //    桩的存在意义是「签名对齐」，不是「真的能跑」，
    //    所以方法体写成空实现即可，但**不能省**。
    fun shiny() {}
    fun colored() {}
    fun build(): ItemStack = error("stub")
}

fun buildItem(material: Material, builder: ItemBuilder.() -> Unit = {}): ItemStack = error("stub")

fun buildItem(material: XMaterial, builder: ItemBuilder.() -> Unit = {}): ItemStack = error("stub")

fun buildItem(itemStack: ItemStack, builder: ItemBuilder.() -> Unit = {}): ItemStack = error("stub")
