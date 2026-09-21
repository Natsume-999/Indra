package com.indra.rpg.ui.menu

import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuManager
import org.bukkit.entity.Player
import taboolib.module.ui.ClickEvent

/**
 * 信标设置面板 —— **占位实现**。
 *
 * ══ 为什么先放一个占位面板 ═══════════════════════════════
 *   信标系统（docs/功能清单.md 的 A4）还没开发，涉及：
 *     · Display 实体（原版信标光柱的 Display 版本）
 *     · 自定义物品模型（CustomModelData / ItemModel）
 *     · 光柱颜色、转动速度
 *     · 着色器（TAB 栏自定义，A6）
 *   这些依赖尚未就位，所以这里**不假装能用** ——
 *   面板打开后会明确说明「未启用」并列出待完成的子项。
 *
 * ══ 为什么不干脆不注册这个面板 ═══════════════════════════
 *   因为「打开一个说明页」比「/indra menu beacon 报未知面板」对用户友好：
 *   后者看起来像 bug，前者是明确的产品状态。
 *   等 A4 开发完成后，这个类会被真正的实现替换掉，接口不必改。
 */
class BeaconMenu(player: Player) : AbstractMenu(player) {

    override val menuId: String = "beacon"
    override val defaultTitle: String = "&8信标设置 &7(未启用)"
    override val defaultRows: Int = 5

    override fun MenuCtx.render() {
        put(
            13, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "placeholder.material", "BEACON"),
                MenuConfig.str(menuId, "placeholder.name", "&7信标系统开发中", player),
                listOf(
                    "&8该功能尚未实装（功能清单 A4 / A6）",
                    "",
                    "&7规划中的能力：",
                    "  &8· &7Display 实体信标光柱",
                    "  &8· &7自定义物品模型（ItemModel / CustomModelData）",
                    "  &8· &7光柱自定义颜色",
                    "  &8· &7光柱转动速度",
                    "  &8· &7完全模仿原版信标的层级激活逻辑",
                    "  &8· &7TAB 栏着色器自定义（A6）",
                    "",
                    "&8当前版本不提供任何可操作项"
                ),
                where = "$menuId.placeholder"
            )
        )

        // 依赖状态自检：把「缺什么」直接告诉用户，省一轮排查
        put(
            31, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "deps.material", "HOPPER"),
                MenuConfig.str(menuId, "deps.name", "&e依赖状态", player),
                listOf(
                    "&7PacketEvents：&f${dep("com.github.retrooper.packetevents.PacketEvents")}",
                    "&7ImagePreviewer：&f${dep("me.justahuman.ImagePreviewer")}",
                    "&7SweetData：&f${dep("com.minecrafthook.SweetData")}",
                    "",
                    "&8缺失的依赖不会影响其他功能"
                ),
                where = "$menuId.deps"
            )
        )
    }

    override fun onAction(event: ClickEvent, key: String) {
        if (key == "back") MenuManager.openRoot(event.clicker)
    }

    /** 探测某个类是否存在（用 Bukkit 的插件类加载器，避免误判我们的类） */
    private fun dep(className: String): String = runCatching {
        Class.forName(className)
        "&a已安装"
    }.getOrElse { "&7未检测到" }
}
