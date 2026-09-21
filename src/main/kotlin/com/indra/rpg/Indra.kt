package com.indra.rpg

import com.indra.rpg.config.IndraConfig
import com.indra.rpg.data.DataManager
import com.indra.rpg.module.ModuleManager
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import java.util.logging.Logger

/**
 * Indra —— RPG 服务器基础功能插件
 *
 * 入口类必须是 object（TabooLib 约定），且无需 plugin.yml（框架自动生成）。
 *
 * 生命周期：
 *   CONST → INIT → LOAD → ENABLE → ACTIVE → DISABLE
 *
 * ⚠️ `Plugin` 基类**没有** logger / dataFolder 成员（已反编译核实），
 *    需要 Bukkit 侧能力时通过 [plugin]（即 BukkitPlugin 单例）取。
 */
object Indra : Plugin() {

    /** 插件单例，方便各处引用 */
    val instance: Indra get() = this

    /** Bukkit 侧插件实例：logger / dataFolder / getResource 都从这里取 */
    val plugin by lazy { BukkitPlugin.getInstance() }

    /** 日志器（等价于 plugin.logger） */
    val logger: Logger get() = plugin.logger

    override fun onLoad() {
        info("§b[Indra] §f正在加载 RPG 基础模块...")
    }

    override fun onEnable() {
        // 0. 启动自检 —— 打印「注解管线是否真的跑起来了」
        //    排查「配置文件不生成 / 命令不存在 / 零报错」时，这一段日志是分水岭：
        //      · 若连本段都不打印 → Indra.onEnable() 根本没被调用，
        //        即 pluginInstance == null（类扫描集合为空，见 IndraBootstrap Javadoc）
        //      · 若本段打印了但下面各步失败 → 是各模块自身的问题
        runtimeSelfCheck()

        // 1. 读取配置
        IndraConfig.load()

        // 2. 初始化数据存储（SQLite / MySQL 自动切换）
        DataManager.init()

        // 3. 装配功能模块
        ModuleManager.enableAll()

        info("§b[Indra] §a启用完成，共加载 §e${ModuleManager.modules.size} §a个模块。")
    }

    /**
     * 启动自检。
     *
     * ⚠️ 别删。这是「插件静默失活」类问题的第一现场证据：
     *    只要这行日志出现，就说明 TabooLib 的类扫描 / `Plugin.setInstance()`
     *    整条链路是通的，问题一定在更下游。
     */
    private fun runtimeSelfCheck() {
        val group = System.getProperty("taboolib.group")
        val folder = runCatching { plugin.dataFolder }.getOrNull()
        info("§b[Indra] §f启动自检：")
        info("  §7- §ftaboolib.group = §e${group ?: "<未设置>"}")
        info("  §7- §f数据目录       = §e${folder?.absolutePath ?: "<不可用>"}")
        info("  §7- §f插件版本       = §e${runCatching { plugin.pluginMeta.version }.getOrElse { "?" }}")

        // 注解驱动是否生效的「指纹文件」：config.yml 由 @Config 生成。
        // 不存在 → ConfigLoader 没跑 → 类扫描链路有问题（回归 IndraBootstrap 的根因）。
        if (folder != null) {
            val cfg = java.io.File(folder, "config.yml")
            if (cfg.exists()) {
                info("  §7- §fconfig.yml     = §a已生成 §7(注解管线正常)")
            } else {
                warning(
                    "[Indra] config.yml 未生成：注解驱动管线未生效。" +
                        "请检查 taboolib.group 是否为 com.indra（当前=$group），" +
                        "以及 IndraBootstrap 是否被 ASM 后处理破坏。"
                )
            }
        }
    }

    override fun onDisable() {
        ModuleManager.disableAll()
        DataManager.shutdown()
        info("§b[Indra] §c已卸载。")
    }

    /**
     * ACTIVE 阶段：所有插件均已加载完成。
     * 适合做跨插件兼容检查、玩家数据补全等操作。
     *
     * ⚠️ `onActive()` 是 TabooLib `Plugin` 基类的普通方法（非注解钩子），
     *    直接 `override` 即可；用 `@Awake(LifeCycle.ACTIVE)` 反而会
     *    报 "'onActive' hides member of supertype 'Plugin'"。
     */
    override fun onActive() {
        // 在线玩家数据初始化（重载场景）
        DataManager.loadOnlinePlayers()
    }
}
