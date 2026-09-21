package com.indra.rpg.common.script

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.adaptCommandSender
import taboolib.common.platform.function.console
import taboolib.common.platform.function.warning
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions

/**
 * Kether 脚本执行器（封禁自动解封等「配置化动作」统一走这里）。
 *
 * ── 变量替换策略 ─────────────────────────────────────────
 *   在脚本行文本上直接替换 `{key}` / `<key>`，而不是走 Kether 的
 *   VariableMap。原因：文本替换对 `tell "{player} 已解封"` 这类
 *   内嵌在字符串里的占位符同样生效，而 VariableMap 只对独立标识符生效。
 *
 * ── sender 选择 ──────────────────────────────────────────
 *   在线玩家 → 以该玩家为 sender（动作里的 `tell` 直接发给他）
 *   离线 / 无主 → 以控制台为 sender（公告类动作）
 */
object KetherRunner {

    /** 变量表 */
    typealias Variables = Map<String, String>

    fun emptyVariables(): Variables = emptyMap()

    fun applyVariables(line: String, variables: Variables): String {
        var result = line
        for ((key, value) in variables) {
            result = result.replace("{${key}}", value, ignoreCase = true)
            result = result.replace("<${key}>", value, ignoreCase = true)
        }
        return result
    }

    /**
     * 执行脚本（异步，不阻塞主线程）。
     * @param sender 执行者：Player / 控制台 / null
     */
    fun run(sender: Any?, scripts: List<String>, variables: Variables = emptyVariables()) {
        if (scripts.isEmpty()) return
        val substituted = scripts.map { applyVariables(it, variables) }
        val proxy = when (sender) {
            is Player -> adaptCommandSender(sender)
            else -> console()
        }
        runCatching {
            KetherShell.eval(
                substituted,
                ScriptOptions(sender = proxy, namespace = listOf("Indra"))
            )
        }.onFailure { e ->
            taboolib.common.platform.function.warning(
                "[Indra] Kether 脚本执行失败: ${e.javaClass.simpleName} (${e.message})"
            )
        }
    }

    /** 按玩家名取在线玩家（离线返回 null，调用方会回退到控制台） */
    fun onlinePlayerOrNull(name: String): Player? = Bukkit.getPlayer(name)

    /**
     * 同步求值一组 Kether 脚本，返回布尔结果（供出售条件等「判断型」场景使用）。
     *
     * ── 为什么不用 KetherShell.eval 的 get() 直接阻塞 ────────────
     *   若调用发生在主线程，而脚本内部又有依赖主线程的动作（如 `tell`、
     *   实体操作），`get()` 会直接死锁。
     *   因此这里判断当前线程：
     *     · 不在主线程 → 允许短暂阻塞等待（默认 2s 超时）
     *     · 在主线程   → **不等待**，直接返回 null 交由调用方按「不满足」处理，
     *                    并打印一次告警。这是我们能给出的最安全行为。
     *
     * @return true / false；无法求值（主线程阻塞风险、超时、异常）返回 null
     */
    fun evalBooleanBlocking(
        sender: Any?,
        scripts: List<String>,
        variables: Variables = emptyVariables(),
        timeoutMillis: Long = 2000L,
    ): Boolean? {
        if (scripts.isEmpty()) return true
        if (Bukkit.isPrimaryThread()) {
            warning(
                "[Indra] Kether 条件求值被跳过：当前在主线程，为避免死锁不执行同步等待。" +
                    "条件脚本：${scripts.joinToString(" | ")}"
            )
            return null
        }
        val substituted = scripts.map { applyVariables(it, variables) }
        val proxy = when (sender) {
            is Player -> adaptCommandSender(sender)
            else -> console()
        }
        return runCatching {
            KetherShell.eval(substituted, ScriptOptions(sender = proxy, namespace = listOf("Indra")))
                .get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.onFailure {
            warning("[Indra] Kether 条件求值失败: ${it.javaClass.simpleName} (${it.message})")
        }.getOrNull().toBooleanLoose()
    }

    /**
     * 宽松布尔解析：Kether 条件的返回值可能是 boolean / null / 数字 / 字符串。
     * 约定：null → false（Kether 里「没有输出」通常意味着条件不成立）
     */
    private fun Any?.toBooleanLoose(): Boolean = when (this) {
        null -> false
        is Boolean -> this
        is Number -> this.toInt() != 0
        is String -> this.equals("true", ignoreCase = true) || this == "1"
        else -> this.toString().equals("true", ignoreCase = true)
    }
}
