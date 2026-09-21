// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ============================================================================

package taboolib.common.platform

/** 代理指令发送者（玩家 / 控制台的统一抽象） */
interface ProxyCommandSender {
    val name: String
    fun sendMessage(message: String)
}

/** 代理玩家 */
interface ProxyPlayer : ProxyCommandSender

/** 周期任务句柄：submit(...) 的返回值 */
interface PlatformTask {
    fun cancel()
}
