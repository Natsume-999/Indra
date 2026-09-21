package com.indra.rpg.data

import com.indra.rpg.config.IndraConfig
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.expansion.PersistentContainer
import taboolib.expansion.db
import taboolib.expansion.persistentContainer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File

/**
 * 数据管理器 —— 统一收口所有持久化操作。
 *
 * ── 存储策略（项目硬性要求：本地数据库 + MySQL）───────────────────
 *   datasource.yml → database.enable = true  → MySQL
 *   datasource.yml → database.enable = false → 本地 SQLite（插件目录 data.db）
 *   代码零改动，只切配置。
 *
 *   ⚠️ TabooLib 6.3 真实行为（已反编译核对）：
 *      `db(name, node, file)` 读取 `<node>.enable`：
 *        true  → 返回 ConfigurationSection（走 HostSQL / MySQL）
 *        false → 返回 File（走 HostSQLite）
 *      `persistentContainer(source) { new<T>() }` 内部会调
 *      `Container.init()`，**自动对所有注册实体建表**，无需手写 DDL。
 *
 * ── 缓存策略 ────────────────────────────────────────────────────
 *   · 玩家数据常驻内存，读写优先走缓存 → 不阻塞主线程
 *   · 定时异步批量写回数据库
 *   · 退出 / 关服时强制落盘
 */
object DataManager {

    lateinit var container: PersistentContainer
        private set

    /** 在线玩家缓存：uuid → 数据对象 */
    private val cache = mutableMapOf<String, IndraPlayerData>()

    /** 脏数据：需要写回库的 uuid */
    private val dirty = mutableSetOf<String>()

    private var mysqlMode = false

    fun init() {
        // enable=true → ConfigurationSection（MySQL）；false → File（SQLite）
        val source = db("datasource.yml", "database", "data.db")
        mysqlMode = source !is File

        container = persistentContainer(source) {
            new<IndraPlayerData>()
        }

        startAutoSave()

        info("[Indra] 数据层就绪 → ${if (mysqlMode) "MySQL" else "本地 SQLite"}")
    }

    fun isMySQL(): Boolean = mysqlMode

    // ── 缓存操作 ──────────────────────────────────────────

    /** 加载玩家数据到缓存，无记录则创建默认数据 */
    fun load(player: Player): IndraPlayerData {
        val uuid = player.uniqueId.toString()
        cache[uuid]?.let { it.name = player.name; return it }

        val data = findOne(uuid) ?: IndraPlayerData().apply {
            this.uuid = uuid
            this.name = player.name
            this.level = IndraConfig.defaultLevel
            this.exp = IndraConfig.defaultExp
            this.firstJoin = System.currentTimeMillis()
            this.lastSeen = this.firstJoin
            val tmp = this
            submitAsync { runCatching { container.get<IndraPlayerData>().insert(listOf(tmp as Any)) } }
        }

        data.name = player.name
        cache[uuid] = data
        return data
    }

    fun get(player: Player): IndraPlayerData? = cache[player.uniqueId.toString()]

    fun get(uuid: String): IndraPlayerData? = cache[uuid]

    fun markDirty(uuid: String) {
        dirty += uuid
    }

    /** 玩家是否首次进入（用于欢迎语判定） */
    fun isFirstJoin(player: Player): Boolean = findOne(player.uniqueId.toString()) == null

    fun unload(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = cache.remove(uuid) ?: return
        data.lastSeen = System.currentTimeMillis()
        dirty -= uuid
        submitAsync { runCatching { container.get<IndraPlayerData>().upsert(listOf(data as Any)) } }
    }

    fun saveNow(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = cache[uuid] ?: return
        data.lastSeen = System.currentTimeMillis()
        dirty -= uuid
        submitAsync { runCatching { container.get<IndraPlayerData>().upsert(listOf(data as Any)) } }
    }

    fun loadOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach { load(it) }
    }

    /**
     * 同步查库：按 uuid 取单条（仅用于首次加载 / 首进判定）。
     *
     * `findOne(key, filter)` 的第一个参数是「按哪个字段查」，第二个是过滤条件。
     * 这里直接用更省事的 `findOneByKey`，PTC 会按实体上的 `@UniqueKey` 自动定位。
     */
    private fun findOne(uuid: String): IndraPlayerData? = runCatching {
        container.get<IndraPlayerData>().findOneByKey<IndraPlayerData>(uuid)
    }.getOrNull()

    // ── 排行榜 / 查询 ─────────────────────────────────────

    /** 按等级取前 N 名（降序） */
    fun topLevel(limit: Int = 10): List<IndraPlayerData> = runCatching {
        container.get<IndraPlayerData>()
            .sortDescendingPage<IndraPlayerData>("level", 1, limit) { }
    }.getOrDefault(emptyList())

    /** 按总经验取前 N 名 */
    fun topExp(limit: Int = 10): List<IndraPlayerData> = runCatching {
        container.get<IndraPlayerData>()
            .sortDescendingPage<IndraPlayerData>("exp_total", 1, limit) { }
    }.getOrDefault(emptyList())

    // ── 定时任务 ─────────────────────────────────────────

    private fun startAutoSave() {
        val ticks = IndraConfig.autoSaveInterval * 20L
        // ⚠️ TabooLib 6.3 没有 `repeat()`；用 submitAsync(period, delay) 实现周期任务
        submitAsync(period = ticks, delay = ticks) {
            runCatching { flushDirty() }
                .onFailure { info("[Indra] 自动保存异常：${it.message}") }
        }
    }

    /** 批量写回脏数据 */
    fun flushDirty() {
        if (dirty.isEmpty()) return
        val snapshot = dirty.toList()
        val list = snapshot.mapNotNull { cache[it] }
        if (list.isEmpty()) { dirty.removeAll(snapshot); return }
        runCatching {
            // upsert 形参是 List<Any>，IndraPlayerData 需显式转成 Any 列表
            container.get<IndraPlayerData>().upsert(list.map { it as Any })
            dirty.removeAll(snapshot)
        }.onFailure { info("[Indra] 自动保存失败：${it.message}") }
    }

    /** 关服：全量落盘 + 关闭连接 */
    fun shutdown() {
        cache.values.forEach { it.lastSeen = System.currentTimeMillis() }
        val all = cache.values.toList()
        if (all.isNotEmpty()) {
            runCatching { container.get<IndraPlayerData>().upsert(all.map { it as Any }) }
        }
        cache.clear()
        dirty.clear()
        runCatching { container.close() }
    }
}
