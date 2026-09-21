// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib Configuration 离线桩
//  ⚠️ 仅当 verify.sh 找不到真实 TabooLib jar 时编译。
//     签名已 javap 对照 basic-configuration-6.3.0-f9483b9.jar 核实。
//
//  ★ 真包结构（与旧桩完全不同，旧桩在这里漂移最严重）：
//      Configuration 是 **interface**，继承 taboolib.library.configuration.ConfigurationSection
//      静态工厂在 `Configuration.Companion` 上：
//        Configuration.loadFromFile(file) / loadFromString(s) / loadFromInputStream(is)
//      实例方法：file / saveToString / saveToFile / loadFromFile / loadFromString
//                / loadFromReader / loadFromInputStream / reload / onReload / changeType
//
//  ⚠️ 桩做成 interface 而不是 open class，是为了让
//     `Configuration.loadFromFile(file)` 这种**伴生对象静态调用**能编译：
//     写成 open class 时，Kotlin 会在类自身上找 loadFromFile，
//     找不到就报 unresolved reference —— 这正是旧桩的症状。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.module.configuration

import taboolib.library.configuration.ConfigurationSection
import java.io.File
import java.io.InputStream
import java.io.Reader

enum class Type { YAML, JSON, TOML, HOCON }

/** 字段绑定注解：贴在 `lateinit var conf: Configuration` 上 */
annotation class Config(val value: String, val autoReload: Boolean = false)

/** 路径绑定注解：贴在 `var xxx: T` 上，声明该字段读配置文件里的哪个节点 */
annotation class ConfigNode(val value: String, val comment: String = "")

interface Configuration : ConfigurationSection {
    var file: File
    val reloadGeneration: Int

    fun saveToString(): String
    fun saveToFile(file: File)
    fun loadFromFile(file: File)
    fun loadFromString(source: String)
    fun loadFromReader(reader: Reader)
    fun loadFromInputStream(input: InputStream)
    fun reload()
    fun onReload(runnable: Runnable)
    fun changeType(type: Type)

    companion object {
        @JvmStatic fun loadFromFile(file: File): Configuration = error("stub")
        @JvmStatic fun loadFromString(source: String): Configuration = error("stub")
        @JvmStatic fun loadFromInputStream(input: InputStream): Configuration = error("stub")
        @JvmStatic fun loadFromReader(reader: Reader): Configuration = error("stub")
        @JvmStatic fun empty(): Configuration = error("stub")
    }
}
