import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
// ⚠️ 必须用别名：`java` 在脚本里已被 `java` 插件访问器占用，
//    直接写全限定名 `java.io.File` 会报 Unresolved reference 'io'。
import java.io.File as JFile
import java.util.zip.ZipEntry as JZipEntry
import java.util.zip.ZipFile as JZipFile
import java.util.zip.ZipOutputStream as JZipOutputStream
import java.nio.file.Files as JFiles
import java.nio.file.StandardCopyOption as JCopyOption

// ═══════════════════════════════════════════════════════════════════════════
//  ⚠️⚠️  关键：TabooLib 内置 ASM 不支持 Java 25，后处理改为自建  ⚠️⚠️
// ───────────────────────────────────────────────────────────────────────────
//  问题现象：
//      ./gradlew build
//      > Execution failed for TabooLibMainTask{}.
//      > java.lang.IllegalArgumentException: Unsupported class file major version 69
//
//  根因（已逐字节核实）：
//    TabooLib Gradle 插件 2.0.37 把 ASM 9.7 **shade 进了自己的 jar**。
//    ASM 9.7 的 ClassReader 构造时硬校验 class 主版本：
//        readShort(offset + 6)  >  68  ->  throw IllegalArgumentException
//    而 Paper 26.3 / Java 25 编译出的 class 是 major **69**，于是必然失败。
//
//  为什么「换 ASM / 降 jvmTarget / 改插件字节码」都不可取：
//    · 换 ASM 无效：Chrome 式 parent-first 类加载下，插件 classloader
//      始终解析到它自己 shade 的那份（已实测）。
//    · 降 jvmTarget 无效：ClassWriter.visit() 把 version **原样回写**，
//      产物会一起降级，Paper 26.3 反而加载失败。
//    · 改插件字节码（曾尝试）：属于魔改第三方二进制，脆弱且不可维护。
//
//  本方案（Approach A）：**不再调用 TabooLib 的后处理任务**，用一个自建
//  任务做等价工作。TabooLibMainTask 实际只干两件事：
//    1) relocate()   —— 把依赖包重定位到插件内命名空间，
//                       **顺带把 `taboolib.platform.BukkitPlugin` 等入口类打进 jar**
//    2) 生成 plugin.yml + META-INF/taboolib/{env,version}.properties
//  而 Indra **没有任何第三方依赖需要重定位**（taboolib{} 里没有 relocate(...)），
//  所以 relocate() 的「改名」部分对我们是空转（实测：ASM 处理 1390ms）。
//
//  ⚠️ 2026-09-20 实机踩坑：relocate 对我们**不是纯空转**，它还有第二个职责
//     —— 把入口类塞进 jar。首版方案以为可以整个跳过，结果服务端启动即崩：
//         InvalidPluginException: Cannot find main class
//         `com.indra.taboolib.platform.BukkitPlugin'
//     现在改为「跳过改名，但自己补上入口类」，见 [indraMainClass] 与
//     `tasks.jar` 的 `from(...)`。同时把 version.properties 的
//     `skip-taboolib-relocate` / `skip-kotlin-relocate` 置为 true，
//     让运行期也不改写，两边保持原样才能对上（详见 [buildTaboolibVersion]）。
//
//  这样做的收益：
//    · 彻底绕开 ASM 版本问题，而不是给别人的 jar 打补丁；
//    · 产物不再经过 ASM 读写往返，**字节码原样保留**（major 69 天然正确）；
//    · 上游把内置 ASM 升级到 9.9+ 后，删掉本段即可切回官方后处理。
//
//  ⚠️ 已知取舍：
//    1. 不提供 relocate 能力。将来若要 shade 第三方库并改名，需用 Java 25
//       兼容的独立 ASM 自行实现。当前项目无此需求。
//    2. 多个 TabooLib 插件同服时，TabooLib 的类不再各自隔离（因为关掉了
//       relocate）。Bukkit 的 PluginClassLoader 本身按插件隔离，实测可行；
//       真出现冲突再考虑切回 ASM 方案。
// ═══════════════════════════════════════════════════════════════════════════

/** TabooLib 描述文件的固定抬头（与官方产出一致） */
private val TABOOLIB_BANNER = "\n\n#         Powered by TabooLib 6.2         #\n\n\n"

/**
 * 1980-02-01 00:00:00 UTC —— 与 Gradle `isPreserveFileTimestamps = false`
 * 写入的常量时间一致。zip 的 DOS 时间戳最小只能表示到 1980。
 */
private val CONSTANT_TIME = 315532800000L

/**
 * 是否启用 TabooLib 的隔离类加载器（方案 B 的必要条件）。
 *
 * ⚠️ 这里与 `taboolib { env { enableIsolatedClassloader } }` 是**两个独立的
 *    落点**，必须同步为同一个值：
 *      · `env.properties`（本文件生成）→ 运行期 PrimitiveLoader 读取
 *      · `taboolib{}` 扩展 → 构建期写入口
 *    2026-09-20 踩坑：只改了扩展忘了改这里，运行期 IS_ISOLATED_MODE 仍为
 *    false，下载来的 stdlib 挂不回隔离加载器，启动即报「未能找到 kotlin.Lazy」。
 *    提成常量后两边共用，杜绝漂移。
 */
private val INDRA_ISOLATED = true

/**
 * 构建 `META-INF/taboolib/env.properties` 的内容。
 * 键名 / 顺序 / 取值逻辑均按 TabooLib 2.0.37 的 `buildEnv()` 还原
 * （已用反射调用真实方法逐字节比对过）。
 *
 * ⚠️ 该文件**结尾不带换行符**。
 */
fun buildTaboolibEnv(modules: List<String>): String = buildString {
    append(TABOOLIB_BANNER)
    append("debug=false\n")
    append("force-download-in-dev=true\n")
    // 指向国内镜像，避免首次运行时从海外源拉取模块失败
    append("repo-central=https://maven.aliyun.com/repository/central\n")
    append("repo-taboolib=https://repo.tabooproject.org/repository/releases\n")
    append("file-libs=libraries\n")
    append("file-assets=assets\n")
    append("enable-legacy-dependency-resolver=false\n")
    // ── 方案 B 的必要条件 ─────────────────────────────────────────
    //  必须为 true，否则运行期下载的 kotlin-stdlib 会挂在插件自身的
    //  classloader 上，而 PrimitiveLoader 的 isKotlinEnvironment() 检查
    //  走的是 ClassAppender.getClassLoader()，在非隔离模式下取不到
    //  → 依旧报「未能找到 kotlin.Lazy」。
    //  与 taboolib{} 里的 enableIsolatedClassloader = true 必须一致。
    append("enable-isolated-classloader=")
        .append(if (INDRA_ISOLATED) "true" else "false").append('\n')
    append("disable-on-skipped-version=true\n")
    append("disable-on-unsupported-version=true\n")
    append("disable-when-primitive-loader-error=false\n")
    append("module=").append(modules.joinToString(","))
}

/**
 * 构建 `META-INF/taboolib/version.properties` 的内容。
 * 还原自 TabooLib 2.0.37 的 `buildVersion()`（同样已反射比对）。
 *
 * `kotlin-coroutines` 固定写 1.7.3：与官方产出一致，
 * 表示「TabooLib 运行期自带的 coroutines 版本」，不是本项目的编译版本。
 */
/**
 * 构建 `META-INF/taboolib/version.properties` 的内容。
 *
 * ⚠️ `skip-taboolib-relocate` / `skip-kotlin-relocate` 必须为 **true**。
 *
 *    2026-09-20 实机踩坑：这两个开关默认 false 时，TabooLib 运行期会用
 *    jar-relocator 把下载下来的模块 jar 从 `taboolib.*` 重写成
 *    `<group>.taboolib.*`。而官方构建流程是**在构建期**就把插件 jar 内的
 *    全部 `taboolib.*` 引用（含用户代码）一起重写，两边才对得上。
 *    Indra 的方案 A 跳过了构建期 relocate（为了避开 ASM major 69 阻断），
 *    所以运行期也必须关掉改写，否则用户代码里的 `taboolib.*` 全部
 *    NoClassDefFoundError。关掉后：
 *      · 插件代码引用 `taboolib.*`  ✔ 与运行期模块一致
 *      · plugin.yml 的 main 必须是**未改写**的 `taboolib.platform.BukkitPlugin`
 */
fun buildTaboolibVersion(taboolib: String, kotlin: String): String = buildString {
    append(TABOOLIB_BANNER)
    append("kotlin=").append(kotlin).append('\n')
    append("kotlin-coroutines=1.7.3\n")
    append("taboolib=").append(taboolib).append('\n')
    append("skip-kotlin-relocate=true\n")
    append("skip-taboolib-relocate=true")
}

/**
 * 构建 `plugin.yml`。
 * 还原自 TabooLib 的 Bukkit 平台 Builder。
 *
 * ⚠️ 产物**首尾都带空行**（官方格式如此），不要"顺手" trim。
 */
fun buildPluginYml(
    name: String,
    version: String,
    mainClass: String,
    apiVersion: String
): String = buildString {
    append(TABOOLIB_BANNER)
    append("name: ").append(name).append('\n')
    append("main: ").append(mainClass).append('\n')
    append("version: ").append(version).append('\n')
    append("api-version: ").append(apiVersion).append('\n')
    append("folia-supported: true")
}

/**
 * plugin.yml 的 `main`。
 *
 * ⚠️ **不再是** `taboolib.platform.BukkitPlugin`，而是我们自己的引导类。
 *
 *   2026-09-20 实机踩坑（本项是「插件加载成功却静默失活」的正解）：
 *   引导类 `com.indra.rpg.IndraBootstrap` 继承 `BukkitPlugin`，仅做一件事
 *   —— 在静态块里 `System.setProperty("taboolib.group", "com.indra")`。
 *   完整根因链见该类的 Javadoc（务必先读那段再动这里）。
 *
 *   一句话：官方 ASM 后处理会把 `common-util` 里 `ProjectInfoKt` 的
 *   `"taboolib"` 常量改写成 `com.indra`；我们禁用了 ASM，改写没了，
 *   于是运行期 `getGroupId()` 返回 `"taboolib"`，`isProjectClass()`
 *   把所有 `com.indra.*` 类判成"非本项目"全部丢弃 → 类集合为空
 *   → `Plugin.setInstance()` 从不被调用 → `pluginInstance == null`
 *   → 所有生命周期钩子被 `if (pluginInstance != null)` 静默跳过。
 *
 *   系统属性是唯一补救通道（TabooLib 没有从配置文件读 groupId 的入口）。
 *   Java 静态块保证它在超类构造之前执行，时序上稳稳早于 `LifeCycle.INIT`。
 *
 * ⚠️ 该引导类必须**真的在 jar 里**。它是我们自己的源码，由 Kotlin/Java
 *    编译任务直接产出，无需像 `BukkitPlugin` 那样靠 `from(...)` 手动塞。
 *    但 `BukkitPlugin`（父类）仍必须塞进包 —— 否则引导类加载时
 *    `NoClassDefFoundError: taboolib/platform/BukkitPlugin`
 *    （见下方 jar 任务的 `from`）。
 */
val indraMainClass = "com.indra.rpg.IndraBootstrap"

/**
 * TabooLib 版本。
 *
 * ⚠️ 必须 ≥ `6.3.0-f9483b9`。2026-09-20 实机踩坑（需重点记住）：
 *
 *   服务端启动日志出现 5 次
 *       [Indra] 当前 Minecraft 版本不受支持，请等待插件适配。
 *   并且去下载了 `11700.reobf.tiny`（**1.17 的映射表**）。
 *
 *   反编译 `taboolib.module.nms.MinecraftVersion` 定位到根因：
 *     · 该类持有 `supportedVersion: Array<Array<String>>` 版本白名单表
 *     · `getMajor()` = 遍历表做 `runningVersion.contains(表项)` 取下标，
 *       全不匹配则返回 -1
 *     · `getVersionId()` = `tableswitch(getMajor())`，default 分支返回
 *       `0 + getMinor()`（即把一个解析失败的版本号当 minor 用）
 *
 *   对照实测：
 *     · `6.3.0-a1d3953` 的表**最高只到 `26.1.2`** → 匹配 `26.3` 失败
 *       → getMajor() = -1 → 拉 `11700.reobf.tiny` → isSupported() = false
 *     · `6.3.0-f9483b9` 的表末尾为 `26.1.2 → 26.2 → 26.3`，
 *       且 `versionId` 的 tableswitch 扩到 `0..16`，
 *       26.3 返回 **260300**（正确的映射编号）
 *
 *   结论：TabooLib **对 `1.21.x` 的支持早就完备，但 `26.3`
 *   （26.1 之后的年份版本号段）是较新加入的**，老快照的表里没有。
 *   本项与 ASM 无关，也与我们的构建改造无关，纯粹是上游版本表覆盖问题。
 *
 *   ℹ️ 症状表现是「警告 + 功能降级」而非崩溃：插件仍能 enable，
 *      但 NMS 相关能力（封包收发、部分 Bukkit 工具）会走错误分支。
 *      因此日志里若再出现这句警告，第一反应就该是升级本版本号。
 */
val indraTaboolibVersion = "6.3.0-f9483b9"
val indraKotlinVersion = "2.3.20"

/** 重写后处理：TabooLibMainTask 的等价实现，但不使用 ASM。 */
val indraMainTask by tasks.registering {
    group = "taboolib"
    description = "TabooLib 后处理等价实现（生成 plugin.yml 与 META-INF/taboolib/*.properties，不使用 ASM）"

    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)

    // 输入 = jar 任务在 build/ 内的输出
    val inJar = jarTask.flatMap { it.archiveFile }
    inputs.file(inJar)
    inputs.property("taboolibVersion", indraTaboolibVersion)
    inputs.property("kotlinVersion", indraKotlinVersion)
    inputs.property("pluginName", project.name)
    inputs.property("pluginVersion", project.version.toString())
    inputs.property("mainClass", indraMainClass)
    // ⚠️ 引导类是本项目「静默失活」修复的关键，必须存在且含属性注入语句。
    //    这里把它列为任务输入：一旦被误删/改坏，Gradle 会重新执行本任务，
    //    且下面的断言会直接让构建失败，而不是等到实机启动才发现。
    val bootstrapFile = layout.projectDirectory.file(
        "src/main/java/com/indra/rpg/IndraBootstrap.java"
    )
    inputs.file(bootstrapFile)

    // 输出 = dist/ 下的最终交付物（与输入是不同文件）
    val outJar = layout.projectDirectory.file("dist/Indra-${project.version}.jar")
    outputs.file(outJar)

    doLast {
        val source = inJar.get().asFile
        val target = outJar.asFile
        target.parentFile.mkdirs()
        // 先写临时文件再原子改名，避免中途失败留下半个 jar
        val tmp = JFile(target.parentFile, target.name + ".tmp")

        // ── 读取模块清单 ──
        // ⚠️ 唯一真相来源是 taboolib{} 扩展本身（Env.getModules()），**不是**硬编码表。
        //    2026-09-19 踩过坑：加 install(Kether) 后忘了同步兜底表，
        //    产物 env.properties 里 module= 缺 kether，运行期 TabooLib 不加载该模块，
        //    KetherShell 会 NoClassDefFoundError。现在从扩展直接读，杜绝漂移。
        //    jar 内若已有 env.properties（理论上不会有，taboolibMainTask 已禁用）则以其为准。
        val modules: List<String> = run {
            val fromJar = runCatching {
                JZipFile(source).use { zip ->
                    val e = zip.getEntry("META-INF/taboolib/env.properties") ?: return@use null
                    zip.getInputStream(e).bufferedReader(Charsets.UTF_8).use { r ->
                        r.readLines().firstOrNull { it.startsWith("module=") }
                            ?.removePrefix("module=")
                            ?.split(",")
                            ?.filter { it.isNotBlank() }
                    }
                }
            }.getOrNull()
            val fromExtension = runCatching {
                io.izzel.taboolib.gradle.TabooLibExtension::class.java
                    .let { project.extensions.getByType(it) }
                    .env.modules.sorted()
            }.getOrNull()
            (fromJar ?: fromExtension) ?: error(
                "无法解析 TabooLib env 模块清单：jar 内无 env.properties 且读取 taboolib{} 扩展失败"
            )
        }

        logger.lifecycle("[Indra] 后处理：${modules.size} 个 env 模块")

        // ── 断言：引导类必须存在且真的注入了 taboolib.group ──────────
        //  这是「插件加载成功但静默失活」的第一道防线。
        //  没有这句 setProperty，运行期 ProjectInfoKt.getGroupId() 会返回
        //  "taboolib"，导致所有 com.indra.* 类被扫描器丢弃、
        //  pluginInstance 为 null、全部生命周期钩子静默跳过（且零报错）。
        run {
            val src = bootstrapFile.asFile
            check(src.exists()) {
                """
                引导类缺失：${src.path}
                plugin.yml 的 main 指向 $indraMainClass，但源文件不存在，
                运行期会 InvalidPluginException: Cannot find main class。
                """.trimIndent()
            }
            val text = src.readText()
            check(text.contains("""System.setProperty("taboolib.group"""")) {
                """
                引导类 ${src.name} 未包含 taboolib.group 注入语句！
                该语句是修复「插件加载成功却零报错静默失活」的唯一手段：
                缺少它 → ProjectInfoKt.getGroupId() 返回 "taboolib"
                → isProjectClass("com.indra.*") = false
                → 类扫描集合为空 → Plugin.setInstance() 不被调用
                → pluginInstance = null → 全部生命周期钩子被静默跳过。
                请不要删除这行。详见该文件 Javadoc。
                """.trimIndent()
            }
            check(text.contains("com.indra")) {
                "引导类 ${src.name} 里的 group 值必须是 com.indra（与 gradle.properties 的 group 一致）。"
            }
            logger.lifecycle("[Indra] 引导类自检通过：taboolib.group = com.indra")
        }

        val env = buildTaboolibEnv(modules).toByteArray(Charsets.UTF_8)
        val ver = buildTaboolibVersion(indraTaboolibVersion, indraKotlinVersion).toByteArray(Charsets.UTF_8)
        val yml = buildPluginYml(
            name = project.name,
            version = project.version.toString(),
            mainClass = indraMainClass,
            // ⚠️ 26.3 是「年份.序号」版本号，api-version 也照写 `26.3`，
            //    不是 1.21.x，更不是 1.13。
            //    写 1.13 会让 Paper 判定为老插件 → 走 legacy 重映射路径，
            //    而 26.1+ Mojang 已不再混淆、Spigot 映射不存在，remap 必炸。
            //    Paper 官方文档给的合法区间是 1.13 - 26.1.2，示例即 `26.1.2`。
            apiVersion = "26.3"
        ).toByteArray(Charsets.UTF_8)

        // ── 原样复制所有条目，仅替换 / 追加上述三个文件 ──
        // 关键：class 字节**不做任何处理**，保持 Kotlin 编出的 major 69。
        JZipFile(source).use { zip ->
            JZipOutputStream(tmp.outputStream().buffered()).use { out ->
                val seen = HashSet<String>()

                //  ⚠️ 时间戳必须沿用源条目：ZipEntry 默认 time=-1 会写成「当前时间」，
                //     那样每次构建产物都不同，可复现构建就废了（见 tasks.jar 的说明）。
                fun entry(name: String, time: Long): JZipEntry =
                    JZipEntry(name).also { it.time = time }

                for (entry in zip.entries()) {
                    val name = entry.name
                    // 跳过旧的描述文件（由我们重新生成）
                    if (name == "plugin.yml" ||
                        name == "META-INF/taboolib/env.properties" ||
                        name == "META-INF/taboolib/version.properties"
                    ) continue
                    if (entry.isDirectory) {
                        if (!seen.add(name)) continue
                        out.putNextEntry(entry(name, entry.time))
                        out.closeEntry()
                        continue
                    }
                    if (!seen.add(name)) continue
                    out.putNextEntry(entry(name, entry.time))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }

                fun write(name: String, bytes: ByteArray) {
                    if (!seen.add(name)) return
                    // 新写的三个描述文件用固定时间戳（1980-02-01，与 Gradle
                    // isPreserveFileTimestamps=false 的取值一致）
                    out.putNextEntry(entry(name, CONSTANT_TIME))
                    out.write(bytes)
                    out.closeEntry()
                }
                write("plugin.yml", yml)
                write("META-INF/taboolib/env.properties", env)
                write("META-INF/taboolib/version.properties", ver)
            }
        }

        // 原子替换到最终路径
        JFiles.move(tmp.toPath(), target.toPath(), JCopyOption.REPLACE_EXISTING)
        logger.lifecycle("[Indra] 已生成 plugin.yml + env.properties + version.properties（ASM 未参与）")
    }
}

// [官方管线恢复 2026-09-21]
//  jvmTarget 已降至 21（major 65），官方 taboolibMainTask 的 ASM 9.7 可正常处理，
//  构建期重定位把组名 com.indra 焊进 jar（含运行期下载模块的常量改写链路），
//  运行期不再需要 -Dtaboolib.group。自建 indraMainTask 保留注册但不再挂入 assemble。

// ══════════════════════════════════════════════════════════════════════════
//  方案 B 的版本一致性断言
//
//  KotlinRuntime.kt 的 @RuntimeDependency 注解参数是编译期常量，无法引用
//  gradle.properties，只能硬编码。这里做一道校验，防止「升级 Kotlin 插件
//  版本时忘了同步注解」——那会导致运行期下载的 stdlib 与编译期版本不一致。
//
//  参考踩坑：PrimitiveSettings 读的键是 `taboolib.kotlin.stdlib`（默认
//  1.8.22），**不是** version.properties 里的 `kotlin=`，所以不能指望
//  用后者影响下载版本，必须靠本断言守住。
// ══════════════════════════════════════════════════════════════════════════
val indraVerifyKotlinVersions by tasks.registering {
    group = "verification"
    description = "校验 KotlinRuntime.kt 硬编码的 Kotlin 版本与 gradle.properties 一致"

    val kotlinFile = layout.projectDirectory.file(
        "src/main/kotlin/com/indra/rpg/common/runtime/KotlinRuntime.kt"
    )
    inputs.file(kotlinFile)
    inputs.property("kotlinVersion", indraKotlinVersion)

    doLast {
        val text = kotlinFile.asFile.readText()
        val declared = Regex("""kotlin-stdlib:([0-9][^"]*)""")
            .find(text)?.groupValues?.get(1)
        check(declared == indraKotlinVersion) {
            """
            KotlinRuntime.kt 的 stdlib 版本与构建配置不一致，必须同步：
              gradle.properties / build.gradle.kts → $indraKotlinVersion
              KotlinRuntime.kt @RuntimeDependency  → ${declared ?: "<未找到>"}
            两处不一致会导致运行期下载的 Kotlin 与编译期不是同一版本。
            """.trimIndent()
        }
        logger.lifecycle("[Indra] Kotlin 版本一致性校验通过：$declared")
    }
}

tasks.named("check") {
    dependsOn(indraVerifyKotlinVersions)
}

plugins {
    java
    //  Kotlin 2.3（本构建固定），jvmTarget 已降至 21 以适配官方 ASM 后处理
    //    （2.1.x / 2.2.x 会直接报 "unknown JVM target version: 25"）
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    // ⚠️ 必须 2.0.37+：2.0.31 内部调用 Gradle 的 `archivePath`，
    //     该属性在 Gradle 8 已被移除，会导致 "Could not get unknown
    //     property 'archivePath' for task ':jar'"。2.0.37 起已修复。
    id("io.izzel.taboolib") version "2.0.37"
}

taboolib {
    env {
        // ── 基础（最低要求）──────────────────────────────
        install(Basic, Bukkit, BukkitUtil)
        // ── 工具 / 事件 ─────────────────────────────────
        install(BukkitNMSUtil)
        // ── 界面与语言 ──────────────────────────────────
        //    ⚠️ 注意：i18n 模块的访问器叫 `I18n`，不是 `MinecraftI18n`
        //       （对应字符串 "minecraft-i18n"，已从插件 class 常量表核实）
        install(BukkitUI, MinecraftChat, MinecraftEffect, I18n)
        // ── 指令 ───────────────────────────────────────
        install(CommandHelper)
        // ── 脚本引擎（封禁自动解封的后续动作脚本用）────
        //    移植自 Phoenix：ban 模块的 Auto-Unban.Actions 是 Kether 脚本
        install(Kether)
        // ── 数据持久化（本地 SQLite + MySQL 双存储）──────
        install(Database)
        install("database-ptc-object")

        // ── 隔离类加载器（方案 B 的必要条件）──────────────
        //  开启后，运行期下载的库会挂进一个独立的 URLClassLoader
        //  （taboolib.common.classloader.IsolatedClassLoader），
        //  而 ClassAppender.getClassLoader() 会优先返回它。
        //  这正是「Kotlin 靠下载也能被找到」的关键：
        //  PrimitiveLoader 里 isKotlinEnvironment() 用的是
        //  ClassAppender.getClassLoader()，隔离模式下能看到下载的 stdlib。
        //  来源对照：BilibiliVideo-Fork 用同一套组合（隔离 + 运行期下载 kotlin）。
        //  ⚠️ 取值必须与文件顶部的 [INDRA_ISOLATED] 一致（env.properties 由
        //     后者生成），两边不同步会导致运行期行为与预期不符。
        enableIsolatedClassloader = INDRA_ISOLATED
    }
    description {
        // [官方管线] Paper 26.3 必须写 api-version: 26.3；官方默认 1.13 会触发 legacy 重映射直接崩
        bukkitApi("26.3")
    }

    version {
        // ⚠️ 必须 6.3+：26.1 起 Mojang 不再混淆，Spigot 译名失效
        // ⚠️ 且必须 ≥ 6.3.0-f9483b9：老快照的版本白名单表里没有 26.3，
        //    会导致「当前 Minecraft 版本不受支持」并拉错映射表。
        //    完整踩坑记录见上方 [indraTaboolibVersion] 的注释。
        //    注意这里**不要**改回字面量，统一引用常量。
        taboolib = indraTaboolibVersion
        // ── 方案 B：Kotlin 不打进包（改由 @RuntimeDependencies 运行期下载）──
        //  ⚠️ 这一项必须为 true，否则 relocate 会把下载的 stdlib 改名成
        //     `taboolib.kotlin.*`，而 PrimitiveLoader 检查的是**原始名**
        //     `kotlin.Lazy`，两者对不上 → 仍旧「未能找到 kotlin.Lazy」。
        skipKotlinRelocate = true
    }
}

repositories {
    // 国内镜像优先（官方源在部分网络不可达）
    maven("https://maven.aliyun.com/repository/public")
    maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
    // TabooLib 产物只在这里有
    maven("https://repo.tabooproject.org/repository/releases/")
    // Paper API
    maven("https://repo.papermc.io/repository/maven-public/")
    // ⚠️ PacketEvents 官方仓库。注意：
    //    - Maven Central 上的 packetevents 已不再更新
    //    - JitPack 上的同坐标需要认证（401）
    //    - 只有 codemc 这个源是公开可直接拉的
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net")
    maven("https://jitpack.io")
    // 官方源兜底
    mavenCentral()
}

/**
 * 入口类引导配置：只用来把 TabooLib 的 Bukkit 入口类（`platform-bukkit`
 * 模块，`taboolib.platform.BukkitPlugin`）拉进来打进产物 jar。
 *
 * 单独开一个 configuration 而不是从 compileOnly 里捞，是因为 Gradle 9
 * 的 `resolvedConfiguration.files` 已经不存在（实测编译报
 * `Unresolved reference 'files'`），而 compileOnly 里混着 Paper / Vault /
 * PacketEvents 一大堆，按文件名筛太脆。
 */
val indraBootstrap by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // 只要这一个模块的 class，不要它的传递依赖
    isTransitive = false
}

// [官方管线 2026-09-21] paper-api 26.3 的变体元数据声明 org.gradle.jvm.version=25，
//  而本构建编译产物为 21（适配官方 ASM 9.7）；仅提升 compileClasspath 的目标属性
//  让解析通过，产物字节码仍是 major 65，Paper 可正常运行。
configurations.compileClasspath {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    // ══════════════════════════════════════════════════════════
    //  服务端 API —— Paper 26.3（Build 19）
    //  26.3 是新的年份版本号，不是 1.21.x
    //  编译目标 Java 25（class major 69）
    // ══════════════════════════════════════════════════════════
    compileOnly("io.papermc.paper:paper-api:26.3.build.19-alpha")

    // ── 经济系统（Vault API）────────────────────────────
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // ── 封包处理（TAB 栏 / 消息路由 / 图片预览共用）──────
    //    ⚠️ 必须 2.14.0-SNAPSHOT：
    //       2.13.0 的 ClientVersion 只到 V_26_2，没有 V_26_3，
    //       无法识别 Paper 26.3 的协议号 777。
    //       2.14.0-SNAPSHOT 已含 V_26_3（实测 2026-09-19 构建）。
    //    坐标来源：repo.codemc.io（Maven Central 无、JitPack 需认证）
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0-SNAPSHOT") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // ── 入口类（会被打进 jar，见 tasks.jar 的 from）────────────
    //    `platform-bukkit` 模块 = BukkitPlugin 等 6 个类，必须随包发布
    indraBootstrap("io.izzel.taboolib:platform-bukkit:$indraTaboolibVersion")

    // ── 方案 B：Kotlin 不入包，改为运行期下载 ──────────────────
    //  2026-09-20 由方案 A（taboo 嵌入，产物 1.9 MB）切换而来。
    //
    //  ⚠️ 这里**故意不加** taboo("org.jetbrains.kotlin:kotlin-stdlib:...")。
    //     方案 A 就是靠那一行把 stdlib 嵌进产物的；去掉后产物会瘦到 ~300 KB，
    //     代价是**首次启动必须联网**下载 Kotlin（以及 TabooLib 的 28 个模块）。
    //
    //  Kotlin 的获取由 KotlinRuntime.kt 的 @RuntimeDependencies 负责，
    //  配合 env{} 的 enableIsolatedClassloader = true 生效。
    //  完整机制与验证方式见该文件头部注释。

    // ── 本地 jar（可选）：把 ImagePreviewer / KcPiano 等无 Maven 坐标的
    //     jar 放进 libs/ 目录即可编译期引用；目录不存在也不会报错。
    if (file("libs").exists()) {
        compileOnly(fileTree("libs"))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    // ⚠️ Kotlin 2.2 起 kotlinOptions 已废弃，统一改用 compilerOptions
    compilerOptions {
    //  官方管线约束：taboolib 插件 2.0.37 内置 ASM 9.7 上限 major 68，构建期降为 21（Paper 运行 major 65 无碍）
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-jvm-default=enable")
    }
}

java {
    //  官方管线约束：taboolib 插件 2.0.37 内置 ASM 9.7 上限 major 68，构建期降为 21（Paper 运行 major 65 无碍）
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    // 不用 toolchain：避免 Gradle 强制下载 JDK 25 导致离线构建失败。
    // 用 `java -version` 确认构建机 JDK 就是 25 即可（见 README 前置检查）。
}

// jar 任务只产出「原始字节码包」，不注入任何描述文件。
// ⚠️ 输出**必须留在 build/ 内部**（Gradle 自管的输出目录），
//    这样它的 up-to-date 判定才是可靠的。
//    最终产物由 indraMainTask 写到 dist/，两者不共用同一个文件
//    —— 否则会出现「任务覆盖自己的输入」导致产物随机损坏。
tasks.jar {
    archiveBaseName.set("Indra")
    destinationDirectory.set(layout.buildDirectory.dir("raw-jar"))

    // ── 可复现构建 ──────────────────────────────────────────────
    //  默认 jar 会写入真实时间戳，导致每次 clean build 的 sha256 都不同
    //  （2026-09-20 实测：两次 clean build 得到 ce6c2e04… 与 49237b2f…）。
    //  关掉时间戳保留 + 固定条目顺序后，配合 indraMainTask 沿用源条目的
    //  time，最终产物才能真正字节一致、可用 sha256 校验。
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    // ── 把 TabooLib 的 Bukkit 入口类打进包 ───────────────────────
    //  ⚠️ 2026-09-20 实机踩坑：plugin.yml 的 main 类必须由 Bukkit 的
    //     PluginClassLoader 在插件 jar 内找到（`Class.forName(main)`），
    //     而 TabooLib 的入口类 `taboolib.platform.BukkitPlugin` 属于
    //     `platform-bukkit` 模块、不在我们自己的源码里。
    //     官方是让 relocate 步骤顺手把它塞进去的；我们跳过了 relocate，
    //     就必须自己塞，否则启动即崩：
    //       InvalidPluginException: Cannot find main class `...BukkitPlugin'
    //     这只取 platform-bukkit 一个模块（6 个类，约 12 KB），
    //     不含 platform-bukkit-impl，后者由 TabooLib 在运行时下载。
    from({
        indraBootstrap.files.map { zipTree(it) }
    }) {
        exclude("META-INF/**")
    }
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
