// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 与真实 TabooLib 6.3 指令 API 对齐（已通过 javap 反编译核实）：
//
//   入口
//     command(name, aliases, description, usage, permission, permissionMessage,
//             permissionDefault, permissionChildren, hidden, Function1<CommandBase, Unit>)
//     ⚠️ name 是**函数参数**，不是 CommandBase 的可写属性
//
//   结构
//     CommandBase.literal(String[], boolean, String, boolean, String, Function1<CommandComponent, Unit>)
//     CommandComponent.dynamic(String, boolean, String, String, Function1<CommandComponentDynamic, Unit>)
//     ⚠️ 6.3 已无 `subCommand("name") { }`
//
//   执行
//     CommandComponent.execute<T>(Function3<T, CommandContext<T>, String, Unit>)
//     CommandComponent.exec<T>(Function1<ExecuteContext<T>, Unit>)
//
//   补全 / 取参
//     CommandComponentDynamic.suggest(Function1<SuggestContext<ProxyCommandSender>, List<String>>)
//     CommandContext<T>.int(name) / get(name) / args() / sender() / player()
// ============================================================================

package taboolib.common.platform.command

import taboolib.common.platform.command.component.CommandBase

/** 命令上下文。真实类型带泛型参数 `CommandContext<T>`。 */
class CommandContext<T> {
    /** `ctx["player"]` */
    operator fun get(key: String): String = ""
    fun args(): List<String> = emptyList()
    fun sender(): Any = Any()
    @Suppress("UNCHECKED_CAST")
    fun <P : Any> player(): P = Any() as P
}

/**
 * 取参扩展 —— 源码里 `ctx.int("amount")` 就是这个。
 *
 * ⚠️ 真实宿主是 `taboolib/common/platform/command/ExtraContextKt`，
 *    **不是** `CommandContext` 的成员方法（已 javap 核实）：
 *      public static final <T> int    int   (CommandContext<T>, String)
 *      public static final <T> Double intOrNull(...)
 *      public static final <T> double double(CommandContext<T>, String)
 *      public static final <T> float  float (CommandContext<T>, String)
 *      public static final <T> bool   bool  (CommandContext<T>, String)
 *    真实 API **没有** `long()` / `string()` 扩展（用 `get()` / `argument()` 代替）。
 *    因此这里必须写成**扩展函数**；若误写成类成员，源码写错也照样过。
 */
fun CommandContext<*>.int(key: String): Int = 0
fun CommandContext<*>.double(key: String): Double = 0.0
fun CommandContext<*>.bool(key: String): Boolean = false
/** ⚠️ 真实 TabooLib 无此扩展，仅为兼容旧代码保留；新代码请用 `get(key)`。 */
@Deprecated("真实 TabooLib 无此扩展，请使用 CommandContext.get(key)")
fun CommandContext<*>.long(key: String): Long = 0L
/** ⚠️ 同上，真实 API 请用 `get(key)` / `argument(i)`。 */
@Deprecated("真实 TabooLib 无此扩展，请使用 CommandContext.get(key)")
fun CommandContext<*>.string(key: String): String = ""

/** 执行上下文（exec 用） */
class ExecuteContext<T> {
    @Suppress("UNCHECKED_CAST")
    val sender: T get() = Any() as T
    @Suppress("UNCHECKED_CAST")
    val self: T get() = Any() as T
    fun <V> get(key: String): V? = null
    val args: List<String> get() = emptyList()
}

/** 补全上下文 */
class SuggestContext<T>

// ⚠️ CommandComponent / CommandComponentDynamic 的真实包是
//    taboolib.common.platform.command.**component**（见 component/CommandComponent.kt）。
//    为了让本包内引用也能通过，这里用 typealias 重新导出。
typealias CommandComponent = taboolib.common.platform.command.component.CommandComponent
typealias CommandComponentDynamic = taboolib.common.platform.command.component.CommandComponentDynamic

/**
 * 指令入口。
 * ⚠️ `aliases` 是 **List<String>**（真实签名如此），不是 vararg。
 */
fun command(
    name: String,
    aliases: List<String> = emptyList(),
    description: String = "",
    usage: String = "",
    permission: String = "",
    permissionMessage: String = "",
    permissionDefault: String = "OP",
    permissionChildren: Map<String, String> = emptyMap(),
    hidden: Boolean = false,
    block: CommandBase.() -> Unit
): Any = Any()
