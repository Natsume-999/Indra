package com.indra.rpg.ui

import com.indra.rpg.util.Msg
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.type.Chest
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu

/**
 * 所有 Indra 面板的基类。
 *
 * ══ 它解决什么问题 ═══════════════════════════════════════════════
 *   逐个手写面板会有大量重复：铺底色、装返回键、装翻页键、算空槽位、
 *   防拖拽、关闭时清状态……任何一处漏了都是同一种 bug。
 *   这里把这些固化成模板方法，子类只回答三个问题：
 *
 *     1. [menuId]   我是哪个面板（决定 menu.yml 读哪一段、状态存哪个 key）
 *     2. [render]   我的内容长什么样（往 [MenuCtx] 里塞图标）
 *     3. [onAction] 点了按钮干什么
 *
 * ══ 与 TabooLib bukkit-ui 的关系 ════════════════════════════════
 *   [Chest] 是 bukkit-ui 的接口，`ChestImpl` 由 `Menu.Companion` 的
 *   静态块自动注册为它的实现 —— 因此**不要**自己去 `new ChestImpl()`，
 *   直接用 `openMenu<Chest>` 让框架挑实现即可。
 *
 * ══ 防物品拖走（双层防护，两层都要）════════════════════════════
 *   第一层：`handLocked(true)`  —— 阻止「光标拖着物品点进面板槽位」
 *   第二层：`setCancelled(true)` —— 阻止普通点击 / Shift 点击搬运
 *   只做第二层会漏掉 drag 事件（`ClickType.DRAG`），物品会被拖进去；
 *   只做第一层会漏掉 shift-click 整摞搬运。两层叠加才严实。
 *
 * ══ 刷新语义 ═══════════════════════════════════════════════════
 *   [refresh] 重开面板（TabooLib 没有「原地重绘」的公开 API）。
 *   重开会触发 onClose → 我们的 onClose 里做了「如果是刷新则不结束会话」
 *   的判定，所以页码 / 排序 / 搜索词不会丢。
 */
abstract class AbstractMenu protected constructor(
    protected val player: Player,
) {

    /** 面板唯一 ID：既是 menu.yml 的配置段名，也是会话状态的 key */
    abstract val menuId: String

    /** 默认标题（menu.yml 没配时用这个） */
    protected abstract val defaultTitle: String

    /** 默认行数 */
    protected open val defaultRows: Int = 6

    /** 是否需要管理员权限才能打开 */
    open val permission: String? = null

    /** 是否启用分页（子类覆写 [pageData] 时应当同时置 true） */
    protected open val pageable: Boolean = false

    /** 本面板的会话状态（页码 / 排序 / 搜索词） */
    protected val state: MenuState.Session get() = MenuState.get(player.uniqueId, menuId)

    /** 是否正在自我刷新（用于区分「翻页重开」与「玩家真的关了」） */
    @Volatile
    private var refreshing = false

    // ══ 子类实现 ═══════════════════════════════════════════

    /** 往 ctx 里塞图标。ctx 已经预铺好底色和导航键 */
    protected abstract fun MenuCtx.render()

    /** 点击事件总入口。默认什么都不做 */
    protected open fun onAction(event: ClickEvent, key: String) {}

    /**
     * 分页数据源。
     *
     * 返回「当前页要展示的条目」；返回空列表表示没有内容，
     * 基类会自动放一个「暂无数据」提示。
     * 未启用分页的面板不必覆写。
     */
    protected open fun pageData(): List<Any> = emptyList()

    // ══ 打开 / 刷新 / 关闭 ═════════════════════════════════

    /** 打开面板（带权限检查） */
    fun open() {
        val perm = permission
        if (perm != null && !player.hasPermission(perm)) {
            Msg.send(player, "&c你没有权限打开这个面板。")
            return
        }
        MenuManager.audit(player, menuId, "OPEN")
        player.openMenu<Chest>(title()) {
            rows(MenuConfig.rows(menuId, defaultRows))
            handLocked(true)

            val ctx = MenuCtx(this)
            // 导航键先铺，子类的 render() 后铺 —— 这样允许子类有意覆盖某个导航槽位
            renderNavigation(ctx)
            ctx.render()
            ctx.apply()

            // 兜底取消：任何未被显式处理的点击都不允许搬运物品
            //
            // ⚠️ 这里**不要**调 event.setCancelled(true)：
            //    Java 方法 `setCancelled(boolean)` 的 Kotlin 语法糖是
            //    属性写法 `event.isCancelled = true`，直接写 setCancelled(...)
            //    会「unresolved reference」。而且 Chest.onClick 内部已经把
            //    点击事件 cancel 掉了，这里再设一次是多余的。
            //
            // ★ `Chest.onClick` 只有两个重载，**都必须带首参**：
            //     onClick(slot: Int,  run: (ClickEvent) -> Unit)
            //     onClick(handled: Boolean, run: (ClickEvent) -> Unit)
            //   不存在 `onClick(run)` 单参形式。
            //   这里传 true 表示「已处理」—— 框架据此 cancel 掉原版点击。
            onClick(true) { event ->
                val key = ctx.keyAt(event.rawSlot)
                if (key != null) onAction(event, key)
            }

            onClose(true, true) { _ ->
                // 自我刷新 → 保留会话；真关闭 → 清状态
                if (!refreshing) {
                    MenuState.end(player.uniqueId, menuId)
                    MenuManager.audit(player, menuId, "CLOSE")
                }
            }
        }
    }

    /**
     * 原地刷新 —— 重开面板并保留页码 / 排序 / 搜索词。
     *
     * ⚠️ 必须延迟 1 tick 再开：当前仍在 InventoryClickEvent 的处理栈里，
     *    此时直接 `player.openInventory()` 会被服务端丢弃
     *    （Bukkit 在事件处理期间禁止切换视图）。
     */
    fun refresh() {
        refreshing = true
        taboolib.common.platform.function.submit(delay = 1) {
            refreshing = false
            open()
        }
    }

    /** 关闭面板（走服务端正常关闭流程，会触发 onClose 清状态） */
    fun close() {
        player.closeInventory()
    }

    // ══ 模板内部 ═══════════════════════════════════════════

    protected fun title(): String = MenuIcons.color(MenuConfig.title(menuId, defaultTitle, player))

    /**
     * 铺导航键：返回键（所有面板）+ 翻页键（分页面板）。
     *
     * 槽位取自 menu.yml，服主可改。默认：
     *   返回 → 第 n 行第 1 格
     *   上一页 → 第 n 行第 4 格
     *   下一页 → 第 n 行第 6 格
     */
    private fun renderNavigation(ctx: MenuCtx) {
        val rows = MenuConfig.rows(menuId, defaultRows)
        val last = (rows - 1) * 9

        if (menuId != MenuManager.ROOT_ID) {
            val back = MenuConfig.int(menuId, "nav.back-slot", last)
            ctx.put(
                back, "back",
                MenuIcons.icon(
                    MenuConfig.str(menuId, "nav.back-material", "ARROW"),
                    MenuConfig.str(menuId, "nav.back-name", "&7← 返回", player),
                    where = "$menuId.nav.back"
                )
            )
        }

        if (!pageable) return

        val prev = MenuConfig.int(menuId, "nav.prev-slot", last + 3)
        val next = MenuConfig.int(menuId, "nav.next-slot", last + 5)
        val page = state.page
        val total = totalPages()

        // 首页 / 末页把翻页键变灰（换材质 + 去掉点击 key）——
        // 比「点了没反应」友好：玩家一眼知道到头了
        val canPrev = page > 1
        val canNext = page < total

        ctx.put(
            prev, if (canPrev) "prev" else null,
            MenuIcons.icon(
                if (canPrev) MenuConfig.str(menuId, "nav.prev-material", "PAPER")
                else "GRAY_DYE",
                MenuConfig.str(menuId, "nav.prev-name", "&e← 上一页", player),
                listOf("&7第 &f$page &7/ &f$total &7页"),
                where = "$menuId.nav.prev"
            )
        )
        ctx.put(
            next, if (canNext) "next" else null,
            MenuIcons.icon(
                if (canNext) MenuConfig.str(menuId, "nav.next-material", "PAPER")
                else "GRAY_DYE",
                MenuConfig.str(menuId, "nav.next-name", "&e下一页 →", player),
                listOf("&7第 &f$page &7/ &f$total &7页"),
                where = "$menuId.nav.next"
            )
        )
    }

    /** 总页数（未启分页恒为 1） */
    protected fun totalPages(): Int {
        if (!pageable) return 1
        val size = pageSize()
        if (size <= 0) return 1
        val count = pageData().size
        return ((count + size - 1) / size).coerceAtLeast(1)
    }

    /**
     * 每页容量 = 内容槽位数。
     *
     * 从 menu.yml 的 `content-slots` 读；没配就按「整个容器减最后一行导航栏」推算。
     */
    protected fun pageSize(): Int {
        val slots = MenuConfig.slots(menuId, "content-slots", emptyList())
        if (slots.isNotEmpty()) return slots.size
        val rows = MenuConfig.rows(menuId, defaultRows)
        return ((rows - 1) * 9).coerceAtLeast(9)
    }

    /** 内容区槽位（分页面板渲染条目用） */
    protected fun contentSlots(): List<Int> {
        val explicit = MenuConfig.slots(menuId, "content-slots", emptyList())
        if (explicit.isNotEmpty()) return explicit
        val rows = MenuConfig.rows(menuId, defaultRows)
        return (0 until (rows - 1) * 9).toList()
    }

    /**
     * 翻页。
     *
     * 页码变化后**不重置**排序与搜索词 —— 这正是把状态放在
     * [MenuState] 而不是类字段的意义。
     */
    protected fun turnPage(delta: Int) {
        val target = (state.page + delta).coerceIn(1, totalPages())
        if (target == state.page) return
        state.page = target
        state.touch()
        refresh()
    }

    /** 切排序（升/降往复），并回到第 1 页 */
    protected fun toggleSort() {
        state.descending = !state.descending
        state.page = 1
        state.touch()
        refresh()
    }

    /** 供指令 / 搜索监听写入关键词 */
    fun setKeyword(text: String) {
        state.keyword = text.trim()
        state.page = 1
        state.touch()
        refresh()
    }

    /**
     * 构造一个符合 `Chest.set` 要求的点击回调。
     *
     * ★ 真包签名（javap 核实 bukkit-ui-6.3.0）：
     *     `onClick: Function1<? super ClickEvent, Unit>`
     *   在 Kotlin 侧**等价于值参数 lambda** `(ClickEvent) -> Unit`。
     *
     *   它**不是**接收者 lambda `ClickEvent.() -> Unit` ——
     *   真包上写 `onClick { println(this.rawSlot) }` 会报
     *   `unresolved reference 'rawSlot'`（`this` 指向外层对象，不是事件），
     *   必须写 `onClick { println(it.rawSlot) }`。
     *   两者在源码里长得像，但类型互不兼容、不可互相赋值。
     */
    private fun clickHandler(key: String): (ClickEvent) -> Unit = { event ->
        onAction(event, key)
    }

    // ══ 图标写入缓冲区 ═════════════════════════════════════
    /**
     * 图标缓冲区 —— 子类通过它「声明」图标，由 [apply] 统一写入 Chest。
     *
     * ── 为什么要缓冲 ────────────────────────────────────────
     *   `Chest.set(slot, item){}` 每次调用都会在面板内部登记一个点击回调，
     *   组合槽位时容易写出重复登记（同一个槽位被 set 两次 → 点击回调跑两遍）。
     *   先在 Map 里合并（同槽位后写覆盖先写），最后一次性写入，天然去重。
     */
    inner class MenuCtx(private val chest: Chest) {

        /** slot → (点击 key, 图标)。key 为 null 表示纯装饰，点了不做事 */
        private val buffer = LinkedHashMap<Int, Pair<String?, ItemStack>>()

        /** 内容区槽位（分页用，由基类填好） */
        internal val contentSlots: List<Int> = contentSlots()

        /** 已占用的槽位（算空槽位铺底色用） */
        internal val used = mutableSetOf<Int>()

        /** 点击 key 索引：rawSlot → key，供 onClick 分发 */
        internal val keys = HashMap<Int, String>()

        /** 往指定槽位放图标。[key] 非空则该图标可点 */
        fun put(slot: Int, key: String?, item: ItemStack) {
            if (slot !in 0..53) return
            buffer[slot] = key to item
            used += slot
            // 先清后加：同槽位被 put 两次时，旧的 key 必须失效，
            // 否则点击会同时命中新旧两个 key（点击回调跑两遍）。
            //
            // ⚠️ 这里按**值**删（it.value == key），不是按槽位删。
            //    因为同一个 key 可能被放在别的槽位上，两个槽位都得失效，
            //    否则玩家点旧槽位仍会触发已经不存在的图标的行为。
            //    key == null（纯装饰）不需要清理：它从未进过 keys。
            if (key != null) {
                keys.entries.removeIf { it.value == key }
                keys[slot] = key
            } else {
                // 纯装饰：该槽位即便之前有 key，也必须失效
                keys.remove(slot)
            }
        }

        /**
         * 覆盖式写入（明确表达「我要抢这个槽位的点击」）。
         * 与 [put] 的差别只在语义，行为一致。
         */
        fun override(slot: Int, key: String, item: ItemStack) = put(slot, key, item)

        /** 本面板所有已登记的点击 key */
        fun keyAt(rawSlot: Int): String? = keys[rawSlot]

        /** 内容区第 n 个槽位（分页渲染用） */
        fun contentSlot(index: Int): Int? = contentSlots.getOrNull(index)

        /** 底板材质（menu.yml 可配） */
        fun fillerMaterial(): String =
            MenuConfig.str(menuId, "filler-material", "GRAY_STAINED_GLASS_PANE")

        /**
         * 把缓冲区刷进 Chest，并自动铺满剩余空槽位。
         *
         * ⚠️ 顺序：先 apply 内容 → 再算空槽 → 最后铺底色。
         *    反过来的话底色会盖掉先写的内容。
         */
        internal fun apply() {
            val rows = MenuConfig.rows(menuId, defaultRows)
            val maxSlot = rows * 9 - 1

            // 1) 内容
            //
            // ★ `Chest.set(slot, item, onClick)` 的 onClick 是
            //     `(ClickEvent) -> Unit`（值参数），见 [clickHandler] 的说明。
            buffer.forEach { (slot, pair) ->
                val key = pair.first
                val item = pair.second
                if (key == null) {
                    chest.set(slot, item)
                } else {
                    chest.set(slot, item, clickHandler(key))
                }
            }

            // 2) 剩余空槽铺底色（不登记 key → 点了只是被取消，不做事）
            val empty = (0..maxSlot).filter { it !in used }
            MenuIcons.filler(empty, fillerMaterial()).forEach { (slot, item) ->
                chest.set(slot, item)
            }
        }
    }
}
