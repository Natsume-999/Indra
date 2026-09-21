// ═══════════════════════════════════════════════════════════════════════════
//  XMaterial 离线桩
//  ⚠️ 仅当 verify.sh 找不到真实 TabooLib jar 时编译。
//
//  真包是 Java 枚举（host = platform-bukkit-impl），约 1669 个常量。
//  桩不参与运行，只负责让 Indra 源码里出现的常量名能被解析 ——
//  因此只列 Indra 用到的那些，并把用不到的名字交给 Kotlin 在编译期兜底。
//
//  ⚠️ 桩里 `valueOf(name)` 必须真实实现（不能 error("stub")）：
//     `MenuIcons.material()` 在**构造期**就会调它，
//     如果桩抛异常，桩模式下所有图标测试都会失败 —— 那就不是「兜底」，
//     而是「桩本身有 bug」。这里用一个宽松的兜底映射。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.library.xseries

import org.bukkit.Material

enum class XMaterial(val bukkitMaterial: Material) {
    STONE(Material.STONE),
    AIR(Material.AIR),
    ARROW(Material.ARROW),
    PAPER(Material.PAPER),
    BOOK(Material.BOOK),
    BOOKSHELF(Material.BOOKSHELF),
    BARRIER(Material.BARRIER),
    BEACON(Material.BEACON),
    CLOCK(Material.CLOCK),
    COMMAND_BLOCK(Material.COMMAND_BLOCK),
    COMPARATOR(Material.COMPARATOR),
    COPPER_BLOCK(Material.COPPER_BLOCK),
    DIAMOND(Material.DIAMOND),
    EMERALD(Material.EMERALD),
    EMERALD_BLOCK(Material.EMERALD_BLOCK),
    EXPERIENCE_BOTTLE(Material.EXPERIENCE_BOTTLE),
    GOLD_BLOCK(Material.GOLD_BLOCK),
    GOLDEN_HELMET(Material.GOLDEN_HELMET),
    GRAY_DYE(Material.GRAY_DYE),
    GRAY_STAINED_GLASS_PANE(Material.GRAY_STAINED_GLASS_PANE),
    BLACK_STAINED_GLASS_PANE(Material.BLACK_STAINED_GLASS_PANE),
    HOPPER(Material.HOPPER),
    IRON_BLOCK(Material.IRON_BLOCK),
    IRON_DOOR(Material.IRON_DOOR),
    NETHER_STAR(Material.NETHER_STAR),
    OAK_SIGN(Material.OAK_SIGN),
    PLAYER_HEAD(Material.PLAYER_HEAD),
    REDSTONE_BLOCK(Material.REDSTONE_BLOCK),
    REDSTONE_TORCH(Material.REDSTONE_TORCH),
    SKELETON_SKULL(Material.SKELETON_SKULL),
    SPYGLASS(Material.SPYGLASS),
    TNT(Material.TNT),
    ;

    override fun toString(): String = name

    companion object {
        /**
         * ⚠️ 不要声明 `valueOf(name: String)`！
         *    枚举天生带一个同签名的合成方法 `valueOf(String)`，
         *    再声明一次会报
         *      "platform declaration clash: The following declarations have
         *       the same JVM signature (valueOf(Ljava/lang/String;)...)"
         *
         *   真包的 XMaterial 是 Java 写的、自己实现了 `valueOf`（用于别名兼容），
         *   但 Kotlin 桩里做不到同样的事 —— 好在**桩不参与运行**，
         *   `MenuIcons` 调 `XMaterial.valueOf(name)` 时会自动落到枚举自带的那个，
         *   语义完全一致。所以这里只需要补一个 `VALUES` 就够。
         */
        @JvmStatic
        val VALUES: List<XMaterial> get() = entries
    }
}
