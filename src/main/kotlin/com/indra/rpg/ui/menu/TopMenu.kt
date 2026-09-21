package com.indra.rpg.ui.menu

import com.indra.rpg.ban.BanApi
import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.data.IndraPlayerData
import com.indra.rpg.ui.AbstractMenu
import com.indra.rpg.ui.MenuConfig
import com.indra.rpg.ui.MenuIcons
import com.indra.rpg.ui.MenuManager
import com.indra.rpg.util.Msg
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.module.ui.ClickEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 等级排行榜面板。
 *
 * ══ 分页 ════════════════════════════════════════════════
 *   内容区槽位由 menu.yml 的 `content-slots` 决定（默认前 4 行 36 格）。
 *   一页 36 名，Top 50 只要 2 页。
 *
 *   ⚠️ 这里**没有**用 TabooLib 的 `PageableChest`：
 *      `PageableChest` 会把「翻页 + 元素渲染」都收进框架，
 *      我们自己的排序 / 搜索状态反而不好接进去（框架有自己的页码字段，
 *      两个页码会打架）。自己按 [contentSlots] 切片更直接，
 *      而且排行榜的排序切换（升/降序）本来就是自定义逻辑。
 *
 * ══ 排序 ════════════════════════════════════════════════
 *   `state.descending = true`  → 等级从高到低（默认）
 *   点排序按钮切换。切换后回到第 1 页（在 [toggleSort] 里做）。
 */
class TopMenu(player: Player) : AbstractMenu(player) {

    override val menuId: String = "top"
    override val defaultTitle: String = "&8等级排行榜"
    override val defaultRows: Int = 6
    override val pageable: Boolean = true

    /** 一页最多显示多少名（预设 50 名上限，与指令 `/indra top` 一致） */
    private val maxEntries: Int = MenuConfig.int(menuId, "max-entries", 50)

    override fun MenuCtx.render() {
        val all = fetchSorted()
        // 搜索过滤（复用 MenuState 的 keyword，与封禁面板同一套交互）
        val filtered = all.filter { filterMatch(it) }
        val pageSize = pageSize()
        val total = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        // 防御：删了数据导致页数变少时，把页码夹回合法范围
        if (state.page > total) state.page = total

        val from = (state.page - 1) * pageSize
        val slice = filtered.drop(from).take(pageSize)

        if (slice.isEmpty()) {
            put(
                contentSlots().first(), null,
                MenuIcons.icon(
                    "BARRIER",
                    MenuConfig.str(menuId, "empty.name", "&7暂无排行数据", player),
                    listOf("&8还没有玩家数据，或搜索无结果"),
                    where = "$menuId.empty"
                )
            )
        } else {
            slice.forEachIndexed { i, entry ->
                val slot = contentSlots().getOrNull(i) ?: return@forEachIndexed
                val rank = from + i + 1
                put(slot, "entry.$rank", renderEntry(rank, entry))
            }
        }

        // ── 排序切换按钮（倒数第 2 行最右）──────────────────
        val sortSlot = MenuConfig.int(menuId, "nav.sort-slot", (MenuConfig.rows(menuId, defaultRows) - 2) * 9 + 8)
        put(
            sortSlot, "act.sort",
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.sort-material", "HOPPER"),
                MenuConfig.str(menuId, "nav.sort-name", "&e切换排序", player),
                listOf(
                    "&7当前：&f" + if (state.descending) "等级从高到低" else "等级从低到高",
                    "&7点击切换"
                ),
                where = "$menuId.nav.sort"
            )
        )

        // ── 搜索按钮 ──────────────────────────────────────
        val searchSlot = MenuConfig.int(menuId, "nav.search-slot", (MenuConfig.rows(menuId, defaultRows) - 2) * 9 + 2)
        put(
            searchSlot, "act.search",
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.search-material", "OAK_SIGN"),
                MenuConfig.str(menuId, "nav.search-name", "&e搜索玩家", player),
                listOf(
                    if (state.keyword.isEmpty()) "&7未设置过滤" else "&7当前过滤：&f${state.keyword}",
                    "&8点击后在聊天栏输入"
                ),
                where = "$menuId.nav.search"
            )
        )

        // ── 数据统计条 ────────────────────────────────────
        val statSlot = MenuConfig.int(menuId, "nav.stat-slot", (MenuConfig.rows(menuId, defaultRows) - 2) * 9 + 4)
        put(
            statSlot, null,
            MenuIcons.icon(
                MenuConfig.str(menuId, "nav.stat-material", "BOOK"),
                MenuConfig.str(menuId, "nav.stat-name", "&b统计", player),
                listOf(
                    "&7玩家总数：&f${all.size}",
                    "&7当前过滤后：&f${filtered.size}",
                    "&7第 &f${state.page} &7/ &f$total &7页"
                ),
                where = "$menuId.nav.stat"
            )
        )
    }

    override fun onAction(event: ClickEvent, key: String) {
        when {
            key == "act.sort" -> toggleSort()
            key == "act.search" -> beginSearch(event.clicker)
            key == "prev" -> turnPage(-1)
            key == "next" -> turnPage(1)
            key == "back" -> MenuManager.openRoot(event.clicker)
            key.startsWith("entry.") -> {
                // 点某个名次 → 打开该玩家的信息面板
                val rank = key.removePrefix("entry.").toIntOrNull() ?: return
                val entry = fetchSorted().getOrNull(rank - 1) ?: return
                val target = Bukkit.getPlayerExact(entry.name) ?: run {
                    Msg.send(event.clicker, "&7&f${entry.name} &7不在线，仅能查看缓存数据。")
                    return
                }
                MenuManager.open(event.clicker, "player")
                Msg.send(event.clicker, "&7已打开 &f${target.name} &7的信息面板。")
            }
        }
    }

    /** 取排序后的全量数据 */
    private fun fetchSorted(): List<IndraPlayerData> {
        val list = runCatching { DataManager.topLevel(maxEntries) }.getOrDefault(emptyList())
        return if (state.descending) list else list.reversed()
    }

    /** 关键词过滤：匹配玩家名（忽略大小写） */
    private fun filterMatch(data: IndraPlayerData): Boolean {
        if (state.keyword.isEmpty()) return true
        return data.name.contains(state.keyword, ignoreCase = true)
    }

    private fun beginSearch(clicker: Player) {
        // ⚠️ 先关面板再登记输入：面板占着焦点时聊天事件语义混乱
        clicker.closeInventory()
        taboolib.common.platform.function.submit(delay = 2) {
            com.indra.rpg.ui.MenuInputListener.await(clicker) { keyword ->
                // 频道复用：写入 top 面板的会话状态，然后重开
                val s = com.indra.rpg.ui.MenuState.get(clicker.uniqueId, menuId)
                s.keyword = keyword
                s.page = 1
                MenuManager.open(clicker, menuId)
            }
        }
    }

    /** 渲染一位玩家的条目 —— 前三名给特殊材质 */
    private fun renderEntry(rank: Int, data: IndraPlayerData) = MenuIcons.icon(
        when (rank) {
            1 -> MenuConfig.str(menuId, "entry.material-1", "GOLD_BLOCK")
            2 -> MenuConfig.str(menuId, "entry.material-2", "IRON_BLOCK")
            3 -> MenuConfig.str(menuId, "entry.material-3", "COPPER_BLOCK")
            else -> MenuConfig.str(menuId, "entry.material", "PAPER")
        },
        MenuConfig.str(menuId, "entry.name", "&f#{rank} &e{player}", player)
            .replace("{rank}", rank.toString())
            .replace("{player}", data.name),
        MenuConfig.list(
            menuId, "entry.lore",
            listOf(
                "&7等级：&eLv.{level} &8/ &7{maxLevel}",
                "&7总经验：&e{expTotal}",
                "&7击杀 / 死亡：&a{kills} &8/ &c{deaths}",
                "&7最后在线：&f{lastSeen}",
                "",
                "&8点击查看该玩家详情"
            ),
            player
        ).map {
            it.replace("{level}", data.level.toString())
                .replace("{maxLevel}", IndraConfig.maxLevel.toString())
                .replace("{expTotal}", data.expTotal.toString())
                .replace("{kills}", data.killCount.toString())
                .replace("{deaths}", data.deathCount.toString())
                .replace("{lastSeen}", fmtTime(data.lastSeen))
                .replace("{player}", data.name)
        },
        shiny = rank <= 3,
        where = "$menuId.entry"
    )

    private fun fmtTime(millis: Long): String =
        if (millis <= 0) "&7未知"
        else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
