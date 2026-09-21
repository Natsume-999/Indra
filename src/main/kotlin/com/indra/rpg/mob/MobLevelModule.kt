package com.indra.rpg.mob

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.module.IndraModule
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import taboolib.platform.util.bukkitPlugin

/**
 * 怪物等级模块 —— RPG 化的核心内容。
 *
 * 禁止原版生物生成后，服务器上的怪主要来自自定义刷怪点。
 * 该模块为每只生成的怪按等级放大血量与攻击力，
 * 并在名字前显示等级，供击杀经验与掉落结算使用。
 *
 * 等级规则：以生成点距离世界原点（0,0）的距离划分，每 N 格 +1 级。
 * 想要更精细的控制，可以扩展为读取刷怪点配置表。
 */
object MobLevelModule : IndraModule {

    override val name = "MobLevel"

    /** 等级标记前缀，用 Scoreboard Tag 存，便于其他模块读取 */
    private const val LEVEL_TAG = "indra_level:"

    private var listener: Listener? = null

    override fun onEnable() {
        if (!IndraConfig.enableMobLevel) return

        val l = object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onSpawn(e: CreatureSpawnEvent) {
                // 只处理敌对生物，避免动到村民、宠物等
                if (isHostile(e.entity)) {
                    applyLevel(e.entity)
                }
            }
        }
        listener = l
        org.bukkit.Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)
    }

    override fun onDisable() {
        listener?.let { org.bukkit.event.HandlerList.unregisterAll(it) }
        listener = null
    }

    /** 判定是否为敌对生物（用 Monster 接口，覆盖所有原版敌对怪） */
    private fun isHostile(entity: LivingEntity): Boolean =
        entity is org.bukkit.entity.Monster

    /** 按坐标距离计算等级 */
    fun calcLevel(location: Location): Int {
        val origin = Location(location.world, 0.0, location.y, 0.0)
        val dist = location.distance(origin)
        return (dist / IndraConfig.blocksPerLevel).toInt().coerceAtLeast(1)
    }

    /** 对生物套用等级强化 */
    fun applyLevel(entity: LivingEntity) {
        val level = calcLevel(entity.location)

        // 血量 = 基础值 × 倍率^(level-1)
        // ⚠️ Paper 26.3：Attribute 常量已去掉 `GENERIC_` 前缀
        //    旧名 GENERIC_MAX_HEALTH → 新名 MAX_HEALTH
        entity.getAttribute(Attribute.MAX_HEALTH)?.let { attr ->
            val newHealth = attr.baseValue *
                Math.pow(IndraConfig.healthPerLevel, (level - 1).toDouble())
            attr.baseValue = newHealth
            entity.health = newHealth
        }

        // 攻击力（同样去掉 GENERIC_ 前缀）
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.let { attr ->
            attr.baseValue += IndraConfig.attackPerLevel * (level - 1)
        }

        // 名字显示等级
        // 注意：Bukkit 的 `customName`（String 版）在 Paper 上已废弃，
        // 这里用 Adventure 的 `customName(Component)`，跨端兼容且不产生告警。
        if (IndraConfig.showMobLevelName) {
            entity.customName(
                net.kyori.adventure.text.Component.text("§c[Lv.$level] §f${entity.type.name}")
            )
            entity.isCustomNameVisible = true
        }

        // 标记等级，供击杀经验 / 掉落模块读取
        entity.addScoreboardTag("$LEVEL_TAG$level")
    }

    /** 从实体读取等级（非强化怪返回 1） */
    fun getLevel(entity: LivingEntity): Int {
        return entity.scoreboardTags
            .firstOrNull { it.startsWith(LEVEL_TAG) }
            ?.removePrefix(LEVEL_TAG)
            ?.toIntOrNull() ?: 1
    }
}
