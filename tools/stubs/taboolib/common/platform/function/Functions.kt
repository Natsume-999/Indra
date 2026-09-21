package taboolib.common.platform.function

import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.service.PlatformExecutor
import java.io.File
import java.util.logging.Logger

val logger: Logger = Logger.getLogger("TabooLib")

fun info(msg: String) = logger.info(msg)
fun warning(msg: String) = logger.warning(msg)
fun severe(msg: String) = logger.severe(msg)

fun getDataFolder(): File = File("plugins/Indra")

/** 周期任务：返回句柄，调用方可 cancel() */
fun submit(delay: Long = 0, period: Long = -1, task: Runnable): PlatformExecutor.PlatformTask? = null
fun submitAsync(delay: Long = 0, period: Long = -1, task: Runnable): PlatformExecutor.PlatformTask? = null

/** 控制台 sender（Kether 脚本无主执行时用） */
fun console(): ProxyCommandSender = object : ProxyCommandSender {
    override val name: String get() = "CONSOLE"
    override fun sendMessage(message: String) = Unit
}

/** org.bukkit 对象 → TabooLib 代理命令发送者 */
fun adaptCommandSender(sender: Any): ProxyCommandSender = object : ProxyCommandSender {
    override val name: String get() = "SENDER"
    override fun sendMessage(message: String) = Unit
}

fun sync(task: Runnable) {}
