package com.indra.rpg.sell

import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigNode
import java.io.File

/**
 * 出售系统配置。
 *
 * ── 目录约定 ─────────────────────────────────────────────────
 *   plugins/Indra/sell/  下的 yml    出售规则（每个文件可含多条顶层规则）
 *   plugins/Indra/table/ 下的 yml    出售界面（每个文件可含多个顶层界面）
 *
 * ⚠️ 上面刻意不写通配符形式（如 `sell/` 后跟星号加点 yml）：
 *    Kotlin 的块注释**支持嵌套**，注释正文里出现 `斜杠+星号` 会被编译器
 *    当成「嵌套注释开始」，导致后面的闭合符号提前配平、整个注释结构错位，
 *    最终报出与真实位置相差很远的 `Unclosed comment`（实测踩过）。
 *
 * 与 Phoenix 的 `Sell/sell` 与 `Sell/table` 布局等价，
 * 文件内容格式 100% 兼容，可直接拷贝。
 *
 * ── 为什么单独 `sell.yml` ────────────────────────────────────
 *   规则/界面是「多文件、可增删」的动态集合，不适合用 `@ConfigNode` 绑定；
 *   模块自身的行为开关（是否自动释放默认文件等）才放 `sell.yml`。
 */
object SellConfig {

    @Config("sell.yml", autoReload = false)
    lateinit var conf: Configuration

    /** 打开命令不带界面名且只有一个界面时，是否直接打开 */
    @ConfigNode("open.direct-when-single")
    var directWhenSingle: Boolean = true

    /** 未加载到任何界面时，打开命令是否自动释放默认文件并重扫 */
    @ConfigNode("options.auto-release-defaults")
    var autoReleaseDefaults: Boolean = true

    /** 规则目录 */
    val sellDir: File get() = File(dataFolder(), "sell")

    /** 界面目录 */
    val tableDir: File get() = File(dataFolder(), "table")

    // ── 已加载内容（重载时整体替换）──────────────────────────────

    @Volatile
    var rules: List<SellRule> = emptyList()
        private set

    @Volatile
    var tables: Map<String, SellTable> = emptyMap()
        private set

    private fun dataFolder(): File = taboolib.common.platform.function.getDataFolder()

    /**
     * 释放默认文件（仅当目标不存在时）。
     * @return 释放的文件数
     */
    fun releaseDefaults(): Int {
        var count = 0
        count += release("sell/Example.yml", File(sellDir, "Example.yml"))
        count += release("table/Example.yml", File(tableDir, "Example.yml"))
        if (count > 0) info("[Indra] 已释放 $count 个出售默认文件")
        return count
    }

    private fun release(resourcePath: String, target: File): Int {
        if (target.exists() && target.length() > 0L) return 0
        target.parentFile?.mkdirs()
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
        if (stream == null) {
            warning("[Indra] 默认资源缺失，无法释放：$resourcePath")
            return 0
        }
        return runCatching {
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            if (target.length() > 0L) 1 else {
                warning("[Indra] 释放后文件为空：${target.path}")
                0
            }
        }.getOrElse {
            warning("[Indra] 释放默认文件失败：${target.path}（${it.message}）")
            0
        }
    }

    /**
     * 扫描并加载全部规则与界面。
     * @return Pair(规则数, 界面数)
     */
    fun load(): Pair<Int, Int> {
        rules = loadRules()
        tables = loadTables().associateBy { it.id }

        if (rules.isEmpty()) {
            warning("[Indra] 未加载到任何出售规则，请检查 ${sellDir.path}")
        }
        if (tables.isEmpty()) {
            warning("[Indra] 未加载到任何出售界面，请检查 ${tableDir.path}")
        }

        info("[Indra] 出售系统已加载：${rules.size} 条规则 / ${tables.size} 个界面")
        return rules.size to tables.size
    }

    private fun loadRules(): List<SellRule> {
        val result = mutableListOf<SellRule>()
        forEachConfigFile(sellDir) { file, config ->
            for (id in config.getKeys(false)) {
                val section = config.getConfigurationSection(id) ?: continue
                val rule = SellRule.parse(id, file.name, section)
                if (rule == null) {
                    // 顶层节点没写 Item → 视为非规则文件（例如用户放了别的说明文件）
                    continue
                }
                if (!rule.hasReward()) {
                    warning("[Indra] 出售规则 `${rule.id}`（${file.name}）没有任何奖励（Money/Point/Kether 全为空），已跳过")
                    continue
                }
                result += rule
            }
        }
        return result
    }

    private fun loadTables(): List<SellTable> {
        val result = mutableListOf<SellTable>()
        forEachConfigFile(tableDir) { file, config ->
            for (id in config.getKeys(false)) {
                val section = config.getConfigurationSection(id) ?: continue
                val table = SellTable.parse(id, file.name, section) ?: continue
                if (result.any { it.id.equals(table.id, ignoreCase = true) }) {
                    warning("[Indra] 出售界面名 `${table.id}` 重复（${file.name}），后者已跳过")
                    continue
                }
                result += table
            }
        }
        return result
    }

    private fun forEachConfigFile(dir: File, action: (File, Configuration) -> Unit) {
        if (!dir.isDirectory) {
            dir.mkdirs()
            return
        }
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("yml", "yaml") }
            ?.sortedBy { it.name }
            ?: return

        for (file in files) {
            runCatching { Configuration.loadFromFile(file) }
                .onSuccess { action(file, it) }
                .onFailure {
                    warning("[Indra] 解析出售文件失败，已跳过：${file.name}（${it.javaClass.simpleName}: ${it.message}）")
                }
        }
    }

    /** 按名字找界面（不区分大小写） */
    fun table(name: String): SellTable? =
        tables[name] ?: tables.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** 指定界面可用的规则 */
    fun rulesFor(tableName: String): List<SellRule> = rules.filter { it.appliesTo(tableName) }
}
