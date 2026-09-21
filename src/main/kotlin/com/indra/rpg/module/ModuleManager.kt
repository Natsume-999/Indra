package com.indra.rpg.module

import taboolib.common.platform.function.info

/**
 * 功能模块接口。
 *
 * ⚠️ TabooLib 开发约定：对外暴露的 API 不要使用 Kotlin 接口
 * （编译期重定向会导致问题），内部模块管理可以正常使用。
 * 如果这个接口要给其他插件调用，请改写为 Java interface。
 */
interface IndraModule {

    /** 模块名 */
    val name: String

    /** 是否默认启用 */
    val defaultEnabled: Boolean get() = true

    /** 启用 */
    fun onEnable()

    /** 停用 */
    fun onDisable()
}

/**
 * 模块注册中心。
 *
 * 新增功能模块只需在 [modules] 列表里加一行，
 * 并在下方 [ModuleManager] 中注册。
 */
object ModuleManager {

    val modules = mutableListOf<IndraModule>()

    /** 全部内置模块清单 */
    private val builtin: List<IndraModule>
        get() = listOf(
            com.indra.rpg.mob.MobSpawnModule,
            com.indra.rpg.mob.MobLevelModule,
            com.indra.rpg.player.PlayerModule,
            com.indra.rpg.chat.ChatModule,
            com.indra.rpg.ban.BanModule,
            com.indra.rpg.sell.SellModule,
            // ⚠️ 面板模块必须在 CommandModule **之前**：
            //    /indra menu 指令会调 MenuManager.open，
            //    而 MenuManager 的初始化（registerAll）依赖 MenuConfig.conf
            //    已经绑定。顺序反了会出现「指令能注册但面板打不开」。
            com.indra.rpg.ui.MenuModule,
            com.indra.rpg.command.CommandModule,
        )

    fun enableAll() {
        builtin.forEach { module ->
            if (!module.defaultEnabled) {
                info("[Indra] 模块 §e${module.name}§7 已跳过（默认关闭）")
                return@forEach
            }
            runCatching {
                module.onEnable()
                modules += module
                info("[Indra] 模块 §a${module.name}§7 已启用")
            }.onFailure {
                info("[Indra] 模块 §c${module.name}§7 启用失败: ${it.message}")
            }
        }
    }

    fun disableAll() {
        modules.reversed().forEach { module ->
            runCatching { module.onDisable() }
                .onFailure { info("[Indra] 模块 §c${module.name}§7 卸载异常: ${it.message}") }
        }
        modules.clear()
    }
}
