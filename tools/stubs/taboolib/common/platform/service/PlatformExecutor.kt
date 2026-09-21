// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  真实类型：taboolib.common.platform.service.PlatformExecutor.PlatformTask
//    submit(delay, period) { } 返回它，用于取消周期任务。
// ============================================================================

package taboolib.common.platform.service

import taboolib.common.platform.PlatformTask

class PlatformExecutor {
    interface PlatformTask : taboolib.common.platform.PlatformTask
}
