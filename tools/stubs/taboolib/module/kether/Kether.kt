// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 已通过 javap 对真实 minecraft-kether-6.3.0-a1d3953.jar 核实：
//    · KetherShell.eval(List<String>, ScriptOptions): CompletableFuture<Object>
//    · ScriptOptions(useCache, namespace, cache, sender, sandbox, detailError, context)
//      全部带默认值，Kotlin 侧可用命名参数只传需要的
// ============================================================================

package taboolib.module.kether

import taboolib.common.platform.ProxyCommandSender
import java.util.concurrent.CompletableFuture

class ScriptOptions(
    val useCache: Boolean = true,
    val namespace: List<String> = emptyList(),
    val cache: KetherShell.Cache? = null,
    val sender: ProxyCommandSender? = null,
    val sandbox: Boolean = false,
    val detailError: Boolean = false,
    val context: (ScriptContext) -> Unit = {}
)

object KetherShell {
    class Cache
    fun eval(scripts: List<String>, options: ScriptOptions = ScriptOptions()): CompletableFuture<Any?> =
        CompletableFuture.completedFuture(null)
}

class ScriptContext
