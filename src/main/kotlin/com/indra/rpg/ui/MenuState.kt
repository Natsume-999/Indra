package com.indra.rpg.ui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 面板的**会话状态** —— 每个玩家每类面板一份，随关闭丢弃。
 *
 * ── 为什么状态要独立于面板实例 ──────────────────────────
 *   TabooLib 的分页面板（PageableChest）每次「刷新 / 翻页」都会重建
 *   一部分图标，但**不会**把我们的业务状态（当前排序、搜索词）还回来。
 *   如果把排序 / 搜索词存在面板实例字段里，每次翻页都会重置成默认值，
 *   玩家会看到「翻到第 2 页，排序突然乱了」。
 *   所以这类跨刷新的状态必须挂在一个**比面板活得更久**的地方。
 *
 * ── 生命周期 ────────────────────────────────────────────
 *   面板关闭（onClose）时由 [end] 清除。
 *   另有一道 [sweep] 兜底：极端情况下 close 回调没触发（玩家掉线、
 *   服务端被 kill 前发的 QUIT），残留状态会在下次打开时被覆盖，
 *   并且 [sweep] 会顺手清掉超过 [STALE_MILLIS] 的孤儿条目。
 *
 * ── 线程安全 ────────────────────────────────────────────
 *   用 ConcurrentHashMap：onClose 回调可能在异步线程触发
 *   （TabooLib 的 onClose 有一个 async 参数），并发写不会炸。
 */
object MenuState {

    /** 状态多久没被碰过就视为孤儿（毫秒）。10 分钟足够覆盖任何正常操作间隔。 */
    private const val STALE_MILLIS = 10 * 60 * 1000L

    /** key = 玩家 UUID + "|" + 面板 ID（同一玩家可同时开多个不同类型面板） */
    private val states = ConcurrentHashMap<String, Session>()

    /** 一个面板会话的全部可变状态 */
    class Session {
        /** 当前页码（从 1 开始） */
        var page: Int = 1

        /** 排序方向：true = 降序。各面板自行解释「按什么排」 */
        var descending: Boolean = true

        /** 搜索关键词，空串 = 不过滤 */
        var keyword: String = ""

        /** 附加状态（各面板自用，例如封禁面板选中的玩家） */
        val extra: MutableMap<String, String> = mutableMapOf()

        /** 最后活跃时间，供 [sweep] 判断孤儿 */
        var touched: Long = System.currentTimeMillis()

        fun touch() { touched = System.currentTimeMillis() }
    }

    private fun key(player: UUID, menuId: String) = "$player|$menuId"

    /** 取（不存在则新建）该玩家在该面板上的会话状态 */
    fun get(player: UUID, menuId: String): Session {
        sweep()
        return states.computeIfAbsent(key(player, menuId)) { Session() }
    }

    /** 面板关闭时清除，避免状态泄漏到下一次打开 */
    fun end(player: UUID, menuId: String) {
        states.remove(key(player, menuId))
    }

    /** 该玩家是否正在操作某面板 */
    fun has(player: UUID, menuId: String): Boolean = states.containsKey(key(player, menuId))

    /**
     * 清掉长期未活跃的孤儿状态。
     *
     * 只在 [get] 时顺带跑一次 —— 不需要定时任务，
     * 因为面板系统本来就不会高频创建会话。
     */
    fun sweep() {
        val now = System.currentTimeMillis()
        states.entries.removeIf { now - it.value.touched > STALE_MILLIS }
    }

    /** 关服 / 重载时全清 */
    fun clear() = states.clear()
}
