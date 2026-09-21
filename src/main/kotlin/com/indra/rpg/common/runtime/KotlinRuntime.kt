package com.indra.rpg.common.runtime

import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency

/**
 * 运行期依赖声明（方案 B 的核心）。
 *
 * ═══════════════════════════════════════════════════════════════════════
 * 为什么需要这个文件
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Indra 原用「方案 A」：`dependencies {}` 里写
 * `taboo("org.jetbrains.kotlin:kotlin-stdlib:...")` 把 Kotlin 嵌进插件 jar，
 * 产物因此有 1.9 MB（stdlib 占约 1.7 MB）。
 *
 * 「方案 B」把 Kotlin 移出产物、改为**首次启动下载**，产物瘦到约 300 KB。
 *
 * ═══════════════════════════════════════════════════════════════════════
 * 为什么删掉 taboo(...) 之后还能启动（关键机制，已反编译核实）
 * ═══════════════════════════════════════════════════════════════════════
 *
 * TabooLib 启动时会做一次 Kotlin 环境检查：
 *
 * ```java
 * // taboolib.common.PrimitiveLoader#lambda$loadAll$1 字节码
 * load("common-env", ...);                        // ← 先加载 common-env 模块
 * if (!TabooLib.isKotlinEnvironment()) {          // ← 偏移 25，检查在**之后**
 *     throw new IllegalStateException("无法启动 Kotlin 环境。(未能找到 kotlin.Lazy)");
 * }
 * ```
 *
 * 即：**只要加载 `common-env` 这一步顺带把 stdlib 拉下来，检查就能过。**
 * `common-env` 的依赖树里本来就含 `kotlin-stdlib`。
 *
 * 而 `isKotlinEnvironment()` 内部是：
 *
 * ```java
 * // TabooLib#isKotlinEnvironment
 * Class.forName("kotlin.Lazy", false, ClassAppender.getClassLoader());
 * ```
 *
 * `ClassAppender.getClassLoader()` 会看 `IS_ISOLATED_MODE`：
 * - `enableIsolatedClassloader = false` → 返回插件 classloader
 * - `enableIsolatedClassloader = true`  → 返回 `IsolatedClassLoader`（URLClassLoader）
 *
 * 这就是 `taboolib {}` 必须开 `enableIsolatedClassloader = true` 的原因 ——
 * 下载来的 stdlib 挂在隔离加载器上，只有隔离模式才被 `Class.forName` 看到。
 *
 * ⚠️ `version { skipKotlinRelocate = true }` 也必须为 true：若为 false，
 *    下载的 stdlib 会被重定位成 `taboolib.kotlin.*`，而检查找的是**原始名**
 *    `kotlin.Lazy`，照样失败。
 *
 * 来源对照：BilibiliVideo-Fork 用同一套组合（隔离加载器 + 运行期下载 Kotlin）。
 *
 * ═══════════════════════════════════════════════════════════════════════
 * 这个 object 会不会被扫到？（2026-09-20 反编译核实）
 * ═══════════════════════════════════════════════════════════════════════
 *
 * 结论：**不需要在任何地方显式引用 `KotlinRuntime`**，注解是自动生效的。
 *
 * 证据链（全部来自 6.3.0-f9483b9 的真实字节码）：
 *
 * 1) `PrimitiveLoader#loadAll` → 加载 `common-util` 模块，其 `ClassVisitorHandler`
 *    是在 `LifeCycle.CONST` 阶段注册扫描任务的（`init()` 里对每个非 NONE
 *    的 LifeCycle 调 `registerLifeCycleTask`）。
 *
 * 2) `ClassVisitorHandler#lambda$getClasses$3` 调
 *    `ProjectScannerKt.getRunningClassMap()` —— 里面对**插件 jar 自身**
 *    做 `JarFile.stream()`，逐个条目按 `endsWith(".class")` 取出来，
 *    用 `ReflexClass.of(LazyClass, InputStream)` 构造，**只读字节、不加载类**。
 *
 * 3) `ClassVisitorHandler#lambda$getClasses$1` 做第一轮过滤：
 *    ```java
 *    isProjectClass(name) && !isLibraryClass(name) && !isAnonymousInnerClass(name)
 *    ```
 *    其中 `isProjectClass` 是**按前缀**判定：
 *    ```java
 *    name.startsWith(ProjectInfoKt.getGroupId())      // = "com.indra"
 *        || name.startsWith(ProjectInfoKt.getTaboolibId())   // = "taboolib"
 *    ```
 *
 * 因此：`com.indra.rpg.common.runtime.KotlinRuntime` 天然落在
 * `com.indra` 前缀内，会被自动扫到并解析 `@RuntimeDependencies`。
 *
 * ⚠️ 两个前置条件（改动时别破坏）：
 *   · `gradle.properties` 的 `group` 必须是 `com.indra`。若改成别的，
 *     这里要同步 —— 否则**所有** `@Awake` / `@SubscribeEvent` / 本注解
 *     都会静默失效（扫描不到，不报错）。
 *   · 本文件不能被写进 `<any>.library.` 或 `<any>.libs.` 包路径下，
 *     那是 `isLibraryClass` 的排除特征。
 *
 * 代价（可接受）：object 的静态初始化在扫描阶段**不会**被触发
 * （`ReflexClass` 是惰性解析），所以这里不写 `init {}` 之类有副作用的代码。
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ⚠️ 代价（切方案 B 前必须知道）
 * ═══════════════════════════════════════════════════════════════════════
 *
 * **首次启动必须联网**。离线部署会因找不到 `kotlin.Lazy` 而禁用插件。
 * 下载源由 `env.properties` 的 `repo-central` 决定（本项目已指向阿里云镜像）。
 * 下载落盘位置：`plugins/Indra/libraries/`。
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ⚠️ 版本号必须与构建配置同步（已加构建期断言）
 * ═══════════════════════════════════════════════════════════════════════
 *
 * 注解参数是**编译期常量**，无法引用 gradle.properties，所以只能硬编码。
 * 为防止漂移，`build.gradle.kts` 的 `indraVerifyKotlinVersions` 任务会
 * 读本文件、比对 gradle.properties 的版本，不一致直接构建失败。
 */
@RuntimeDependencies(
    // ── Kotlin 标准库（方案 B 的核心依赖）─────────────────────────
    //  ⚠️ `!` 前缀 = 强制下载。
    //     不带 `!` 时 TabooLib 会先做存在性检测（test 字段）决定是否跳过；
    //     而本场景恰恰是「包内没有 stdlib」，若被误判为已存在就会跳过下载，
    //     然后 loadAll 的检查立刻抛「未能找到 kotlin.Lazy」。
    //     BilibiliVideo 的 RuntimeEnv.kt 里也踩过类似坑（它把 test 注释掉了）。
    //
    //  ⚠️ transitive = false：stdlib 自身无必要传递依赖，减少下载量。
    RuntimeDependency(
        value = "!org.jetbrains.kotlin:kotlin-stdlib:2.3.20",
        transitive = false
    ),

    // ── Kotlin 协程（TabooLib 的 submit 链 / Kether 等模块依赖）──────
    //  版本需与 version.properties 里的 kotlin-coroutines 保持一致（1.7.3）。
    RuntimeDependency(
        value = "!org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3",
        transitive = false
    )
)
object KotlinRuntime
