// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  真实：`taboolib.common.util.replaceWithOrder(vararg args): String`
//    把 "{0} {1} ..." 占位符按顺序替换。
//  真实：`taboolib.module.chat.colored(): String` —— & 颜色码 → §
// ============================================================================

package taboolib.common.util

fun String.replaceWithOrder(vararg args: Any?): String = this

fun List<String>.replaceWithOrder(vararg args: Any?): String = joinToString("")
