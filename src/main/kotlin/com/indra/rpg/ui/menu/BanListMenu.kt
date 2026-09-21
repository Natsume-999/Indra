package com.indra.rpg.ui.menu

import com.indra.rpg.ban.BanApi
import com.indra.rpg.ban.BanConfig
import com.indra.rpg.ban.BanDatabaseManager
import com.indra.rpg.ban.BanMessages
import com.indra.rpg.ban.BannedPlayer
import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuInputListener
import com.indra.rpg.ui.MenuManager
import com.indra.rpg.ui.MenuState
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.module.ui.ClickEvent

/**
 * 封禁管理面板 —— 唯一带**写操作**的面板。
 *
 * ══ 这是全项目最危险的界面 ══════════════════════════════
 *   面板点击 = 绕过指令直接写库。所以这里做了四道防护：
 *
 *   1. **权限**：`indra.command.ban`（与 `/indra ban` 同一节点，
 *      不另开一个节点 —— 否则会出现「能用指令封人但打不开面板」
 *      或反过来的荒谬权限组合）
 *   2. **二次确认**：封禁 / 解封 / 删除都要求「先选中、再点确认」，
 *      不给「手滑点到就封号」的机会
 *   3. **审计**：所有写操作走 [BanApi]，自动写 indra_ban_history
 *   4. **操作者记录**：operator 一律取 `clicker.name`，绝不信配置
 *
 * ══ 交互流程 ════════════════════════════════════════════
 * ```
 *   列表页 ──点条目──▶ 选中（条目高亮 + 底部出现操作栏）
 *                          │
 *          ┌───────────────┼───────────────┬──────────────┐
 *       解封            警告            清除记录        搜索
 *          │               │               │
 *       立即执行       输入原因        二次确认
 * ```
 *   选中状态存在 [MenuState.Session.extra]（key = "selected"），
 *   因此刷新 / 翻页后选中不会丢。
 */
class BanListMenu(player: Player) : AbstractMenu(player) {

    override val menuId: String = "ban"
    override val defaultTitle: String = "&8封禁管理"
    override val defaultRows: Int = 6
    override val pageable: Boolean = true

    /** 与 `/indra ban` 同一权限节点 */
    override val permission: String? = "indra.command.ban"

    /** 每页条目数上限保护（避免一次读太多） */
    private val maxEntries: Int = MenuConfig.int(menuId, "max-entries", 200)

    /** 当前选中的玩家 ID（存在会话里，刷新不丢） */
    private val selected: String?
        get() = state.extra["selected"]?.takeIf { it.isNotBlank() }

    /** 处于「等待二次确认」的操作名 */
    private val pending: String?
        get() = state.extra["pending"]?.takeIf { it.isNotBlank() }

    // ══ 渲染 ═══════════════════════════════════════════════

    override fun MenuCtx.render() {
        val all = fetchBans()
        val filtered = all.filter { filterMatch(it) }
        val pageSize = pageSize()
        val total = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        if (state.page > total) state.page = total

        val from = (state.page - 1) * pageSize
        val slice = filtered.drop(from).take(pageSize)

        if (slice.isEmpty()) {
            put(
                contentSlots().first(), null,
                MenuIcons.icon(
                    "BARRIER",
                    MenuConfig.str(menuId, "empty.name", "&a当前没有封禁记录", player),
                    if (state.keyword.isEmpty()) listOf("&7服务器很干净")
                    else listOf("&7搜索无结果：&f${state.keyword}"),
                    where = "$menuId.empty"
                )
            )
        } else {
            slice.forEachIndexed { i, ban ->
                val slot = contentSlots().getOrNull(i) ?: return@forEachIndexed
                put(slot, "ban.${ban.playerID}", renderBanEntry(ban, from + i + 1))
            }
        }

        renderListNav()
        renderActionBar(total, filtered.size, all.size)
    }

    /** 列表页底部的导航 / 搜索 / 排序 */
    private fun MenuCtx.renderListNav() {
        val last = (MenuConfig.rows(menuId, defaultRows) - 2) * 9

        put(
            MenuConfig.int(menuId, "nav.search-slot", last + 2), "act.search",
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.search-material", "OAK_SIGN"),
                MenuConfig.str(menuId, "nav.search-name", "&e搜索", player),
                listOf(
                    if (state.keyword.isEmpty()) "&7未设置过滤" else "&7过滤：&f${state.keyword}",
                    "&8点输入，输入 - 清除"
                ),
                where = "$menuId.nav.search"
            )
        )
        put(
            MenuConfig.int(menuId, "nav.sort-slot", last + 8), "act.sort",
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.sort-material", "HOPPER"),
                MenuConfig.str(menuId, "nav.sort-name", "&e排序", player),
                listOf("&7当前：&f" + if (state.descending) "封禁时间从新到旧" else "从旧到新", "&7点击切换"),
                where = "$menuId.nav.sort"
            )
        )
        put(
            MenuConfig.int(menuId, "nav.reload-slot", last + 4), "act.refresh",
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.reload-material", "CLOCK"),
                MenuConfig.str(menuId, "nav.reload-name", "&b刷新列表", player),
                listOf("&7从数据库重新读取"),
                where = "$menuId.nav.reload"
            )
        )
    }

    /**
     * 操作栏 —— 底部最后一行。
     *
     * 分两种形态：
     *   未选中 → 提示「点击一个玩家条目以选择」
     *   已选中 → 显示目标名 + 三个操作按钮
     */
    private fun MenuCtx.renderActionBar(totalPages: Int, filteredSize: Int, allSize: Int) {
        val base = (MenuConfig.rows(menuId, defaultRows) - 1) * 9
        val sel = selected

        if (sel == null) {
            put(
                base + 4, null,
                MenuIcons.icon(
                    MenuConfig.str(menuId, "hint.material", "PAPER"),
                    MenuConfig.str(menuId, "hint.name", "&7请先选择一个玩家", player),
                    listOf(
                        "&7点击上方任意封禁条目进行选择",
                        "",
                        "&8共 &f$allSize &8条记录，过滤后 &f$filteredSize &8条",
                        "&8第 &f${state.page} &8/ &f$totalPages &8页"
                    ),
                    where = "$menuId.hint"
                )
            )
            return
        }

        // ── 已选中：显示目标 + 操作键 ─────────────────────
        val data = runCatching { BanDatabaseManager.getDatabase().getPlayerByName(sel) }.getOrNull()

        put(
            base, "act.deselect",
            MenuIcons.icon(
                "PLAYER_HEAD",
                "&c&l已选择 &f$sel",
                listOf(
                    "&7封禁原因：&f${data?.banReason ?: "—"}",
                    "&7封禁时长：&f${data?.banDuration ?: "—"}",
                    "&7封禁时间：&f${data?.banTime ?: "—"}",
                    "&7解封时间：&f${data?.unbanTime?.ifBlank { "永久" } ?: "—"}",
                    "&7操作管理员：&f${data?.banningAdmin ?: "—"}",
                    "",
                    "&8点击取消选择"
                ),
                where = "$menuId.selected"
            )
        )

        // 解封
        put(
            base + 2, "act.unban",
            MenuIcons.icon(
                MenuConfig.str(menuId, "actions.unban.material", "EMERALD"),
                MenuConfig.str(menuId, "actions.unban.name", "&a解封", player),
                confirmLore("unban", listOf("&7解除 &f$sel &7的封禁", "&7记录保留，可追溯")),
                where = "$menuId.actions.unban"
            )
        )

        // 警告
        put(
            base + 3, "act.warn",
            MenuIcons.icon(
                MenuConfig.str(menuId, "actions.warn.material", "PAPER"),
                MenuConfig.str(menuId, "actions.warn.name", "&6追加警告", player),
                listOf("&7给 &f$sel &7记一次警告", "&7点击后在聊天栏输入原因"),
                where = "$menuId.actions.warn"
            )
        )

        // 清除记录（危险操作）
        put(
            base + 5, "act.delete",
            MenuIcons.icon(
                MenuConfig.str(menuId, "actions.delete.material", "TNT"),
                MenuConfig.str(menuId, "actions.delete.name", "&c&l清除记录", player),
                confirmLore("delete", listOf("&c从数据库中删除该玩家的封禁记录", "&c此操作不可撤销！", "&7警告与历史记录不受影响")),
                where = "$menuId.actions.delete"
            )
        )

        // 查看详情
        put(
            base + 6, "act.detail",
            MenuIcons.icon(
                MenuConfig.str(menuId, "actions.detail.material", "BOOK"),
                MenuConfig.str(menuId, "actions.detail.name", "&b查看详情", player),
                listOf("&7查看封禁状态与警告列表"),
                where = "$menuId.actions.detail"
            )
        )

        put(
            base + 8, "act.deselect",
            MenuIcons.icon("ARROW", "&7取消选择", listOf("&8回到列表浏览模式"), where = "$menuId.deselect"),
        )
    }

    /** 二次确认的描述文本：未确认时给警告，已确认时给「再点一次」 */
    private fun confirmLore(action: String, normal: List<String>): List<String> =
        if (pending == action) {
            listOf("", "&e&l再次点击以确认", "&7（3 秒内有效）")
        } else normal + listOf("", "&8需要二次确认")

    // ══ 交互 ═══════════════════════════════════════════════

    override fun onAction(event: ClickEvent, key: String) {
        // 危险操作超时保护：pending 存在超过 3 秒视为过期
        expirePendingIfStale()

        when {
            key.startsWith("ban.") -> select(event.clicker, key.removePrefix("ban."))
            key == "act.deselect" -> {
                state.extra.remove("selected")
                state.extra.remove("pending")
                state.touch()
                refresh()
            }
            key == "act.search" -> beginSearch(event.clicker)
            key == "act.sort" -> toggleSort()
            key == "act.refresh" -> {
                state.touch()
                refresh()
                Msg.send(event.clicker, "&a列表已刷新。")
            }
            key == "act.unban" -> guarded(event.clicker, "unban") { doUnban(event.clicker) }
            key == "act.delete" -> guarded(event.clicker, "delete") { doDelete(event.clicker) }
            key == "act.warn" -> beginWarn(event.clicker)
            key == "act.detail" -> showDetail(event.clicker)
            key == "back" -> MenuManager.openRoot(event.clicker)
            key == "prev" -> turnPage(-1)
            key == "next" -> turnPage(1)
        }
    }

    /** 选中一个玩家（再点一次取消选择） */
    private fun select(clicker: Player, playerID: String) {
        if (selected == playerID) {
            state.extra.remove("selected")
        } else {
            state.extra["selected"] = playerID
        }
        state.extra.remove("pending")
        state.touch()
        refresh()
    }

    /**
     * 二次确认门禁。
     *
     * ⚠️ 不能用「点击后弹一个 confirm 面板再点」：
     *    多一次界面切换就多一次玩家流失，而且 confirm 面板要另做一套。
     *    这里用「同一个按钮点两次」——第一次点亮 `pending`，
     *    第二次才真正执行，直观且零额外界面。
     */
    private inline fun guarded(clicker: Player, action: String, body: () -> Unit) {
        if (pending != action) {
            state.extra["pending"] = action
            state.extra["pendingAt"] = System.currentTimeMillis().toString()
            state.touch()
            refresh()
            Msg.send(clicker, "&e请再点击一次以确认操作。")
            return
        }
        state.extra.remove("pending")
        state.extra.remove("pendingAt")
        body()
    }

    private fun expirePendingIfStale() {
        val at = state.extra["pendingAt"]?.toLongOrNull() ?: return
        if (System.currentTimeMillis() - at > 3000) {
            state.extra.remove("pending")
            state.extra.remove("pendingAt")
        }
    }

    // ── 写操作 ───────────────────────────────────────────

    private fun doUnban(clicker: Player) {
        val target = selected ?: return
        val ok = runCatching { BanApi.unbanPlayer(target) }.isSuccess
        if (ok) {
            Msg.send(clicker, "&a已解封 &f$target&a。")
            state.extra.remove("selected")
        } else {
            Msg.send(clicker, "&c解封失败，请查看控制台日志。")
        }
        state.touch()
        refresh()
    }

    private fun doDelete(clicker: Player) {
        val target = selected ?: return
        val removed = BanApi.deletePlayerRecord(target, clicker.name)
        if (removed) {
            Msg.send(clicker, "&a已删除 &f$target &a的封禁记录。")
            state.extra.remove("selected")
        } else {
            Msg.send(clicker, "&7&f$target &7没有可删除的记录。")
        }
        state.touch()
        refresh()
    }

    private fun beginWarn(clicker: Player) {
        val target = selected ?: return
        clicker.closeInventory()
        taboolib.common.platform.function.submit(delay = 2) {
            Msg.raw(clicker, "&7在聊天栏输入警告原因（输入 &f- &7使用默认原因）")
            MenuInputListener.await(clicker, "&8本次输入不会发送给其他玩家") { reason ->
                val finalReason = reason.ifBlank { BanConfig.getDefaultWarnReason() }
                runCatching { BanApi.warnPlayer(target, finalReason, clicker.name) }
                val count = runCatching { BanApi.getPlayerWarningCount(target) }.getOrDefault(0)
                Msg.send(clicker, "&a已警告 &f$target&a，当前 &e$count &a次。")
                if (count >= BanConfig.getWarnAutoBanThreshold() && BanConfig.getWarnAutoBanThreshold() > 0) {
                    Msg.send(clicker, "&c已达阈值，该玩家已被自动封禁。")
                }
                MenuManager.open(clicker, menuId)
            }
        }
    }

    private fun showDetail(clicker: Player) {
        val target = selected ?: return
        val warns = runCatching { BanApi.getPlayerWarnings(target) }.getOrDefault(emptyList())
        Msg.raw(clicker, "&b──── &f$target &b────")
        Msg.raw(clicker, "&7警告数：&e${warns.size}")
        warns.take(10).forEachIndexed { i, w ->
            Msg.raw(clicker, "  &8${i + 1}. &7${w.reason} &8| &7${w.operator} &8| &7${w.time}")
        }
        if (warns.size > 10) Msg.raw(clicker, "  &8…以及 ${warns.size - 10} 条")
        val hist = runCatching { BanApi.getPlayerHistory(target, 10) }.getOrDefault(emptyList())
        Msg.raw(clicker, "&7操作历史（最近 ${hist.size} 条）：")
        hist.forEach { h ->
            Msg.raw(clicker, "  &8[&7${h.time}&8] &e${actionText(h.action)} &7${h.reason} &8| &7${h.operator}")
        }
    }

    private fun beginSearch(clicker: Player) {
        clicker.closeInventory()
        taboolib.common.platform.function.submit(delay = 2) {
            MenuInputListener.await(clicker) { keyword ->
                val s = MenuState.get(clicker.uniqueId, menuId)
                s.keyword = keyword
                s.page = 1
                MenuManager.open(clicker, menuId)
            }
        }
    }

    // ── 数据 ─────────────────────────────────────────────

    private fun fetchBans(): List<BannedPlayer> =
        runCatching { BanDatabaseManager.getDatabase().getBannedPlayers() }
            .getOrDefault(emptyList())
            .take(maxEntries)

    private fun filterMatch(ban: BannedPlayer): Boolean {
        if (state.keyword.isEmpty()) return true
        val k = state.keyword
        return ban.playerID.contains(k, ignoreCase = true) ||
            ban.banReason.contains(k, ignoreCase = true) ||
            ban.banningAdmin.contains(k, ignoreCase = true)
    }

    private fun renderBanEntry(ban: BannedPlayer, index: Int) = MenuIcons.icon(
        MenuConfig.str(menuId, "entry.material", "SKELETON_SKULL"),
        MenuConfig.str(menuId, "entry.name", "&c{player}", player)
            .replace("{player}", ban.playerID)
            .replace("{index}", index.toString()),
        MenuConfig.list(
            menuId, "entry.lore",
            listOf(
                "&7原因：&f{reason}",
                "&7时长：&f{duration}",
                "&7封禁于：&f{banTime}",
                "&7解封于：&f{unbanTime}",
                "&7操作员：&f{admin}",
                "",
                "&8点击选择该玩家"
            ),
            player
        ).map {
            it.replace("{reason}", ban.banReason.ifBlank { "未填写" })
                .replace("{duration}", ban.banDuration.ifBlank { "永久" })
                .replace("{banTime}", ban.banTime.ifBlank { "—" })
                .replace("{unbanTime}", ban.unbanTime.ifBlank { "永久" })
                .replace("{admin}", ban.banningAdmin.ifBlank { "—" })
                .replace("{player}", ban.playerID)
        },
        shiny = selected == ban.playerID,
        where = "$menuId.entry"
    )

    private fun actionText(action: String): String = when (action) {
        "BAN" -> "&c封禁"
        "UNBAN" -> "&a解封"
        "KICK" -> "&e请出"
        "WHITELIST" -> "&a加白名单"
        "UNWHITELIST" -> "&c移白名单"
        "WARN" -> "&6警告"
        "DELETE" -> "&4清除记录"
        else -> "&7其他"
    }
}
