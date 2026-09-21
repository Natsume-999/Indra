package com.indra.rpg.common.economy

import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import taboolib.common.platform.function.warning

/**
 * Vault 经济反射桥。
 *
 * ── 为什么用反射而不是编译期依赖 VaultAPI ──────────────────────
 *   1. Vault 没有稳定的公开 Maven 坐标（常用的是 JitPack 上的
 *      `com.github.MilkBowl:VaultAPI`，需要认证且在部分网络不可达）；
 *   2. Indra 当前的构建后处理（indraMainTask）**不提供 relocate 能力**，
 *      把 Vault 类编进产物会有包冲突风险；
 *   3. 反射实现在「服务器没装 Vault」时优雅降级，不会 NoClassDefFoundError。
 *
 *   因此这里全程 `Class.forName` + 反射调用，编译期对 Vault 零依赖，
 *   运行期 Vault 不存在时 [isAvailable] 返回 false，调用方自行降级。
 *
 * ⚠️ 2026-09-20（切方案 B / 开启隔离类加载器时加固）：
 *    `Vault` 是**独立插件**，由 Bukkit 自己的 PluginClassLoader 加载，
 *    并不在 Indra 的隔离加载器里。当前类虽由 Indra 加载，但插件 classloader
 *    是 parent-first 的，用无参 `Class.forName(...)` 在隔离模式下可能
 *    解析不到 Vault 的类。
 *    因此这里改为**显式取 Vault 插件的 classloader**：它一定是对的，
 *    且与 Indra 用什么加载器无关。
 *
 * 移植自 Phoenix 的 common/VaultBridge.kt（v1.16.0）。
 */
object VaultBridge {

    data class TransactionResult(val success: Boolean, val error: String = "")

    /** Vault 插件实例（未安装时为 null） */
    private fun vaultPlugin(): Plugin? =
        org.bukkit.Bukkit.getServer().pluginManager.getPlugin("Vault")

    private val economyClass: Class<*>? by lazy {
        runCatching {
            val loader = vaultPlugin()?.javaClass?.classLoader
            if (loader != null) {
                Class.forName("net.milkbowl.vault.economy.Economy", false, loader)
            } else {
                // Vault 未安装：退回本类加载器，仅为让 isAvailable() 优雅返回 false
                Class.forName("net.milkbowl.vault.economy.Economy")
            }
        }.getOrNull()
    }

    private fun provider(): Any? {
        val ec = economyClass ?: return null
        if (!org.bukkit.Bukkit.getServer().pluginManager.isPluginEnabled("Vault")) return null
        val registration = org.bukkit.Bukkit.getServer().servicesManager.getRegistration(ec) ?: return null
        return registration.provider
    }

    /** 经济服务是否可用（未装 Vault 或未注册 Economy 时为 false） */
    fun isAvailable(): Boolean = runCatching { provider() != null }.getOrDefault(false)

    fun deposit(player: OfflinePlayer, amount: Double): Boolean = depositResult(player, amount).success

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean = withdrawResult(player, amount).success

    fun depositResult(player: OfflinePlayer, amount: Double): TransactionResult =
        transaction("depositPlayer", player, amount)

    fun withdrawResult(player: OfflinePlayer, amount: Double): TransactionResult =
        transaction("withdrawPlayer", player, amount)

    /** 余额是否不少于 amount */
    fun has(player: OfflinePlayer, amount: Double): Boolean = runCatching {
        if (!amount.isFinite() || amount < 0.0) return false
        val ec = economyClass ?: return false
        val economy = provider() ?: return false
        ec.getMethod("has", OfflinePlayer::class.java, Double::class.javaPrimitiveType)
            .invoke(economy, player, amount) as Boolean
    }.getOrDefault(false)

    /** 查询余额；Vault 不可用或异常时返回 null */
    fun balance(player: OfflinePlayer): Double? = runCatching {
        val ec = economyClass ?: return null
        val economy = provider() ?: return null
        ec.getMethod("getBalance", OfflinePlayer::class.java).invoke(economy, player) as Double
    }.getOrNull()

    /** 用经济插件自己的格式渲染金额（带货币符号）；失败回退纯数字 */
    fun format(amount: Double): String = runCatching {
        val ec = economyClass ?: return plain(amount)
        val economy = provider() ?: return plain(amount)
        ec.getMethod("format", Double::class.javaPrimitiveType).invoke(economy, amount) as String
    }.getOrDefault(plain(amount))

    private fun transaction(methodName: String, player: OfflinePlayer, amount: Double): TransactionResult {
        if (!amount.isFinite() || amount < 0.0) return TransactionResult(false, "金额无效: $amount")
        val ec = economyClass ?: return TransactionResult(false, "未检测到 Vault")
        val economy = provider() ?: return TransactionResult(false, "Vault 或经济服务不可用")
        return runCatching {
            val response = ec.getMethod(
                methodName,
                OfflinePlayer::class.java,
                Double::class.javaPrimitiveType
            ).invoke(economy, player, amount)
            if (response == null) return@runCatching TransactionResult(false, "经济插件返回 null")
            val success = response.javaClass.getMethod("transactionSuccess").invoke(response) == true
            val error = runCatching {
                response.javaClass.getField("errorMessage").get(response)?.toString().orEmpty()
            }.getOrElse {
                runCatching { response.javaClass.getMethod("getErrorMessage").invoke(response)?.toString().orEmpty() }
                    .getOrDefault("")
            }
            TransactionResult(success, error)
        }.onFailure {
            val action = if (methodName == "depositPlayer") "存款" else "扣款"
            warning("[Indra] Vault $action 失败: ${(it.cause ?: it).message}")
        }.getOrElse { TransactionResult(false, (it.cause ?: it).message.orEmpty()) }
    }

    private fun plain(amount: Double): String {
        val normalized = java.math.BigDecimal.valueOf(amount).stripTrailingZeros()
        return if (normalized.signum() == 0) "0" else normalized.toPlainString()
    }

    /** 启动期状态文案（供 onEnable 日志输出） */
    fun statusLine(plugin: Plugin): String {
        val name = runCatching { plugin.name }.getOrDefault("?")
        return if (isAvailable()) "已连接 Vault 经济（$name）" else "未检测到 Vault 或经济服务"
    }
}
