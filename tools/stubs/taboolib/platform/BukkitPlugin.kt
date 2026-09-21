// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  真实类型：taboolib.platform.BukkitPlugin（继承 JavaPlugin，单例）
//    getInstance() 拿到后可用 logger / dataFolder / getResource 等 Bukkit 能力。
//    ⚠️ TabooLib 的 Plugin 基类**没有** logger，Indra 通过它取。
//
//  ⚠️ 这里**故意不继承 org.bukkit.plugin.java.JavaPlugin**：
//     离线校验只用到 logger / dataFolder，继承会引入 Bukkit 生命周期的
//     抽象方法负担（且不同 Paper 版本 JavaPlugin 是否 final 不一致）。
// ============================================================================

package taboolib.platform

import java.io.File
import java.util.logging.Logger

class BukkitPlugin {

    val logger: Logger = Logger.getLogger("Indra")
    val dataFolder: File = File("plugins/Indra")

    fun getResource(path: String): java.io.InputStream? = null

    companion object {
        private val STUB = BukkitPlugin()
        fun getInstance(): BukkitPlugin = STUB
    }
}
