// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 真实 TabooLib 6.3 的 `Plugin` 基类方法（已通过 javap 核实）：
//        onLoad() / onEnable() / onActive() / onDisable()
//     `onActive()` 是**普通方法**，直接 `override` 即可；
//     用 `@Awake(LifeCycle.ACTIVE)` 反而会报
//     "'onActive' hides member of supertype 'Plugin'"。
// ============================================================================

package taboolib.common.platform

import taboolib.common.LifeCycle

annotation class Awake(val value: LifeCycle = LifeCycle.ENABLE)

open class Plugin {
    open fun onLoad() {}
    open fun onEnable() {}
    open fun onActive() {}
    open fun onDisable() {}
}
