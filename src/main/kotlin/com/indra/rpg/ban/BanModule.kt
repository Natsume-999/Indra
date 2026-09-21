package com.indra.rpg.ban

import com.indra.rpg.Indra
import com.indra.rpg.common.db.IndraDb
import com.indra.rpg.module.IndraModule
import taboolib.common.platform.function.info

/**
 * 封禁模块。
 *
 * ── 功能 ──────────────────────────────────────────────
 *   · 在线 / 离线封禁、时效封禁、永久封禁
 *   · 到期自动解封（周期扫描 + 登录时判定）
 *   · 踢出、白名单
 *   · 警告系统（累计达阈值自动封禁）
 *   · 操作历史（BAN / UNBAN / KICK / WHITELIST / WARN）
 *   · 五个可取消事件，供其他插件挂钩
 *
 * ── 存储 ──────────────────────────────────────────────
 *   表 indra_ban / indra_ban_warn / indra_ban_history，
 *   后端由 datasource.yml 的 database.enable 决定（SQLite / MySQL）。
 *
 * 移植自 Phoenix v1.16.0 的 ban 模块（1011 行 / 17 文件）。
 */
object BanModule : IndraModule {

    override val name: String = "Ban（封禁）"

    override fun onEnable() {
        // 1. 配置注入（@Config 自动从 jar 释放并加载）
        //    访问一次触发 lateinit 初始化，避免首次调用发生在异步线程
        BanConfig.settings
        BanConfig.messages

        // 2. 建表 + 数据源（首次访问 BanDatabaseManager 时惰性建立）
        BanDatabaseManager.getDatabase()

        // 3. 事件监听
        //    TabooLib 6.3 会在 ENABLE 阶段扫描带 @SubscribeEvent 的类并自动注册，
        //    **没有** registerBukkitListener 这类手动 API（已反编译核实）。
        //    这里引用一次只为确保 object 完成类加载。
        BanPlayerListener
        BanWorldListener

        // 4. 自动解封周期任务
        BanAutoUnban.start()

        info("[Indra] 封禁模块就绪 → ${if (IndraDb.isMySQL()) "MySQL" else "本地 SQLite"}")
    }

    override fun onDisable() {
        BanAutoUnban.stop()
        BanDatabaseManager.reset()
        Indra.logger.info("[Indra] 封禁模块已卸载")
    }

    /** 重载：重新读配置、重启扫描任务 */
    fun reload() {
        BanAutoUnban.stop()
        BanAutoUnban.start()
    }
}
