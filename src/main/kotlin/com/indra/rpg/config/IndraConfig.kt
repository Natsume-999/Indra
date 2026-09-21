package com.indra.rpg.config

import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigNode
import taboolib.module.configuration.Configuration

/**
 * 全局配置绑定。
 *
 * `@Config(autoReload = true)` 会在文件被外部修改时自动重载字段。
 */
object IndraConfig {

    @Config("config.yml", autoReload = true)
    lateinit var conf: Configuration

    // ── 调试 / 通用 ─────────────────────────────────────────
    @ConfigNode("debug")
    var debug: Boolean = false

    /** 是否在控制台打印模块加载详情 */
    @ConfigNode("verbose")
    var verbose: Boolean = true

    // ── 原版生物生成控制 ────────────────────────────────────
    /** 是否禁止原版生物自然生成 */
    @ConfigNode("spawn.disable-vanilla-spawn")
    var disableVanillaSpawn: Boolean = true

    /** 允许生成的世界名白名单；空列表表示「所有世界都禁止」 */
    @ConfigNode("spawn.allow-worlds")
    var spawnAllowWorlds: List<String> = emptyList()

    /** 保留的生成原因（即使禁止生成，这些原因仍放行） */
    @ConfigNode("spawn.allow-reasons")
    var spawnAllowReasons: List<String> = listOf("CUSTOM", "SPAWNER", "EGG")

    /** 保留的实体类型，按 EntityType 名称填写 */
    @ConfigNode("spawn.whitelist-entities")
    var spawnWhitelistEntities: List<String> = listOf("VILLAGER", "WOLF", "CAT", "HORSE")

    /** 是否禁止刷怪笼生成 */
    @ConfigNode("spawn.disable-spawner")
    var disableSpawner: Boolean = false

    /** 是否禁止原版生物自然消失（防止自定义怪被清理） */
    @ConfigNode("spawn.disable-despawn")
    var disableDespawn: Boolean = true

    // ── 怪物等级 ───────────────────────────────────────────
    @ConfigNode("mob-level.enable")
    var enableMobLevel: Boolean = true

    /** 每多少格距离提升 1 级 */
    @ConfigNode("mob-level.blocks-per-level")
    var blocksPerLevel: Double = 100.0

    /** 每级血量倍率 */
    @ConfigNode("mob-level.health-per-level")
    var healthPerLevel: Double = 1.15

    /** 每级攻击加成 */
    @ConfigNode("mob-level.attack-per-level")
    var attackPerLevel: Double = 0.5

    /** 是否在怪物名字前显示等级 */
    @ConfigNode("mob-level.show-name")
    var showMobLevelName: Boolean = true

    // ── 玩家等级 ───────────────────────────────────────────
    @ConfigNode("player.default-level")
    var defaultLevel: Int = 1

    @ConfigNode("player.default-exp")
    var defaultExp: Int = 0

    @ConfigNode("player.max-level")
    var maxLevel: Int = 100

    /** 每级所需经验基数（实际需求 = 基数 × 当前等级） */
    @ConfigNode("player.exp-per-level")
    var expPerLevel: Int = 100

    /** 每级奖励属性点（数值本身无属性系统消费，保留给后续扩展） */
    @ConfigNode("player.points-per-level")
    var pointsPerLevel: Int = 3

    /** 击杀怪物每级经验 */
    @ConfigNode("player.exp-per-mob-level")
    var expPerMobLevel: Int = 5

    /** 首次进服欢迎语 */
    @ConfigNode("player.welcome-message")
    var welcomeMessage: String = "&a欢迎来到服务器，&e{player}&a！"

    // ── 聊天 ──────────────────────────────────────────────
    @ConfigNode("chat.enable-format")
    var enableChatFormat: Boolean = true

    @ConfigNode("chat.format")
    var chatFormat: String = "&7[&eLv.{level}&7] &f{player} &8» &7{message}"

    // ── 死亡惩罚 ───────────────────────────────────────────
    @ConfigNode("death.enable-penalty")
    var enableDeathPenalty: Boolean = true

    /** 死亡扣除经验比例 0.0 ~ 1.0 */
    @ConfigNode("death.exp-penalty-ratio")
    var deathExpPenaltyRatio: Double = 0.1

    // ── 数据 ──────────────────────────────────────────────
    /** 自动保存间隔（秒） */
    @ConfigNode("database.auto-save-interval")
    var autoSaveInterval: Long = 300L

    fun load() {
        if (debug) warning("[Indra] 调试模式已开启")
    }

    /** 该世界是否被放行（不被拦截） */
    fun isWorldAllowed(world: String): Boolean {
        return spawnAllowWorlds.isNotEmpty() && world in spawnAllowWorlds
    }
}
