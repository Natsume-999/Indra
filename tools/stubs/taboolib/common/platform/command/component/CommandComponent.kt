// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 真实包路径（javap 核实）：CommandComponent / CommandComponentDynamic
//     都在 taboolib.common.platform.command.**component** 包，
//     与 command() / CommandContext 所在的 taboolib.common.platform.command 不同。
// ============================================================================

package taboolib.common.platform.command.component

import taboolib.common.platform.command.CommandContext
import taboolib.common.platform.command.ExecuteContext

class CommandBase {
    /** 字面量子命令：`literal("status") { ... }` */
    fun literal(name: String, block: CommandComponent.() -> Unit) {}

    /** 无参根指令：`execute<CommandSender> { sender, _, _ -> ... }` */
    fun <T> execute(block: (T, CommandContext<T>, String) -> Unit) {}
    fun <T> execute(block: (T, CommandContext<T>) -> Unit) {}
    fun <T> exec(block: (ExecuteContext<T>) -> Unit) {}
}

class CommandComponent {

    /** 字面量子命令：Phoenix 风格的指令树就靠它在子命令下继续挂分支 */
    fun literal(name: String, block: CommandComponent.() -> Unit) {}
    fun literal(name: String, permission: String, block: CommandComponent.() -> Unit) {}

    /**
     * 动态参数。
     * ⚠️ 必须返回 CommandComponentDynamic：源码依赖链式
     *    `dynamic("a") { }.dynamic("b") { }.dynamic("c") { }`
     */
    fun dynamic(name: String, block: CommandComponentDynamic.() -> Unit): CommandComponentDynamic =
        CommandComponentDynamic()

    fun dynamic(name: String, optional: Boolean, block: CommandComponentDynamic.() -> Unit): CommandComponentDynamic =
        CommandComponentDynamic()

    fun <T> execute(block: (T, CommandContext<T>, String) -> Unit) {}
    fun <T> execute(block: (T, CommandContext<T>) -> Unit) {}
    fun <T> exec(block: (ExecuteContext<T>) -> Unit) {}
}

class CommandComponentDynamic {

    /**
     * 补全回调。
     * 真实签名 `suggestion<T>(Class, boolean, Function2)`，
     * 源码用 `suggestion<CommandSender> { sender, ctx -> ... }` 两参 lambda。
     */
    fun <T> suggestion(block: (T, CommandContext<T>) -> List<String>): CommandComponentDynamic = this

    /** 链式追加下一个动态参数 */
    fun dynamic(name: String, block: CommandComponentDynamic.() -> Unit): CommandComponentDynamic = this
    fun dynamic(name: String, optional: Boolean, block: CommandComponentDynamic.() -> Unit): CommandComponentDynamic =
        this

    fun <T> execute(block: (T, CommandContext<T>, String) -> Unit) {}
    fun <T> execute(block: (T, CommandContext<T>) -> Unit) {}
    fun <T> exec(block: (ExecuteContext<T>) -> Unit) {}
}
