package com.indra.rpg.mob

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.module.IndraModule
import org.bukkit.Bukkit
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.SpawnerSpawnEvent
import taboolib.common.platform.function.info
import taboolib.platform.util.bukkitPlugin

/**
 * 原版生物生成控制 —— Indra 的核心模块。
 *
 * 拦截策略（逐层过滤，任一命中即放行）：
 *   1. 总开关 `spawn.disable-vanilla-spawn` 关闭 → 全部放行
 *   2. 世界在 `spawn.allow-worlds` 中 → 放行
 *   3. 生成原因在 `spawn.allow-reasons` 中 → 放行（自定义怪 / 刷怪笼 / 刷怪蛋）
 *   4. 实体类型在 `spawn.whitelist-entities` 中 → 放行（村民、宠物等）
 *
 * 另有一个独立子功能：禁止原版生物自然消失，防止自定义刷怪点的怪被系统清掉。
 */
object MobSpawnModule : IndraModule {

    override val name = "MobSpawn"

    private var listener: Listener? = null
    private var despawnListener: Listener? = null
    private var spawnerListener: Listener? = null

    /** 预热为 Set，避免每次生成事件都遍历 List */
    private var allowReasons: Set<String> = emptySet()
    private var whitelistEntities: Set<String> = emptySet()
    private var allowWorlds: Set<String> = emptySet()

    override fun onEnable() {
        refreshCache()

        val l = object : Listener {
            /**
             * 自然生成 / 刷怪笼 / 自定义刷怪都走这个事件，
             * SpawnReason 覆盖了绝大多数来源。
             * 用 LOWEST 以便尽早取消，减少后续插件的无效开销。
             */
            @EventHandler(priority = EventPriority.LOWEST)
            fun onCreatureSpawn(e: CreatureSpawnEvent) {
                if (shouldBlock(e.entityType, e.spawnReason.name, e.location.world.name)) {
                    e.isCancelled = true
                }
            }
        }
        listener = l
        Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)

        // 刷怪笼独立开关
        if (IndraConfig.disableSpawner) {
            registerSpawnerListener()
        }

        // 禁止自然消失
        if (IndraConfig.disableDespawn) {
            registerDespawnListener()
        }
    }

    override fun onDisable() {
        listOfNotNull(listener, despawnListener, spawnerListener).forEach { HandlerList.unregisterAll(it) }
        listener = null
        despawnListener = null
        spawnerListener = null
    }

    /** 配置重载后调用 */
    fun refreshCache() {
        allowReasons = IndraConfig.spawnAllowReasons.map { it.uppercase() }.toSet()
        whitelistEntities = IndraConfig.spawnWhitelistEntities.map { it.uppercase() }.toSet()
        allowWorlds = IndraConfig.spawnAllowWorlds.toSet()
    }

    /**
     * 判定是否应当阻止生成。
     * 抽成独立函数，方便其他模块复用与单元测试。
     */
    fun shouldBlock(type: EntityType, reason: String, world: String): Boolean {
        if (!IndraConfig.disableVanillaSpawn) return false

        // 世界白名单放行
        if (allowWorlds.isNotEmpty() && world in allowWorlds) return false
        // 生成原因放行（如自定义刷怪、刷怪蛋）
        if (reason.uppercase() in allowReasons) return false
        // 实体白名单放行（村民、宠物等）
        if (type.name.uppercase() in whitelistEntities) return false

        if (IndraConfig.debug) {
            info("[Indra] 已拦截 $type 生成 (reason=$reason, world=$world)")
        }
        return true
    }

    // ── 子功能：刷怪笼 ────────────────────────────────────

    private fun registerSpawnerListener() {
        val l = object : Listener {
            @EventHandler(priority = EventPriority.LOWEST)
            fun onSpawnerSpawn(e: SpawnerSpawnEvent) {
                if (shouldBlock(e.entityType, "SPAWNER", e.location.world.name)) {
                    e.isCancelled = true
                }
            }
        }
        spawnerListener = l
        Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)
    }

    // ── 子功能：禁止自然消失 ───────────────────────────────

    /**
     * 禁止自定义怪被服务器自然清理。
     *
     * ⚠️ 注意：`EntityRemoveEvent` 是**不可取消**的（Paper API 已确认），
     * 因此不能靠监听事件来阻止消失。
     * 正确做法是在生物生成时调用 `setRemoveWhenFarAway(false)`，
     * 这里监听生成事件，对带 Indra 等级标记的生物关闭自动清理。
     */
    private fun registerDespawnListener() {
        val l = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onSpawn(e: CreatureSpawnEvent) {
                val entity = e.entity
                // 只处理被本插件强化过的怪（带等级标记）
                if (entity.scoreboardTags.none { it.startsWith("indra_level:") }) return

                // 关闭「远离玩家后自动消失」
                runCatching { entity.removeWhenFarAway = false }
            }
        }
        despawnListener = l
        Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)
    }
}
