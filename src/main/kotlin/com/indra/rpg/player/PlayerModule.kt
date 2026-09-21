package com.indra.rpg.player

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.mob.MobLevelModule
import com.indra.rpg.module.IndraModule
import com.indra.rpg.util.Msg
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.platform.util.bukkitPlugin

/**
 * 玩家模块 —— 等级、经验、击杀统计、死亡惩罚。
 *
 * 覆盖点：
 *   - 进服加载数据、首次进服欢迎
 *   - 退服保存数据
 *   - 击杀怪物获得经验
 *   - 死亡扣经验
 */
object PlayerModule : IndraModule {

    override val name = "Player"

    private var listener: Listener? = null

    override fun onEnable() {
        val l = object : Listener {

            @EventHandler(priority = EventPriority.MONITOR)
            fun onJoin(e: PlayerJoinEvent) {
                val player = e.player
                val first = DataManager.isFirstJoin(player)
                val data = DataManager.load(player)

                if (first) {
                    Msg.raw(player, Msg.format(
                        IndraConfig.welcomeMessage,
                        "player" to player.name
                    ))
                    Msg.send(player, "&7当前等级 &eLv.${data.level}&7，经验 &e${data.exp}/${data.expToNextLevel()}")
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onQuit(e: PlayerQuitEvent) {
                DataManager.unload(e.player)
            }

            /** 击杀怪物 → 按怪物等级给经验 */
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onEntityDeath(e: EntityDeathEvent) {
                if (e.entity is Player) return
                val killer = e.entity.killer ?: return

                val mobLevel = MobLevelModule.getLevel(e.entity)
                val exp = IndraConfig.expPerMobLevel * mobLevel
                giveExp(killer, exp)

                DataManager.get(killer)?.let {
                    it.killCount++
                    DataManager.markDirty(it.uuid)
                }
            }

            /** 玩家死亡 → 经验惩罚 */
            @EventHandler(priority = EventPriority.MONITOR)
            fun onPlayerDeath(e: PlayerDeathEvent) {
                val player = e.entity
                val data = DataManager.get(player) ?: return
                data.deathCount++

                if (IndraConfig.enableDeathPenalty) {
                    val lost = (data.exp * IndraConfig.deathExpPenaltyRatio).toInt()
                    if (lost > 0) {
                        data.exp = (data.exp - lost).coerceAtLeast(0)
                        Msg.send(player, "&c你死亡了，损失 &e$lost &c点经验。")
                    }
                }
                DataManager.markDirty(data.uuid)
            }
        }
        listener = l
        org.bukkit.Bukkit.getPluginManager().registerEvents(l, bukkitPlugin)
    }

    override fun onDisable() {
        listener?.let { org.bukkit.event.HandlerList.unregisterAll(it) }
        listener = null
    }

    /** 给玩家加经验并处理升级提示 */
    fun giveExp(player: Player, amount: Int) {
        val data = DataManager.get(player) ?: return
        if (data.level >= IndraConfig.maxLevel) return

        if (data.addExp(amount)) {
            Msg.send(player, "&e&l升级！&r&7 你现在是 &eLv.${data.level}")
            if (data.points > 0) {
                Msg.send(player, "&7可用点数：&e${data.points}")
            }
            player.world.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
        }
        DataManager.markDirty(data.uuid)
    }
}
