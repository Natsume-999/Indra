package com.indra.rpg.ui

import com.indra.rpg.ui.menu.AdminMenu
import com.indra.rpg.ui.menu.BanListMenu
import com.indra.rpg.ui.menu.BeaconMenu
import com.indra.rpg.ui.menu.MainMenu
import com.indra.rpg.ui.menu.PlayerInfoMenu
import com.indra.rpg.ui.menu.TopMenu
import com.indra.rpg.util.Msg
import org.bukkit.entity.Player
import taboolib.common.platform.function.info

/**
 * 面板调度中心 —— 面板的**唯一对外入口**。
 *
 * ══ 职责 ═══════════════════════════════════════════════════════
 *   1. 注册「面板 ID → 构造函数」的映射，让指令 / 其他模块
 *      可以按字符串打开面板（`/indra menu ban`），而不必 import 每个类。
 *   2. 统一审计：谁、什么时候、打开了哪个面板。
 *   3. 容错：面板构造函数抛异常时给出可读提示，而不是把堆栈甩给玩家。
 *
 * ══ 为什么用注册表而不是 `when(id) { ... }` ═══════════════════
 *   `when` 分支每加一个面板就要改一次 MenuManager —— 这是「改动集中在一处」
 *   的反面。用 lazy 注册表后，新增面板只需在 [registerAll] 加一行，
 *   而且**用户可在 menu.yml 里关掉某个面板**（`enabled: false`）而不影响其他。
 */
object MenuManager {

    /** 主菜单的 ID（[AbstractMenu] 用它判断「要不要画返回键」） */
    const val ROOT_ID = "main"

    /** 面板 ID → 工厂函数 */
    private val factories = LinkedHashMap<String, (Player) -> AbstractMenu>()

    /** 面板 ID → 中文名（用于提示与日志） */
    private val labels = LinkedHashMap<String, String>()

    init {
        registerAll()
    }

    private fun registerAll() {
        register("main", "主菜单") { MainMenu(it) }
        register("player", "玩家信息") { PlayerInfoMenu(it) }
        register("top", "等级排行") { TopMenu(it) }
        register("ban", "封禁管理") { BanListMenu(it) }
        register("admin", "管理工具") { AdminMenu(it) }
        register("beacon", "信标设置") { BeaconMenu(it) }
    }

    private fun register(id: String, label: String, factory: (Player) -> AbstractMenu) {
        factories[id] = factory
        labels[id] = label
    }

    /** 全部面板 ID（供指令补全） */
    fun ids(): List<String> = factories.keys.toList()

    /** 面板中文名 */
    fun label(id: String): String = labels[id] ?: id

    /**
     * 按 ID 打开面板。
     *
     * @param id 面板 ID，大小写不敏感
     * @return 是否成功打开（false = ID 不存在或构建失败，提示已发给玩家）
     */
    fun open(player: Player, id: String): Boolean {
        val key = id.trim().lowercase()
        val factory = factories[key]
        if (factory == null) {
            Msg.send(player, "&c未知面板：&e$id&7（可用：${ids().joinToString(", ")}）")
            return false
        }
        // menu.yml 允许单独关掉某个面板
        if (!MenuConfig.bool(key, "enabled", true)) {
            Msg.send(player, "&c面板 &e${label(key)}&c 已被配置禁用。")
            return false
        }
        return runCatching { factory(player).open() }
            .onFailure {
                Msg.send(player, "&c打开面板失败，请查看控制台日志。")
                info("[Indra] 面板 §c$key§7 打开异常：${it.message}")
                it.printStackTrace()
            }
            .isSuccess
    }

    /** 打开主菜单（所有入口的默认落点） */
    fun openRoot(player: Player): Boolean = open(player, ROOT_ID)

    /**
     * 用**自定义工厂**打开面板。
     *
     * ── 什么时候需要它 ──────────────────────────────────────
     *   绝大多数面板只需要知道「谁在看」（[open] 就够了），
     *   但玩家信息面板还需要知道「看谁」—— 这个参数没法从 ID 推出来。
     *
     *   与其在注册表里给所有面板加一个用不上的参数位，
     *   不如给这少数派留一个直通口。
     *
     * ⚠️ 这里**不做** enabled 检查：调用方（指令层）已经知道自己在干什么，
     *    而且自定义工厂的面板未必在 [factories] 里注册。
     */
    fun openFor(player: Player, factory: (Player) -> AbstractMenu): Boolean =
        runCatching { factory(player).open() }
            .onFailure {
                Msg.send(player, "&c打开面板失败，请查看控制台日志。")
                info("[Indra] 自定义面板打开异常：${it.message}")
                it.printStackTrace()
            }
            .isSuccess

    // ══ 审计 ═══════════════════════════════════════════════

    /**
     * 面板操作审计。
     *
     * ── 为什么面板也要审计 ──────────────────────────────────
     *   面板是**绕过指令**的写操作入口（封禁面板可以直接封人）。
     *   只审计指令的话，「面板封的」在 history 表里就没有痕迹，
     *   出事故时无法复盘是谁点的。
     *
     * ── 与 ban history 的分工 ──────────────────────────────
     *   这里只记「开关面板」这种低频动作（INFO 级，进控制台）。
     *   面板里真正的业务写操作（封禁 / 解封）走 [com.indra.rpg.ban.BanApi]，
     *   那一层会写进 indra_ban_history 表 —— 两处不重复。
     */
    fun audit(player: Player, menuId: String, action: String) {
        if (!MenuConfig.bool(menuId, "audit", true)) return
        info("[Indra] 面板审计：${player.name} $action ${label(menuId)}")
    }

    /** 关服 / 重载：清空全部会话状态 */
    fun shutdown() {
        MenuState.clear()
        info("[Indra] 面板系统已卸载")
    }
}
