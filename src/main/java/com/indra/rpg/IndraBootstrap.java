package com.indra.rpg;

import taboolib.platform.BukkitPlugin;

/**
 * Indra 的插件引导类（plugin.yml 的 {@code main} 指向这里）。
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 为什么需要这个类 —— 一句话版
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * **必须抢在 TabooLib 类加载之前，把系统属性 {@code taboolib.group} 设为
 * {@code com.indra}。** 否则运行期 TabooLib 会认为项目包名是 {@code taboolib}，
 * 于是扫描器把所有 {@code com.indra.*} 类全部当成「非本项目类」丢弃，
 * 导致 {@code pluginInstance} 永远为 null，插件进入「加载成功但什么都不做、
 * 也不报任何错」的静默失活状态。
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 完整根因链（2026-09-20 逐字节反编译核实，务必保留本注释）
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【症状】
 *   服务端启动日志：
 *     [Indra] Loading server plugin Indra v1.2.0
 *     [Indra] Enabling Indra v1.2.0        ← 到此为止，之后什么都没有
 *   插件目录只生成了 {@code datasource.yml}（显式 db(...) 写的）与
 *   {@code kether.yml}（TabooLib 自己的），**没有** {@code config.yml} /
 *   {@code ban.yml} / {@code ban-messages.yml}；
 *   {@code /indra} 指令不存在；**全过程零报错**。
 *
 * 【根因】
 *   {@code taboolib.common.io.ProjectInfoKt}（在运行期下载的 common-util 里）
 *   的静态初始化是：
 *
 *   <pre>
 *   static {
 *       groupId = "taboolib".substringBefore(".taboolib");   // = "taboolib"
 *   }
 *   public static String getGroupId() {
 *       if (groupId.equals("taboolib"))
 *           return System.getProperty("taboolib.group", groupId);
 *       return groupId;
 *   }
 *   </pre>
 *
 *   官方构建流程会用 ASM 的 {@code RelocateRemapper} 把插件 jar 内所有
 *   {@code taboolib.*} 引用改写成 {@code com.indra.taboolib.*}，同时把
 *   {@code ProjectInfoKt} 里那个 {@code "taboolib"} 常量也改成 {@code com.indra}，
 *   于是 {@code getGroupId()} 走 {@code return groupId} 分支直接返回正确值。
 *
 *   Indra 为了绕开「TabooLib 内置 ASM 9.7 不支持 class major 69（Java 25）」
 *   的问题，**禁用了官方后处理任务**，改用自建的 {@code indraMainTask}
 *   （只生成 plugin.yml + env.properties + version.properties，不碰字节码）。
 *   代价就是那次常量改写没了 —— 而这恰恰是 ASM 后处理**唯一的实质性职责**。
 *
 *   于是运行期：
 *     getGroupId()                                → "taboolib"
 *     isProjectClass("com.indra.rpg.Indra")
 *       = startsWith("taboolib") || startsWith("taboolib")
 *                                                 → false     ✗
 *
 *   {@code ClassVisitorHandler.getClasses()} 第一阶段过滤条件正是
 *   {@code isProjectClass(name) && !isLibraryClass(name) && !isAnonymousInnerClass(name)}，
 *   所有 {@code com.indra.*} 类在这一步被全部丢弃 → 返回**空集**。
 *
 *   而 {@code PlatformFactory.inject(Set, long)} 里给 {@code pluginInstance}
 *   赋值的分支是：
 *
 *   <pre>
 *   if (superName.equals(Plugin.class.getName())) {      // 直接继承 Plugin
 *       Plugin.setInstance(rc.newInstance() as Plugin);  // ← pluginInstance
 *   }
 *   </pre>
 *
 *   空集 → 这个分支一次都没进 → {@code pluginInstance == null}。
 *
 *   最后 {@code BukkitPlugin.onEnable()}：
 *   <pre>
 *   if (!TabooLib.isStopped() &amp;&amp; pluginInstance != null) {
 *       pluginInstance.onEnable();        // 被跳过
 *   }
 *   </pre>
 *   null 判断**静默跳过，不抛异常** —— 这就是「零报错」的来源。
 *
 * 【为什么 @Awake / @Config / @RuntimeDependencies 也全失效】
 *   {@code PlatformFactory.init()} 还会为每个 LifeCycle 注册
 *   {@code ClassVisitorAwake}。注册后由 {@code ClassVisitorHandler}
 *   拿「同一份为空/不全的类集合」去驱动 {@code ConfigLoader}、
 *   {@code AwakeLoader} 等所有 Visitor。类集合错了，全部注解一起失效：
 *   · {@code IndraConfig.@Config("config.yml")}   → config.yml 不生成
 *   · {@code BanConfig.@Config("ban.yml")}        → ban.yml 不生成
 *   · {@code CommandModule} 的 command{} 注册     → /indra 不存在
 *   · {@code KotlinRuntime.@RuntimeDependencies}  → stdlib 不下载（但这次
 *     侥幸没炸，因为 common-env 模块自身会顺带把 stdlib 拉下来）
 *
 *   注：{@code datasource.yml} 之所以能生成，是因为 {@code DataManager}
 *   走的是显式 {@code db(...)} 调用而非注解；{@code kether.yml} 则是
 *   {@code minecraft-kether} 模块自带的资源，都不是我们的代码产生的。
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 为什么用「静态块 + 继承」而不是别的写法
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么是 Java 而不是 Kotlin】
 *   JVM 规范保证：类的 {@code <clinit>}（静态初始化）在**该类任何其他代码
 *   之前**、且仅执行一次。而 Kotlin 的 {@code object}／{@code init {}} 无法
 *   表达「在超类构造之前」—— Kotlin 的 init 块严格排在 {@code super()}
 *   之后。这里需要的正是「超类构造之前」，所以必须用 Java 静态块。
 *
 * 【时序为什么成立】
 *   Bukkit 的 PluginClassLoader 执行 {@code Class.forName(main)} 时会：
 *     1) 先跑 {@code IndraBootstrap} 的 {@code <clinit>} → 设好系统属性
 *     2) 再 {@code new IndraBootstrap()}
 *     3) {@code super()} → {@code JavaPlugin.<init>()}
 *     4) {@code IllegalAccess.inject()}
 *     5) {@code TabooLib.lifeCycle(LifeCycle.INIT)}   ← 这一步才开始碰 ProjectInfo
 *   第 1 步稳稳早于第 5 步。
 *
 * 【为什么不用 paper-plugin.yml 或 plugin.yml 额外字段】
 *   TabooLib 的 {@code getGroupId()} 读的是 {@code System.getProperty}，
 *   不读任何描述文件；官方也没有提供「从配置注入 groupId」的入口。
 *   系统属性是唯一通道。
 *
 * 【为什么不直接在 Indra.kt 里设】
 *   {@code Indra} 自身就是 {@code Plugin} 子类，它被加载时
 *   {@code ProjectInfoKt} 早已被 {@code PlatformFactory} 读取完毕，为时已晚。
 *
 * 【长期方案】
 *   等 TabooLib 内置 ASM 升到 9.9+（支持 major 69）之后，恢复官方
 *   {@code taboolibMainTask} 即可 —— 那时与本类等价的内联改写由上游完成，
 *   本类可删除、plugin.yml 的 main 改回 {@code taboolib.platform.BukkitPlugin}。
 *   （见 build.gradle.kts 顶部 [Approach A] 的说明。）
 */
public class IndraBootstrap extends BukkitPlugin {

    static {
        // ── 唯一的实质动作 ─────────────────────────────────────────────
        //  键名 `taboolib.group`、值为 Gradle 的 `group`（gradle.properties）。
        //  ⚠️ 改动 gradle.properties 的 group 时，这里必须同步，
        //     以及 KotlinRuntime.kt 里 `com.indra` 前缀相关的说明。
        //
        //  用 setProperty 而非「已存在就不覆盖」：Bukkit 重载插件时会
        //  复用同一个 JVM，属性已经设过也无所谓（值本来就相同）；
        //  但若哪天改了 group，无条件写入才能保证生效。
        System.setProperty("taboolib.group", "com.indra");
    }
}
