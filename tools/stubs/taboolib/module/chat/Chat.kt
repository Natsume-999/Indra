// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  真实：`taboolib.module.chat.colored()` 把 & 颜色码转成 §（并支持十六进制 RGB）。
// ============================================================================

package taboolib.module.chat

fun String.colored(): String = replace('&', '§')

fun List<String>.colored(): List<String> = map { it.replace('&', '§') }
