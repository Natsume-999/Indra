package com.indra.rpg.sell

import com.indra.rpg.common.economy.VaultBridge
import com.indra.rpg.module.IndraModule
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning

/**
 * 出售模块（A3）。
 *
 * ── 功能 ──────────────────────────────────────────────
 *   · 物品回收出售，规则驱动（`sell/` 目录下的 yml）
 *   · 图形化出售界面（`table/` 目录下的 yml），支持一键放入 / 自动出售
 *   · 加权随机结算，支持数值区间（`-500 20~25`）
 *   · 普通出售 / 点券（预留）
 *   · Kether 条件与回调脚本
 *   · 成交审计落库（`indra_sell_log`）
 *
 * ── 与 Phoenix 的关系 ─────────────────────────────────
 *   Phoenix 的出售是「融合第三方 VitaSell 引擎」，其源码不在仓库内，
 *   无法直接移植（见 docs/功能清单.md A3）。这里**按 Phoenix 的配置契约重写**：
 *    `sell/` 与 `table/` 下的文件格式 100% 兼容，
 *   用户现有规则与界面文件可原样拷入 `plugins/Indra/` 使用。
 *
 * ── 移植时保留的语义（重要）───────────────────────────
 *  配置里 **Money 负数表示给予玩家**（VitaSell 语义）。
 *  保留该语义是为了让既有配置迁移后不会反向扣钱。
 */
object SellModule : IndraModule {

    override val name: String = "Sell（出售）"

    override fun onEnable() {
        // 1. 模块配置（@Config 自动释放 sell.yml）
        SellConfig.conf

        // 2. 释放默认规则/界面（仅当目标不存在）
        SellConfig.releaseDefaults()

        // 3. 扫描加载
        SellConfig.load()

        // 4. 事件监听 —— TabooLib 6.3 在 ENABLE 阶段自动扫描 @SubscribeEvent，
        //    这里引用一次确保 object 完成类加载（与 BanModule 一致）
        SellListener

        // 5. 经济可用性提示
        if (!VaultBridge.isAvailable()) {
            warning("[Indra] 未检测到 Vault 经济服务 —— 出售界面可打开，但结算会失败。")
            warning("[Indra] 请安装 Vault 及一个经济实现（EssentialsX / CMI / 自研）后重启。")
        } else {
            info("[Indra] 出售模块已接入 Vault 经济。")
        }

        info("[Indra] 出售模块就绪 → ${SellConfig.rules.size} 条规则 / ${SellConfig.tables.size} 个界面")
    }

    override fun onDisable() {
        info("[Indra] 出售模块已卸载")
    }

    /** 重载规则与界面 */
    fun reload(): Pair<Int, Int> = SellConfig.load()
}
