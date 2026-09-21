package com.indra.rpg.ui

import com.indra.rpg.module.IndraModule
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

/**
 * 面板模块（A5）。
 *
 * ══ 职责 ═══════════════════════════════════════════════
 *   1. 释放 menu.yml 默认配置（仅当文件不存在）
 *   2. 触发 [MenuConfig] 的注解管线，让 `@Config` 真正绑定
 *   3. 触发 [MenuInputListener] 类加载，让 `@SubscribeEvent` 被扫到
 *
 * ══ 为什么第 3 步必须显式引用一下 ═══════════════════════
 *   TabooLib 6.3 在 ENABLE 阶段扫描 `@SubscribeEvent` 是**按已加载的类**
 *   进行的。`object MenuInputListener` 如果没有任何地方引用，
 *   它的静态初始化不会跑，事件监听就不会注册 —— 表现为
 *   「搜索功能点了没反应，也没有任何报错」。
 *   （这与 BanModule / SellModule 里显式写 `SellListener` 是同一个原因。）
 *
 * ══ 面板与指令的关系 ═══════════════════════════════════
 *   面板**不依赖** CommandModule：面板通过 [MenuManager.open] 直接打开，
 *   指令只是它的一个入口。这样即使指令注册出问题，
 *   其他插件仍可通过 API 调 `MenuManager.open(player, "main")` 打开面板。
 */
object MenuModule : IndraModule {

    override val name: String = "Menu（面板）"

    override fun onEnable() {
        // 1. 释放 menu.yml（@Config 会自动释放缺失的文件，
        //    这里再显式读一次是为了拿到"是否释放成功"的信号）
        MenuConfig.conf
        info("[Indra] 面板配置已加载 → menu.yml")

        // 2. 扫描所有面板，把「配置缺失 / 被禁用」的情况一次性报出来 ——
        //    比等到玩家点开面板才发现少了配置要好
        var enabled = 0
        MenuManager.ids().forEach { id ->
            if (MenuConfig.bool(id, "enabled", true)) enabled++
            else warning("[Indra] 面板 §e$id§7 在 menu.yml 中被禁用")
        }

        // 3. 触发搜索输入监听的类加载（见类注释第 3 点）
        MenuInputListener

        info("[Indra] 面板系统就绪 → $enabled/${MenuManager.ids().size} 个面板可用")
    }

    override fun onDisable() {
        // 清空所有玩家的面板会话状态，避免重载后残留旧页码 / 搜索词
        MenuManager.shutdown()
    }
}
