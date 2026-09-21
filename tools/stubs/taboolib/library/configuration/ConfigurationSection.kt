// ═══════════════════════════════════════════════════════════════════════════
//  TabooLib ConfigurationSection 离线桩
//  ⚠️ 仅当 verify.sh 找不到真实 TabooLib jar 时编译。
//     签名已 javap 对照 basic-configuration-6.3.0-f9483b9.jar 核实。
//
//  ★ 关键差异（桩曾在这里漂移，制造过假阴性）：
//    真包的 `getKeys(deep: Boolean)` 返回 **Set<String>**，不是 MutableSet。
//    写成 MutableSet 时，源码里 `for (id in config.getKeys(false))`
//    会因为 Set 与 MutableSet 的 iterator() 解析歧义而报
//    "method 'iterator()' is ambiguous"。返回类型必须与真包一致。
// ═══════════════════════════════════════════════════════════════════════════

package taboolib.library.configuration

interface ConfigurationSection {
    fun getString(path: String): String?
    fun getString(path: String, def: String?): String?
    fun getInt(path: String): Int
    fun getInt(path: String, def: Int): Int
    fun getBoolean(path: String): Boolean
    fun getBoolean(path: String, def: Boolean): Boolean
    fun getLong(path: String): Long
    fun getLong(path: String, def: Long): Long
    fun getDouble(path: String): Double
    fun getDouble(path: String, def: Double): Double

    /** ⚠️ 返回 `Set<String>`（真包签名），不是 MutableSet */
    fun getKeys(deep: Boolean): Set<String>

    fun getStringList(path: String): List<String>
    fun getConfigurationSection(path: String): ConfigurationSection?
    fun createSection(path: String): ConfigurationSection
    fun contains(path: String): Boolean
    fun get(path: String): Any?
    fun get(path: String, def: Any?): Any?
    fun set(path: String, value: Any?)
}
