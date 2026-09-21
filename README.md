# Indra

> Minecraft RPG 服务器基础功能插件模板
> **TabooLib 6.3+ + Kotlin** · Paper 26.3 · 双存储（本地 SQLite / MySQL）

> **状态**：✅ **已在真实 Paper 26.3 服务端实机验证通过**（2026-09-21）——
> 8 大模块全部启用、配置自动生成、`/indra` 全套命令可用、SQLite 数据层就绪。
> 构建已切换至 **完整官方 TabooLib 管线**（构建期重定位，组名自动焊入 jar），
> **无需任何特殊启动参数**。详见下方「📢 2026-09-21 重大更新」。

---

## 📢 2026-09-21 重大更新：已切换完整官方构建管线

本模板此前因「TabooLib 内置 ASM 读不了 Java 25 字节码」而采用自建后处理
（方案 A，需要 `-Dtaboolib.group` 启动参数）。**现已恢复官方管线**，改动四处：

1. `jvmTarget` 25 → **21**（major 65，官方 ASM 9.7 可处理；Paper 运行无碍）；
2. 恢复官方 `taboolibMainTask`（构建期重定位 + 注入入口类 + 生成描述文件）；
3. `compileClasspath` 变体属性提升为 25（paper-api 26.3 元数据要求）；
4. `description { bukkitApi("26.3") }`——官方默认 `1.13` 在 26.3 会触发 legacy 重映射崩溃。

效果：`plugin.yml` 的 `main` 为 `com.indra.taboolib.platform.BukkitPlugin`（官方重定位改写），
`skip-taboolib-relocate=false` 使运行期下载的模块同样改写到 `com.indra.*` 命名空间，
组名自动焊入 jar，**启动脚本零特殊参数**。自建 `indraMainTask` 与 `IndraBootstrap`
保留在源码中作为机制文档与历史参考，不再参与构建。新增 **MenuModule 面板模块**（第 8 个模块）。

---

## 📚 文档

| 文档 | 内容 |
|---|---|
| [docs/功能清单.md](docs/功能清单.md) | 功能路线图、实机验证清单 |
| [docs/TabooLib-6.3-开发实战指南.md](docs/TabooLib-6.3-开发实战指南.md) | 开发实战指南：生命周期/注解/命令 DSL/菜单 UI、**静默失败根因模型**、官方管线配方、26.3 踩坑全记录 |

---

## 一、这个模板是什么

Indra 是一套 **RPG 服务器地基**，不是完整玩法服。它解决了三件事：

| 能力 | 说明 |
|---|---|
| **禁止原版生物生成** | 逐层过滤（世界 / 生成原因 / 实体类型），为自定义怪物让路 |
| **RPG 内容骨架** | 怪物等级强化、玩家等级经验、聊天等级前缀、死亡惩罚、排行榜 |
| **双存储持久化** | 同一套代码，改一行配置即可在 SQLite 与 MySQL 之间切换 |

所有玩法参数都在 `config.yml` 里，**改配置即可，不用重编译**。

---

## 二、环境要求

| 项目 | 版本 |
|---|---|
| 服务端 | **Paper 26.3**（Build 19，对应 `paper-26.3-19.jar`） |
| **JDK** | **25**（Paper 26.1+ 强制要求，不可用 17） |
| Kotlin | **2.3.20**（**必须 2.3+**，见下方说明） |
| TabooLib | `6.3.0-f9483b9`（**必须 ≥ 此版本**，见下方「版本白名单」踩坑） |
| TabooLib Gradle 插件 | `2.0.37`（**必须 2.0.37+**，见下方说明） |
| 构建工具 | Gradle 9.3.0（wrapper 已内置，指向国内镜像） |

### ⚠️ 为什么 TabooLib Gradle 插件必须 2.0.37+

| 插件版本 | Gradle 9 下结果 |
|---|---|
| 2.0.31 | ❌ `Could not get unknown property 'archivePath' for task ':jar'` |
| **2.0.37** | ✅ 正常 |

原因：2.0.31 内部读取 Gradle 的 `archivePath`，该属性在 **Gradle 8.0 已被移除**，
而 Gradle 7.x（唯一还支持它的版本）又**跑不了 JDK 20+**。
所以「Gradle 9 + 插件 2.0.37」是唯一可行组合。

### ⚠️ 为什么 Kotlin 必须 2.3+

Paper 26.3 的 API 是 **Java 25 字节码**（class major 69），Kotlin 必须同时
**读取** Java 25 类文件、**产出** Java 25 目标字节码。实测结论：

| Kotlin 版本 | `-jvm-target 25` 结果 |
|---|---|
| 2.1.10 | ❌ `error: unknown JVM target version: 25` |
| 2.2.20 | ❌ 同上 |
| **2.3.20** | ✅ 正常，产出 major 69 字节码 |

> `JvmTarget.JVM_25` 是 **Kotlin 2.3** 才引入的枚举值。

### ⚠️⚠️ TabooLib 内置 ASM 不支持 Java 25（已用自建后处理替代）
> **【2026-09-21 已解决】** 通过将 `jvmTarget` 降至 21（major 65）恢复官方管线，本章内容保留作机制文档。

**这是本模板最隐蔽的一个坑**，若不处理会直接卡死在构建最后一步：

```
Execution failed for TabooLibMainTask{}.
> java.lang.IllegalArgumentException: Unsupported class file major version 69
```

**根因**（已逐字节核实）：TabooLib Gradle 插件 2.0.37 把 **ASM 9.7 shade 进了自己的
jar**。ASM 9.7 的 `ClassReader` 在构造时硬校验 class 主版本：

```java
// ASM 9.7 ClassReader 构造器反编译结果
readShort(offset + 6)  >  68  →  throw new IllegalArgumentException("Unsupported class file major version " + v)
```

Paper 26.3 / Java 25 编译出的 class 是 **major 69**，于是必然失败。

**常见绕过思路全部无效**（实测）：

| 思路 | 结果 |
|---|---|
| `buildscript` 里加 ASM 9.9.1 覆盖 | ❌ Gradle 插件类加载器优先解析插件内部那份，外部 ASM 进不来 |
| 依赖 Gradle 自带的 `asm-9.9.jar` | ❌ 同上 |
| 把 `jvmTarget` 降到 24 | ❌ 治标不治本，且会**连带降级产物**（见下） |
| 直接改插件 jar 的字节码 | ❌ 魔改第三方二进制，脆弱、不可维护（**已废弃该方案**） |

> 补充发现：`TabooLibClassVisitor` 传给 ASM 的 `Opcodes.ASM9` 在 Groovy 里是
> **按名运行时解析**的（常量池存 `String "ASM9"` + `invokedynamic`），拿到的永远
> 是 589824。所以「换个新 ASM 就能解决」这个前提本身就不成立。

**本模板的方案：不再调用 TabooLib 的后处理任务，改用自建任务做等价工作。**

`TabooLibMainTask` 实际只干两件事：

1. `relocate()` —— 把依赖包重定位到插件内命名空间（**这一步用 ASM**），
   **并顺带把 TabooLib 的 Bukkit 入口类 `taboolib.platform.BukkitPlugin` 打进 jar**
2. 生成 `plugin.yml` + `META-INF/taboolib/{env,version}.properties`（**纯字符串拼接**）

而 **Indra 没有任何需要重定位的第三方依赖**（`taboolib{}` 里没有 `relocate(...)`），
所以 `relocate()` 的「改名」部分对本项目是空转。于是 `build.gradle.kts` 里的
`indraMainTask` 只做第 2 步 —— 生成那三个描述文件，**完全不碰 ASM**，
原生 `taboolibMainTask` 被禁用。

> 🔥 **2026-09-20 实机踩坑（重要）**：`relocate()` 对我们**不是纯空转**，
> 它还有第二个职责 —— 把入口类塞进产物 jar。首版方案以为能整个跳过，
> 结果服务端启动即崩：
>
> ```
> InvalidPluginException: Cannot find main class `com.indra.taboolib.platform.BukkitPlugin'
> Caused by: java.lang.ClassNotFoundException: com.indra.taboolib.platform.BukkitPlugin
> ```
>
> 现在改为「跳过改名，但自己补上入口类」，配套三处改动：
>
> | 改动 | 位置 |
> |---|---|
> | `main` 改用**未改写**的 `taboolib.platform.BukkitPlugin` | `indraMainClass` |
> | `platform-bukkit` 模块的 6 个类打进 jar | `tasks.jar { from(...) }` |
> | `skip-taboolib-relocate` / `skip-kotlin-relocate` 置 `true` | `buildTaboolibVersion()` |
>
> 第三处是关键：官方流程是**构建期**把 jar 内所有 `taboolib.*` 引用一起改写，
> **运行期**再把下载的模块 jar 同样改写，两边才对得上。我们跳过了构建期改写，
> 运行期也必须关掉，否则用户代码里的 `taboolib.*` 会全线 `NoClassDefFoundError`。

> 🔥 **2026-09-20 第二次启动崩溃（`kotlin.Lazy` 找不到）**
>
> 修完入口类后仍然崩，日志：
>
> ```
> IllegalStateException: 无法启动 Kotlin 环境。(未能找到 kotlin.Lazy)
>   at taboolib.common.PrimitiveLoader.lambda$loadAll$1(PrimitiveLoader.java:198)
>   → ExceptionInInitializerError → 插件被禁用
> ```
>
> **根因**：`dependencies {}` 里缺 `taboo(...)`。
>
> 反编译 `PrimitiveLoader` 字节码确认：
> - `deps()` 里**完全没有 kotlin**，`rule()` 也不重定位 kotlin
>   —— TabooLib **自己不下载、不处理 kotlin-stdlib**，它期望插件 jar 里自带；
> - `taboo(...)` 为空时，taboolib 插件退化为「**纯源码打包**」：
>   只塞 primitive 加载器，不嵌 Kotlin。
>
> ℹ️ 两个易错点：`taboo` 是 `DependencyHandler` 的扩展，**只能在
> `dependencies {}` 里调用**（写在 `taboolib {}` 里会报候选不匹配）；
> 另外 `PrimitiveSettings` 读的键是 `taboolib.kotlin.stdlib`（默认 `1.8.22`），
> **不是** `version.properties` 里的 `kotlin=`，别指望用那个键改版本。

---

### 方案 A → 方案 B：Kotlin 改由运行期下载

> 📌 **v1.2.0 起采用方案 B**（本文档其余章节中凡涉及「Kotlin 嵌入产物」的
> 描述均为方案 A 时期的记录，保留作对照）。

| | 方案 A（v1.1.x） | 方案 B（v1.2.0+，当前） |
|---|---|---|
| Kotlin 来源 | `taboo(...)` 嵌进 jar | 首次启动从 Maven 下载 |
| 产物大小 | **2.0 MB** | **约 300 KB** |
| 类数 | 1093 | 约 300 |
| `kotlin/*` 是否在包内 | ✅ 是 | ❌ 否 |
| 离线可用 | ✅ 可以 | ❌ **首次启动必须联网** |
| 关键配置 | `taboo(...)` | `enableIsolatedClassloader = true`<br>`skipKotlinRelocate = true`<br>`@RuntimeDependencies` |

**为什么删掉 `taboo(...)` 之后还能启动** —— 关键在启动顺序（已反编译核实）：

```java
// taboolib.common.PrimitiveLoader#lambda$loadAll$1
load("common-env", ...);                    // 偏移 21 ← 先加载该模块
if (!TabooLib.isKotlinEnvironment()) {      // 偏移 25 ← 检查在**之后**
    throw new IllegalStateException("无法启动 Kotlin 环境。(未能找到 kotlin.Lazy)");
}
```

`common-env` 的依赖树里本就含 `kotlin-stdlib`，所以「加载它」这一步
顺带把 stdlib 拉了下来；检查随之通过。

而 `isKotlinEnvironment()` 内部是
`Class.forName("kotlin.Lazy", false, ClassAppender.getClassLoader())`，
`ClassAppender.getClassLoader()` 在 `IS_ISOLATED_MODE` 为 true 时返回
`IsolatedClassLoader`（下载的库挂在这里）。**这就是必须开隔离加载器的原因**。

⚠️ 三处配置必须同时满足，少一个就回到「未能找到 kotlin.Lazy」：

| 配置 | 位置 | 作用 |
|---|---|---|
| `enableIsolatedClassloader = true` | `taboolib { env { } }` | 让 `ClassAppender` 返回隔离加载器 |
| `enable-isolated-classloader=true` | 生成的 `env.properties` | 运行期 `PrimitiveSettings.IS_ISOLATED_MODE` 取值 |
| `skipKotlinRelocate = true` | `taboolib { version { } }` | 否则下载的 stdlib 被改名为 `taboolib.kotlin.*`，而检查找的是**原始名** `kotlin.Lazy` |

前两项已提成同一个 Gradle 常量 `INDRA_ISOLATED`，不会漂移。

> ℹ️ Kotlin 版本有两处硬编码（`KotlinRuntime.kt` 的注解参数 + `build.gradle.kts`
> 的 `indraKotlinVersion`），因为注解参数是编译期常量、引用不到 `gradle.properties`。
> 已加 `indraVerifyKotlinVersions` 任务做构建期断言，不一致直接 fail。

---

### 🔥 第四次实机问题（★ 最严重）：插件加载成功却零报错静默失活

> **这是本项目至今最隐蔽的一个坑** —— 插件不崩、不报错、日志一切正常，
> 但**什么都不做**：`@Awake` 不触发、`@Config` 不生成配置文件、命令不注册。
> 极易被误判为「配置问题」或「模块没写对」。

**症状**（服务端启动日志，2026-09-20）：

```
[Indra] Loading server plugin Indra v1.2.0
[Indra] Enabling Indra v1.2.0          ← 正常启用，之后一片寂静
（无任何 Indra 后续输出，无异常，无警告）
```

用户反馈原文：

> 实则只加载了 `datasource.yml` 和 `kether.yml` 文件，但是加载没有任何报错。
> Indra 命令也没有了。

**关键线索：为什么偏偏是这两个文件？**

| 文件 | 来源 | 注解驱动？ |
|---|---|---|
| `datasource.yml` | `DataManager` 里**显式** `db(...)` 调用生成 | ❌ 代码直调 |
| `kether.yml` | `minecraft-kether` 模块**自带资源** | ❌ 随模块落盘 |

**没有任何一个注解驱动的配置文件被生成** —— 这是「注解处理管线整条没跑」的指纹。

---

#### 根因链（逐字节反编译核实）

**step 1 —— `pluginInstance` 为 null 导致静默跳过**

`taboolib/platform/BukkitPlugin.onEnable()`：

```java
if (!TabooLib.isStopped() && pluginInstance != null) {
    pluginInstance.onEnable();          // ← 整段被跳过时，无任何日志
}
```

**step 2 —— `pluginInstance` 的唯一赋值点**

`taboolib/common/platform/PlatformFactory.inject(Set, long)`：

```java
if (rc.getStructure().getSuperclass()?.getName().equals(Plugin.class.getName())) {
    Plugin.setInstance(rc.newInstance() as Plugin);     // ← 全项目仅此一处
}
```

**step 3 —— 这个 `Set` 来自 `ClassVisitorHandler.getClasses()`，它做两阶段过滤**

```java
// 阶段一
isProjectClass(name) && !isLibraryClass(name) && !isAnonymousInnerClass(name)

// 阶段二
checkPlatform() && checkRequires()
```

其中：

```java
static boolean isProjectClass(String name) {
    return name.startsWith(ProjectInfoKt.getGroupId())
        || name.startsWith(ProjectInfoKt.getTaboolibId());
}
```

**step 4 —— ★ 真正的元凶：`ProjectInfoKt.groupId` 的静态初值**

```java
static {
    groupId = "taboolib".substringBefore(".taboolib");   // == "taboolib"
}

public static String getGroupId() {
    if (groupId.equals("taboolib")) {
        return System.getProperty("taboolib.group", groupId);   // ← 唯一逃生口
    }
    return groupId;
}
```

- 官方构建流程里，**ASM 后处理会把这个常量改写成 `com.indra`**，
  于是 `getGroupId()` 返回 `com.indra`，扫描器正常识别项目类；
- **而我们为了绕开「ASM 9.7 不支持 major 69」禁用了 `taboolibMainTask`**
  （见第二节），**那次常量改写也就一并没了**；
- 常量维持 `"taboolib"` → `System.getProperty("taboolib.group")` 为 null
  → `getGroupId()` 返回 `"taboolib"`
  → 所有 `com.indra.*` 被判为「非本项目类」丢弃
  → **阶段一过滤结果为空集**
  → `PlatformFactory.inject()` 遍历 0 个类
  → `Plugin.setInstance()` 从不调用 → `pluginInstance == null`
  → `BukkitPlugin.onEnable()` 整段跳过 → **静默失活**。

> 已反编译产物确认 `com.indra.rpg.Indra extends taboolib.common.platform.Plugin`
> （**直接继承**，`superName` 判定是能过的），排除「Kotlin `object` 编译后父类是 Object」这一假设。

#### 仿真对照（`tools/sim/Sim.java`，可复现）

```
场景: 修复前（禁用 ASM，无引导类）
  ProjectInfoKt 静态字段初值   = "taboolib"
  System.getProperty(taboolib.group) = null
  → getGroupId() 返回值        = "taboolib"
  阶段一过滤通过数 = 0 / 9      ← 全部 DROP

场景: 修复后（Java 静态块注入 taboolib.group=com.indra）
  System.getProperty(taboolib.group) = com.indra
  → getGroupId() 返回值        = "com.indra"
  阶段一过滤通过数 = 9 / 9      ← 全部 PASS
```

#### 修复方案：抢在 TabooLib 类加载前注入系统属性

新增 `src/main/java/com/indra/rpg/IndraBootstrap.java`：

```java
package com.indra.rpg;

import taboolib.platform.BukkitPlugin;

public class IndraBootstrap extends BukkitPlugin {
    static {
        System.setProperty("taboolib.group", "com.indra");
    }
}
```

`plugin.yml` 的 `main` 从 `taboolib.platform.BukkitPlugin` 改指向它。

**为什么用 Java 而不是 Kotlin？** 因为必须保证注入**发生在任何 TabooLib 类被加载之前**，
而 JVM 的 `<clinit>` 语义给了这个保证：

| 时机 | 状态 |
|---|---|
| Bukkit `Class.forName("com.indra.rpg.IndraBootstrap")` | 分配内存，**不执行**静态块 |
| `newInstance()` | 执行 `IndraBootstrap.<clinit>` → 注入属性 |
| 父类 `BukkitPlugin.<clinit>` | **在子类静态块之后**（JVM 先跑父类 clinit？**否** —— 见下） |

> ⚠️ 精确的 JVM 语义：初始化子类时**先初始化父类**。但 `BukkitPlugin` 自身
> 并不会在 `<clinit>` 里读 `getGroupId()` —— 真正读取发生在运行期
> `TabooLib.lifeCycle()` 触发的 `PlatformFactory` 扫描阶段，那时属性早已注入。
> 因此这个时序是安全的。**关键是不能把注入写在 `Indra.kt` 里** ——
> `Indra` 是 `object`，它的初始化本身就依赖 TabooLib 的类加载完成，
> 那时扫描已经结束，为时已晚。

**配套三处改动**（`build.gradle.kts`）：

| 改动 | 内容 |
|---|---|
| `indraMainClass` | `"taboolib.platform.BukkitPlugin"` → `"com.indra.rpg.IndraBootstrap"` |
| `indraMainTask` 输入 | 新增 `bootstrapFile` 输入，保证引导类改动会触发重建 |
| `indraMainTask` 自检 | 三条 `check` 断言：文件存在 / 含 `System.setProperty("taboolib.group"` / 含 `com.indra`，构建期即 fail |

构建日志会打印：

```
[Indra] 后处理：28 个 env 模块
[Indra] 引导类自检通过：taboolib.group = com.indra
[Indra] 已生成 plugin.yml + env.properties + version.properties（ASM 未参与）
```

> 💡 **长期方案**：等上游 TabooLib Gradle 插件把内置 ASM 升到 9.9+（支持 major 69），
> 就可以删掉 `indraMainTask` 切回官方后处理，引导类也随之可以移除。
> 在那之前，这个 Java 引导类是「禁用 ASM」的**必要补偿**。

---

### 🔥 第三次实机问题：TabooLib 版本白名单不含 26.3

**症状**（服务端启动日志，2026-09-20）：

```
[10:52:36] [Indra] Loading server plugin Indra v1.2.0
[10:52:36] [Indra] 当前 Minecraft 版本不受支持，请等待插件适配。   ← 出现 5 次
[10:52:36] [Indra-1.2.0] 正在下载资源 ... 11700.reobf.tiny        ← 1.17 的映射表！
[10:52:40] [Indra] Enabling Indra v1.2.0                          ← 但仍能启用
```

**注意这不是崩溃**，是**功能降级**：插件能 enable，但 NMS 相关能力会走错分支。
所以很容易被忽略 —— 而它恰好被之前的启动崩溃掩盖了几轮。

**根因**（反编译 `taboolib.module.nms.MinecraftVersion` 定位）：

| 环节 | 事实 |
|---|---|
| `supportedVersion` | 版本白名单表 `Array<Array<String>>` |
| `getMajor()` | 遍历表做 `runningVersion.contains(表项)` 取下标；全不匹配返回 **-1** |
| `getVersionId()` | `tableswitch(getMajor())`，`default` 分支返回 `0 + getMinor()` |
| `runningVersion` | 由 `Bukkit.getServer().version` 按 `MC:` 切分得到 → **`26.3`** |

对照两个版本的**白名单表末尾**（实测字节码）：

| 版本 | 表末尾 | 结果 |
|---|---|---|
| `6.3.0-a1d3953` | … `1.21.11`, **`26.1.2`** | 匹配 `26.3` 失败 → 拉 `11700.reobf.tiny` ❌ |
| `6.3.0-f9483b9` | … `1.21.11`, `26.1.2`, **`26.2`**, **`26.3`** | 正确返回 `260300` ✅ |

新版还把 `versionId` 的 tableswitch 从 `0..14` 扩到 `0..16`，
并新增常量 `V26_2` / `V26_3`。

> 💡 **结论**：TabooLib 对 `1.21.x` 的支持早已完备，但 `26.3` 是较新加入的。
> 这**与 ASM 无关、与我们的构建改造无关**，纯粹是上游版本表覆盖问题。
> 因此 `build.gradle.kts` 里的 `indraTaboolibVersion` 改成常量引用，
> `taboolib { version { taboolib = ... } }` 也统一引用它，避免再次漂移。

**排查口诀**：日志里见到「当前 Minecraft 版本不受支持」+ 下载的映射表编号
明显偏小（如 `11700` = 1.17），就是版本白名单没覆盖 → 升级 TabooLib。

这样做的好处：

- 彻底绕开 ASM 版本问题，而不是给别人的 jar 打补丁；
- 产物**不经过 ASM 读写往返**，Kotlin 编出的字节码原样保留，major 69 天然正确；
- 构建**可复现**：连续 4 次构建产物 sha256 完全一致。

> ⚠️ **已知取舍**：
> 1. 本方案不提供 `relocate()` 能力。若将来 Indra 需要把第三方库
>    shade 进来并改名，需要自行用 **Java 25 兼容的 ASM 9.9+** 实现这一步，
>    或等上游插件升级内置 ASM 后删掉 `indraMainTask` 切回官方后处理。
>    当前项目无此需求。
> 2. 因为关掉了 relocate，多个 TabooLib 插件同服时 TabooLib 的类不再各自
>    隔离。Bukkit 的 `PluginClassLoader` 本身按插件隔离，实测可行；
>    真出现冲突再考虑切回 ASM 方案。

> **为什么不能靠降 `jvmTarget` 绕过**：ASM 的 `ClassWriter` 会把 `ClassReader`
> 读到的 version **原样写入输出头**，而不是保留原文件版本。一旦字节码经过
> `taboolibMainTask` 处理，产物版本就由 ASM 侧决定，**降 jvmTarget 会连带把
> 产物降级**，Paper 26.3 反而加载失败。

### ⚠️ TabooLib env 模块名坑（已实测校正）

| 写法 | 结果 |
|---|---|
| `MinecraftI18n` | ❌ `Unresolved reference 'MinecraftI18n'` |
| **`I18n`** | ✅ 正确（对应内部字符串 `minecraft-i18n`） |

已从 `taboolib-gradle-plugin` 的 class 常量表逐一核对，可用访问器包括：
`Basic` `Bukkit` `BukkitUtil` `BukkitUI` `BukkitNMSUtil` `BukkitNMSItemTag`
`MinecraftChat` `MinecraftEffect` `I18n` `Kether` `CommandHelper`
`Database` `Ptc` `PtcObject` `DatabasePlayer` 等；
也可直接用字符串形式，如 `install("database-ptc-object")`（**合法**）。

同时 Kotlin 2.2 起 `kotlinOptions { }` 已废弃，构建脚本须改用 `compilerOptions { }`：

```kotlin
tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        // ⚠️ Kotlin 2.3 起 `-Xjvm-default` 已废弃，改用 `-jvm-default`
        //    可选模式：disable / enable / no-compatibility（不再是 all）
        freeCompilerArgs.add("-jvm-default=enable")
    }
}
```

> ### ⚠️ 关于 MC 版本号：26.3 不是 1.21.x
>
> Mojang 自 2026 年起把版本号从 `1.x` 改为「**年份.序号**」：
> `1.21.5` → `26.1` → `26.2` → **`26.3`（Wilderness Bound，2026-09-15 发布）**
>
> | 影响项 | 说明 |
> |---|---|
> | **Java 25** | Paper 26.1+ 要求 Java 25，构建机必须装 JDK 25 |
> | **TabooLib 6.3+** | 26.1 起 Mojang 不再混淆，Spigot 译名失效，6.3 以下无法运行 |
> | **Paper 26.3 是 ALPHA** | 实验构建，API 可能变动 |

> **不需要 `plugin.yml`** —— TabooLib 会在构建时自动生成。

---

## 三、目录结构

```
Indra/
├── build.gradle.kts                       构建脚本（模块安装清单在这里）
├── settings.gradle.kts                    工程名 = Indra
├── gradle.properties                      group / version
├── src/main/
│   ├── java/com/indra/rpg/
│   │   └── IndraBootstrap.java            ★★ 引导类（plugin.yml 的 main）
│   │                                          静态块注入 taboolib.group=com.indra
│   ├── kotlin/com/indra/rpg/
│   │   ├── Indra.kt                       插件主体（object : Plugin）
│   │   ├── config/
│   │   │   └── IndraConfig.kt             配置绑定（@Config / @ConfigNode）
│   │   ├── module/
│   │   │   └── ModuleManager.kt           模块注册中心
│   │   ├── mob/
│   │   │   ├── MobSpawnModule.kt          ★ 禁止原版生物生成
│   │   │   └── MobLevelModule.kt          怪物等级强化
│   │   ├── player/
│   │   │   └── PlayerModule.kt            等级 / 经验 / 击杀统计 / 死亡惩罚
│   │   ├── chat/
│   │   │   └── ChatModule.kt              聊天等级前缀
│   │   ├── ban/                            ★ 封禁系统（移植自 Phoenix v1.16.0）
│   │   │   ├── BanModule.kt                模块入口
│   │   │   ├── BanApi.kt                   封禁 / 踢出 / 白名单 / 警告 / 历史
│   │   │   ├── BanConfig.kt                配置绑定（ban.yml / ban-messages.yml）
│   │   │   ├── BanMessages.kt              消息渲染（{0} 占位 + 颜色码）
│   │   │   ├── BanDatabase.kt              数据接口
│   │   │   ├── BanDatabaseSql.kt           数据实现（Table DSL，SQLite/MySQL 双方言）
│   │   │   ├── BanAutoUnban.kt             到期自动解封 + Kether 动作
│   │   │   ├── BanPlayerListener.kt        登录拦截（含绕过漏洞修复）
│   │   │   ├── BanCommands.kt              指令集（9 条子命令）
│   │   │   └── event/                      5 个可取消事件
│   │   ├── sell/                          ★ 出售系统（A3，格式兼容 Phoenix）
│   │   │   ├── SellModule.kt               模块入口
│   │   │   ├── SellConfig.kt               规则/界面扫描加载 + 默认文件释放
│   │   │   ├── SellRule.kt                 出售规则（权重池 / 区间随机）
│   │   │   ├── SellTable.kt                界面模板（字符网格布局 + Bind）
│   │   │   ├── ItemMatcher.kt              Arim 匹配串裁剪版（name/lore/type/amount…）
│   │   │   ├── SellEngine.kt               结算引擎（两阶段：先算后付再清物）
│   │   │   ├── SellGui.kt                  界面渲染（InventoryHolder）
│   │   │   ├── SellListener.kt             点击/拖拽/关闭交互拦截
│   │   │   ├── SellTradeLog.kt             成交审计落库（indra_sell_log）
│   │   │   └── SellCommands.kt             指令集（open/list/reload/log）
│   │   ├── common/
│   │   │   ├── db/IndraDb.kt               ★ SQLite / MySQL 方言切换 + HikariCP
│   │   │   ├── economy/VaultBridge.kt      ★ Vault 反射桥（零编译期依赖）
│   │   │   ├── script/KetherRunner.kt      Kether 脚本执行 + 变量替换
│   │   │   └── util/TimeUtil.kt            时长解析（"7d" → 秒）
│   │   ├── command/
│   │   │   └── CommandModule.kt            /indra 指令集
│   │   ├── data/
│   │   │   ├── DataManager.kt              ★ 双存储 + 缓存 + 自动保存
│   │   │   └── IndraPlayerData.kt          数据实体（PTC Object，自动建表）
│   │   └── util/
│   │       └── Msg.kt                      颜色码 / 占位符
│   └── resources/
│       ├── config.yml                      功能配置
│       ├── datasource.yml                  ★ 存储配置（MySQL / SQLite 开关）
│       ├── ban.yml                         封禁模块设置
│       ├── ban-messages.yml                封禁消息模板
│       ├── sell.yml                        出售模块设置
│       ├── sell/Example.yml                出售规则示例（首启释放到 plugins/Indra/sell/）
│       └── table/Example.yml               出售界面示例（首启释放到 plugins/Indra/table/）
├── tools/
│   ├── verify.sh                          离线编译校验（不依赖 Gradle）
│   └── stubs/                             TabooLib 编译占位（仅离线校验用）
├── libs/                                  （可选）放本地 jar：ImagePreviewer 等
├── gradlew / gradle/wrapper/              Gradle Wrapper（已内置，指向国内镜像）
└── dist/                                  构建产物（Indra-1.6.0.jar）
```

> `tools/stubs/` 只在 `verify.sh` 离线校验时用作 TabooLib 占位，
> **正式构建（`./gradlew build`）不使用它**，走的是 TabooLib 官方真包。

---

## 四、Paper 26.3 API 破坏性变更（实测清单）

以下均为**用真实 Paper 26.3 API 编译时逐条踩出来的**，迁移旧代码时必看：

| 旧写法（1.21.x 及以前） | 26.3 新写法 | 说明 |
|---|---|---|
| `Attribute.GENERIC_MAX_HEALTH` | `Attribute.MAX_HEALTH` | Mojang 去掉 `GENERIC_` 前缀 |
| `Attribute.GENERIC_ATTACK_DAMAGE` | `Attribute.ATTACK_DAMAGE` | 同上 |
| adventure-api 4.x | **adventure-api 5.2.0** | Paper 26.3 的 BOM 升到 5.2.0 |
| — | 新增 `net.kyori.adventure.text.object` 包 | `Player` 现在继承 `PlayerHeadObjectContents.SkinSource`，**用旧 adventure 会编译不过** |
| `EntityRemoveEvent` 取消 | `entity.removeWhenFarAway = false` | 该事件**不可取消**（已验证） |
| `entity.customName = String` | `entity.customName(Component)` | String 版已废弃 |
| `AsyncPlayerChatEvent` | `AsyncChatEvent` + `e.renderer { }` | renderer 是 **4 参数** 函数 |

### Paper 26.3 依赖坐标速查（实测）

```
io.papermc.paper:paper-api:26.3.build.19-alpha      ← 对应用户的 paper-26.3-19.jar
net.kyori:adventure-bom:5.2.0                        ← Paper 26.3 强制
com.google.guava:guava:33.6.0-jre
com.google.code.gson:gson:2.14.0
org.joml:joml:1.10.9                                ← Display 实体矩阵变换需要
org.slf4j:slf4j-api:2.0.17
com.mojang:brigadier:1.3.11
```

---

## 五、构建

### 前置检查（重要）

```bash
java -version     # 必须输出 25.x
```

> 构建机 **JDK 必须是 25**。Paper 26.3 的 API 是 Java 25 字节码（major 69），
> JDK 17/20/21 在读 Paper API 时就会直接失败。
> 若只装了一个低版本 JDK，建议用 `sdk install java 25-...` 或 `JAVA_HOME=...` 指定。

> 🔥 **2026-09-20 踩坑**：本构建脚本**刻意不使用 Gradle toolchain**
> （见 `build.gradle.kts` 第 600 行注释：「避免 Gradle 强制下载 JDK 25 导致离线构建失败」），
> 而是**直接继承运行 Gradle 的那个 JDK**。
> 于是若 `JAVA_HOME` 指向 JDK 20，会报：
>
> ```
> Execution failed for task ':compileJava'.
> > Java compilation initialization error
>     error: invalid source release: 25
> ```
>
> ⚠️ 注意这个报错出现在 `:compileJava` 而不是 `:compileKotlin` ——
> **不要误判为 Kotlin 版本问题**。Kotlin 编译由 `compilerOptions { jvmTarget }` 控制，
> 而 `compileJava` 用的是 `sourceCompatibility = JavaVersion.VERSION_25`（第 598 行），
> 它受**运行 Gradle 的 JDK 本身**约束，`jvmTarget` 改它没用。
>
> 正确做法是显式指定 JDK 25：
>
> ```bash
> JAVA_HOME=/opt/jdk25/jdk-25.0.4.1+1 ./gradlew build
> ```
>
> 或在 `gradle.properties` 里固化（推荐，避免每次敲）：
>
> ```properties
> org.gradle.java.home=/opt/jdk25/jdk-25.0.4.1+1
> ```

### 正式构建

```bash
# 首次构建（自动下载 TabooLib + 依赖）
./gradlew build

# 产物位置
dist/Indra-1.6.0.jar   →  丢进服务端 plugins/
```

> Wrapper 已内置（Gradle 9.3.0），**无需预装 Gradle**。
> `gradle-wrapper.properties` 里的发行包地址已指向国内镜像
> （`mirrors.cloud.tencent.com`），因 `services.gradle.org` 在部分网络不可达。

**首次运行**会在 `plugins/Indra/` 生成 `config.yml` 与 `datasource.yml`。

#### 构建会输出什么

```
> Task :compileKotlin
> Task :jar
> Task :indraVerifyKotlinVersions
[Indra] Kotlin 版本一致性校验通过：2.3.20
> Task :indraMainTask
[Indra] 后处理：28 个 env 模块
[Indra] 已生成 plugin.yml + env.properties + version.properties（ASM 未参与）
> Task :taboolibMainTask SKIPPED

BUILD SUCCESSFUL in 46s
```

| 任务 | 作用 |
|---|---|
| `:jar` | 只产出「原始字节码包」到 `build/raw-jar/`，**不注入任何描述文件**；同时把 `platform-bukkit` 模块的 6 个入口类（`taboolib.platform.BukkitPlugin` 等）原样并入 |
| `:indraVerifyKotlinVersions` | 校验 `KotlinRuntime.kt` 硬编码的 Kotlin 版本与 `build.gradle.kts` 的 `indraKotlinVersion` 一致，不一致直接 fail（见「方案 A → 方案 B」） |
| `:indraMainTask` | **自建后处理**：读 `jar` 产物 → 写入 `plugin.yml`、`META-INF/taboolib/{env,version}.properties` → 原子落到 `dist/Indra-<版本>.jar`。class 字节原样透传，**完全不使用 ASM**（见第二节） |
| `:taboolibMainTask` | TabooLib 官方后处理，**已被显式禁用**（构建日志中显示 `SKIPPED`），避免重复跑并再次触发 ASM 报错 |

产物自检：

```bash
# 方案 B（v1.6.0）预期分布：{52: 26, 69: 78}，共 104 个 class，约 279 KB
#   69 = Java 25 （77 个）——本项目源码（com/indra/**），Paper 26.3 要求
#                            含 IndraBootstrap（★ 引导类，plugin.yml 的 main）
#   52 = Java 8  （26 个）——TabooLib 的 primitive 加载器 + Bukkit 入口
#          · taboolib/common/**   20 —— primitive 加载器（ClassAppender /
#                                        PrimitiveLoader / PrimitiveSettings …）
#          · taboolib/platform/**   6 —— Bukkit 入口（IndraBootstrap 的父类）
# 关键判定（方案 B 的四条不变式）：
#   1) `kotlin/**` **必须为 0 个** —— stdlib 改由运行期下载，
#      若这里出现 kotlin/*，说明方案 B 失效（多半是
#      kotlin.stdlib.default.dependency 被改成 true，或误加了 taboo(...)）
#   2) `taboolib/platform/BukkitPlugin.class` **必须在包内** —— 否则
#      `InvalidPluginException: Cannot find main class`
#   3) `com/indra/rpg/common/runtime/KotlinRuntime.class` **必须在包内**
#      —— 它是运行期下载 stdlib 的声明载体
#   4) `com/indra/rpg/IndraBootstrap.class` **必须在包内，且常量池含
#      "taboolib.group" 与 "com.indra"** —— 否则插件加载成功但静默失活（见第二节）
python3 -c "
import zipfile
z=zipfile.ZipFile("dist/Indra-1.6.0.jar"); m={}; n_kt=0
for n in z.namelist():
    if n.endswith('.class'):
        if n.startswith('kotlin/'): n_kt+=1
        v=int.from_bytes(z.read(n)[6:8],'big'); m[v]=m.get(v,0)+1
print(m, 'kotlin/* =', n_kt)"
```

### 快速语法校验（不依赖 Gradle）

```bash
bash tools/verify.sh
# 预期：BUILD OK: 77 class, 0 error (major=69, TabooLib=real)
#       ✔ 已对照真实 TabooLib 6.3 校验，签名一致
```

只需网络能到 Maven 镜像即可，**不碰 GitHub、不跑 Gradle**，
用于快速确认「源码语法 + Paper 26.3 / TabooLib 6.3 API 签名」是否正确。

#### ★ v1.6.0 起：默认用**真实 TabooLib jar** 编译

脚本会优先在 `~/.gradle/caches` 里找真实的 `*-6.3.0-*.jar`：

* **找得到** → 直接拿真包编译，**能真正校验 TabooLib 签名**（首选，最可信）。
* **找不到**（干净机器、还没跑过 Gradle）→ 退回 `tools/stubs/` 的离线桩，
  并在结尾打印醒目告警，明确告诉你「本次未校验真实签名」。

> **为什么要改**：桩是手写的近似物，漂移了就会「桩过真包不过」。
> 本轮就抓到一次 —— `CommandContext` 的 `int()`/`double()` 扩展**真实位于
> `ExtraContextKt`**，`CommandContext` 类本身没有这些方法；
> 若桩把它们写在类里，源码写错也照样过。**桩越宽容越危险。**
>
> 要跑真实校验：先 `./gradlew build` 一次（把 TabooLib 拉进 Gradle 缓存），再重跑本脚本。

> `tools/stubs/` 现在只作**兜底**用。改动源码时若用到新的 TabooLib API，
> 仍建议同步更新桩（保证无网机器也能校验），但**最终以真实 jar 编译为准**。
>
> ⚠️ `adventure-text-serializer-legacy` 是 `LegacyComponentSerializer` 的宿主，
> 本脚本会显式下载（Gradle 侧靠 paper-api 传递解析拿到，手工列表容易漏）。

---

## 六、存储切换（重点）

编辑 `plugins/Indra/datasource.yml`：

### 方式一：本地 SQLite（默认，零依赖）

```yaml
database:
  enable: false          # ← 关键
sqlite:
  file: data.db
```

数据落在 `plugins/Indra/data.db`，开箱即用，适合单机测试或小服。

### 方式二：MySQL（生产环境 / 多服共享）

```yaml
database:
  enable: true           # ← 关键
  type: mysql
  host: 127.0.0.1
  port: 3306
  user: root
  password: "your_password"
  database: indra        # 需提前 CREATE DATABASE indra;
  flags:
    useSSL: false
    serverTimezone: Asia/Shanghai
```

**两种模式的表结构完全一致**，切换后 `/indra reload` 即可，无需改代码。

> 表结构由 **PTC Object 注解**声明（`@TableName` / `@Id` / `@UniqueKey` / `@Alias`），
> `persistentContainer(source) { new<T>() }` 在 `Container.init()` 里会自动建表（幂等），
> **不需要手写 DDL，也不需要 `IndraTables.kt`**。

---

## 七、指令与权限

| 指令 | 说明 | 权限 |
|---|---|---|
| `/indra` | 帮助菜单 | — |
| `/indra status` | 运行状态（存储模式、模块、拦截开关） | `indra.admin` |
| `/indra reload` | 重载配置 | `indra.admin` |
| `/indra modules` | 已加载模块列表 | `indra.admin` |
| `/indra level [玩家]` | 查看等级信息 | `indra.admin` |
| `/indra exp add <玩家> <数量>` | 增加经验 | `indra.admin` |
| `/indra top [数量]` | 等级排行榜 | `indra.admin` |
| `/indra mob level` | 查看准星处怪物等级 | `indra.admin` |
| `/indra ban <玩家> [时长] [原因]` | 封禁（在线/离线自动分流） | `indra.admin` |
| `/indra unban <玩家>` | 解封 | `indra.admin` |
| `/indra kick <玩家> [原因]` | 请出 | `indra.admin` |
| `/indra whitelist <玩家> <true/false>` | 白名单 | `indra.admin` |
| `/indra warn <玩家> [原因]` | 警告（达阈值自动封禁） | `indra.admin` |
| `/indra delwarn <玩家> <序号/all>` | 删除警告 | `indra.admin` |
| `/indra warnings <玩家>` | 警告列表 | `indra.admin` |
| `/indra banstatus <玩家>` | 封禁状态 | `indra.admin` |
| `/indra banhistory <玩家>` | 操作历史 | `indra.admin` |
| `/indra sell [界面]` | 打开出售界面 | — |
| `/indra sell open <玩家> [界面]` | 为他人打开出售界面 | `indra.admin` |
| `/indra sell list` | 列出界面与规则数 | `indra.admin` |
| `/indra sell reload` | 重载规则与界面 | `indra.admin` |
| `/indra sell log <玩家> [条数]` | 成交记录（最近 N 条） | `indra.admin` |

别名：`/ind`

> 时长写法见 `ban.yml` 的 `Time-Format.Duration`：`30s` / `30m` / `12H` / `7d` / `2w` / `1M` / `1y`。
> 不填时长 = 永久封禁。

---

## 七·五、封禁系统（移植自 Phoenix）

### 功能

| 能力 | 说明 |
|---|---|
| 在线 / 离线封禁 | 按 `Player-ID` 策略（默认 **UUID**）记录，改名无法绕过 |
| 时效 / 永久封禁 | 时长 `7d` / `1y` 等，到期自动解封 |
| 自动解封 | 启动即扫一次，之后按 `Auto-Unban.Interval-Seconds` 周期扫描 |
| 解封后动作 | Kether 脚本，变量 `{player} {uuid} {reason} {duration} {ban_time} {unban_time} {banning_admin}` |
| 警告系统 | 累计达 `Warning.Auto-Ban-Threshold` 自动封禁 |
| 白名单 | 可选开关，独立于 Bukkit 原生 whitelist |
| 操作历史 | BAN / UNBAN / KICK / WHITELIST / UNWHITELIST / WARN 六类 |
| 可扩展事件 | 5 个 `BukkitProxyEvent`，均可取消：`OnlinePlayerBanEvent` / `OfflinePlayerBanEvent` / `PlayerUnbanEvent` / `PlayerKickEvent` / `PlayerWhitelistEvent` |

### 数据表

| 表 | 用途 |
|---|---|
| `indra_ban` | 封禁主表，主键 `playerID` |
| `indra_ban_warn` | 警告记录，自增 `id` |
| `indra_ban_history` | 操作历史，自增 `id` |

建表由 `BanDatabaseSql` 在首次访问时通过 Table DSL 自动完成，**无需手写 DDL**；
方言差异（自增主键、TEXT 能否作主键、布尔存储）在建表处按 SQLite / MySQL 分叉。

### 相对 Phoenix 的两处改动

1. **修复封禁绕过**：Phoenix 原版在 `unbanTime` 为空（永久封禁）时只看白名单开关，
   白名单关闭就直接 `event.allow()`，**永久封禁的玩家能正常进服**。
   这里改为先判 `isBanned`，封禁优先于白名单。

2. **补齐警告闭环**：Phoenix 有 `Warning.Auto-Ban-Threshold` 配置但**没有触发点**
   （只入库不解封）。这里在 `BanApi.warnPlayer()` 写入后检查阈值并自动封禁。

---

## 七·六、出售系统（A3，格式兼容 Phoenix）

### 与 Phoenix 的关系

Phoenix 的出售**不是自研的** —— 它把第三方插件 **VitaSell** 的引擎类融合进了自己的 jar，
而**引擎源码不在交付物内**（编译期靠一个未上传的占位 jar 顶替）。
所以「移植」这条路走不通，本模块是**按 Phoenix 的配置契约重写**：

| 维度 | Phoenix（VitaSell） | Indra |
|---|---|---|
| 规则/界面文件格式 | `sell/` + `table/` 的 yml 结构 | ✅ **完全一致，可直接拷入** |
| Money 符号语义 | **负数 = 给予玩家** | ✅ **保留**（否则迁移后反向扣钱） |
| 交易审计 | 写文本文件 `data/sell/trades.log` | ⚠️ **改为落库** `indra_sell_log`（项目硬性约束：存储必须双库） |
| 引擎本体 | 闭源第三方 | ✅ 全部自研，源码在 `sell/` 包 |

### 目录与配置

```
plugins/Indra/
├── sell.yml              模块自身行为开关
├── sell/Example.yml      出售规则（可多文件，每文件多条顶层规则）
└── table/Example.yml     出售界面（可多文件，每文件多个顶层界面）
```

首次启动会自动释放 `sell/Example.yml` 与 `table/Example.yml`（**目标已存在则跳过，绝不覆盖**）。

### 规则格式（`sell/*.yml`）

```yaml
Example:
  Item: "name:&f示例材料;lore:contains(&a可回收)"   # 匹配串
  Table: ""          # 限定界面，留空 = 全部
  Condition: [ ]     # Kether 条件（全部为真才可售）
  Action:
    Money:           # "数值 权重"；支持 "最小值~最大值"
      - "-200~-100 25"
      - "-500 20~25"
      - "-1000 75"
    Point:
      - "10 2"
    Kether: [ ]      # 成交后脚本
```

> ⚠️⚠️ **符号语义**：`Money` / `Point` 的**负数表示给予玩家**，正数表示扣除。
> 这是 VitaSell 的语义，保留是为了让既有配置迁移后不会反向扣钱。

**匹配串支持的键**（分号分隔，多条件 AND）：

| 键 | 说明 |
|---|---|
| `name:<文本>` / `name:contains(...)` / `name:!contains(...)` | 显示名 完全等于 / 包含 / 不包含 |
| `lore:...`（同上三种） | Lore 行匹配 |
| `type:<Material>` | 材质，可用逗号写多个 `type:STONE,DIRT` |
| `amount:<n>` / `amount:>=n` … | 数量（默认「至少 n 个」） |
| `custommodeldata:<n>` | 自定义模型数据 |
| `unbreakable:true\|false` | 是否不可破坏 |

> `&a` 与 `§a` 等价，写哪个都能匹配。
> **不支持** `nbt:` / `tag:` / `itemmodel:` 及各类插件物品 —— 需要复杂判断请用 `Condition` 的 Kether 脚本。

### 界面格式（`table/*.yml`）

```yaml
Example:
  Title: "§6出售界面"
  Layout:                 # 每行必须 9 字符，最多 6 行
    - '####S####'
    - '         '
    - '####P###C'
  Auto-Sell: false        # 放入即结算
  Icon:
    '#': { Type: 'GRAY_STAINED_GLASS_PANE', Name: '§8' }
    'S': { Type: 'YELLOW_STAINED_GLASS_PANE', Name: '§e点击出售', Bind: "Sell" }
    'P': { Type: 'GREEN_STAINED_GLASS_PANE', Name: '§a一键放入', Bind: "Put",
           Put-Match: "name:&f示例材料;lore:contains(&a可回收)" }
    'C': { Type: 'GRAY_STAINED_GLASS_PANE', Name: '§c关闭菜单', Bind: "Close" }
```

> **布局的核心规则**：`Layout` 里的字符去 `Icon` 里找同名键 ——
> **有定义** → 该槽位放图标（装饰或按钮）；**没定义** → 该槽位是**玩家可放入物品的空位**。
> 所以 `'         '`（9 个空格）就是「一整行都能放物品」，空格**不需要**在 `Icon` 里声明。

| Bind | 动作 |
|---|---|
| `Sell` | 出售界面内所有可售物品 |
| `Close` | 关闭界面 |
| `Put` | 一键放入背包中可售物品（可用 `Put-Match` 限定范围） |

### 结算流程（防丢物设计）

```
1. 遍历「玩家物品槽」，为每个物品找第一条匹配规则
2. 检查 Condition（Kether）→ 不满足则该物品不可售
3. 按权重池抽取金额，累加
4. 【关键】先尝试 Vault 入账
     ├─ 入账失败 → 物品原样保留，玩家可重试（不会「物品没了钱也没到」）
     └─ 入账成功 → 才清空槽位
5. 执行规则的 Kether 回调 + 落审计
```

> 未匹配任何规则的物品**留在界面里**并统计为「未售出」，不会静默丢弃。
> 关闭界面时，界面里剩余物品**全部还给玩家**；背包满了就掉在脚下，绝不吞掉。

### 数据表

| 表 | 用途 |
|---|---|
| `indra_sell_log` | 成交流水（玩家 / 界面 / 数量 / 金额 / 点券 / 时间） |

与封禁模块共用同一份 `datasource.yml`，SQLite / MySQL 自动切换。

---

## 八、核心功能：禁止原版生物生成

拦截逻辑在 `MobSpawnModule.shouldBlock()`，**四层过滤，任一命中即放行**：

```
1. disable-vanilla-spawn = false         → 全部放行（总开关）
2. 世界 ∈ allow-worlds                   → 放行
3. 生成原因 ∈ allow-reasons              → 放行（CUSTOM / SPAWNER / EGG / COMMAND）
4. 实体类型 ∈ whitelist-entities         → 放行（VILLAGER / WOLF / CAT ...）
   ↓ 都不命中
   取消生成事件
```

### 典型场景

| 需求 | 配置 |
|---|---|
| 全服禁止原版怪 | `disable-vanilla-spawn: true`，其余留空 |
| 只在主城禁止 | `allow-worlds: [world_spawn]` |
| 自定义怪正常生成 | `allow-reasons` 保留 `CUSTOM` |
| 保留村民和宠物 | `whitelist-entities` 加上 `VILLAGER`、`WOLF` |
| 自定义怪不被清理 | `disable-despawn: true`（默认开启） |

> 拦截使用 `EventPriority.LOWEST`，尽早取消以减少后续插件的无效开销。

---

## 九、扩展自己的 RPG 玩法

### 1. 加一个新模块

```kotlin
// src/main/kotlin/com/indra/rpg/yourpack/YourModule.kt
object YourModule : IndraModule {
    override val name = "YourModule"
    override fun onEnable() { /* 注册事件 */ }
    override fun onDisable() { /* 清理 */ }
}
```

然后在 `ModuleManager.builtin` 里加一行即可。

### 2. 加一个配置项

在 `IndraConfig` 里加字段，`config.yml` 里加对应节点，`autoReload = true` 会自动同步：

```kotlin
@ConfigNode("your-section.your-key")
var yourKey: Boolean = true
```

### 3. 存储自定义数据

定义一个 PTC Object 实体，再到 `DataManager.init()` 里注册即可 —— **自动建表**：

```kotlin
import taboolib.expansion.*

@TableName("indra_your_table")
class YourData {
    @Id                                  var id: Int = 0
    @UniqueKey @Length(36) @Alias("uuid")
                                         var uuid: String = ""
    @Alias("your_field")                 var yourField: Int = 0
}

// DataManager.init() 里追加一行：
container = persistentContainer(source) {
    new<IndraPlayerData>()
    new<YourData>()                      // ← 加这一行，重启即自动建表
}
```

> ⚠️ PTC 注解都在 **`taboolib.expansion`** 包（不是 `taboolib.module.database`）；
> 只有 `ColumnTypeSQL` 属于 `taboolib.module.database`。

---

## 十、开发注意事项（TabooLib 约定）

1. **入口类必须是 `object`**，继承 `taboolib.common.platform.Plugin`
2. **不要写 `plugin.yml`** —— 框架自动生成
3. **对外暴露的 API 不要用 Kotlin 接口**（编译期重定向会出问题），需用 `java.util.function.*`
4. **初始化属性时避免调用平台方法**（如 `getDataFolder()`），要用 `by lazy`
5. **`build.gradle.kts` 里没 `install()` 的模块无法使用** —— 这是按需安装机制
6. **服务端 API 坐标需要按你的实际版本替换**（见下方）

### ⚠️ 关于服务端 API 坐标（已核实）

当前 `build.gradle.kts` 中写的是：

```kotlin
compileOnly("io.papermc.paper:paper-api:26.3.build.19-alpha")
```

这对应你提供的 `paper-26.3-19.jar`，**已下载校验通过**（class major = 69 → Java 25）。

仓库：`https://repo.papermc.io/repository/maven-public/`

> 若后续 Paper 发布 26.3 正式版，坐标会变为 `26.3.build.N-stable` 形式，
> 可在 `repo.papermc.io` 查询最新 build 号。

---

## 十一、当前模块清单

### 已实现（骨架，可编译）

| 模块 | 状态 | 说明 |
|---|---|---|
| `MobSpawn` | ✅ | 禁止原版生物生成（核心） |
| `MobLevel` | ✅ | 怪物按距离强化等级 |
| `Player` | ✅ | 等级 / 经验 / 击杀统计 / 死亡惩罚 |
| `Chat` | ✅ | 聊天等级前缀 |
| `Ban` | ✅ | 封禁 / 踢出 / 白名单 / 警告 / 历史 / 自动解封（移植自 Phoenix v1.16.0） |
| `Sell` | ✅ | 出售系统（A3，2026-09-20 新增，配置格式兼容 Phoenix） |
| `Command` | ✅ | `/indra` 指令集（含 9 条封禁 + 5 条出售子命令） |

**已按需求移除**：属性系统、物品系统、战斗系统。

### 公共能力（供后续模块复用）

| 组件 | 说明 |
|---|---|
| `common/db/IndraDb` | SQLite / MySQL 方言切换，Table DSL 与 PTC Object 共用一份 `datasource.yml` |
| `common/economy/VaultBridge` | Vault 反射桥，**零编译期依赖**，未装 Vault 时优雅降级 |
| `common/script/KetherRunner` | Kether 脚本执行 + `{key}` / `<key>` 变量替换 |
| `common/util/TimeUtil` | 时长解析（`7d` → 604800 秒） |

### 待开发（详见 `docs/功能清单.md`）

| 编号 | 功能 | 类别 |
|---|---|---|
| A1 | Vault 经济系统对接 | ✅ **已完成**（`VaultBridge` 底层 + 出售结算业务层） |
| A2 | 封禁系统 | ✅ **已完成**（移植自 Phoenix） |
| A3 | 出售系统 | ✅ **已完成**（2026-09-20，配置格式兼容 Phoenix） |
| A4 | 信标系统（Display + 物品模型，含自定义颜色 / 转动速度） | 新开发 |
| A5 | 统一面板系统 | 新开发 |
| A6 | 自定义 TAB 栏（着色器驱动） | 新开发 |
| A7 | 封禁与出售的面板化操作 | 新开发 |
| **A8** | **后处理效果控制（`/posteffect`）** | 新开发 · **方案已确认**<br>Paper 无 API，走 PacketEvents 封包 |
| B1 | WorldSafe 保护（35 项） | 集成 |
| B2 | MessageBridge 消息路由 | 集成 |
| B3 | 着色器开发 | 集成 · ✅ 已实测<br>**Paper 26.3 无服务端后处理 API**，详见 `docs/功能清单.md` B3.1 |
| B4 | ImagePreviewer 图片预览 | 集成 |
| C1 | KcPiano 钢琴 | 黑盒 |
| C2 | SweetData 数据 | 待明确 |
| C3 | LoadingScreenSkipper 移除加载界面 | 黑盒 |

---

## 十二、PacketEvents 统一策略

三个插件各自内置 PacketEvents 会造成多重初始化冲突。Indra 采用**软依赖 + 优雅降级**：

```kotlin
// plugin.yml
softdepend: [packetevents]

// build.gradle.kts —— 只编译期引用，不打包进 jar
compileOnly("com.github.retrooper:packetevents-spigot:2.7.0")
```

| 功能 | PacketEvents 存在 | 缺失时降级 |
|---|---|---|
| A6 TAB 栏 | 封包精确控制 | 退回 Scoreboard Team |
| B2 消息路由 | 标记转换可用 | 关闭转换，保留原生消息 |
| B4 图片预览 | 可用 | 不可用（该插件本身强依赖） |
| C3 LSS | 可用 | 不可用（该插件本身强依赖） |
| **其余全部** | 正常 | **正常**（A1–A5、A7、B1、C1 都不依赖封包） |

---

## 十三、下一步

环境改造（**JDK 25** + Paper 26.3 坐标 + TabooLib 6.3）**已完成**；
v1.4.0 修复了「静默失活」这一阻塞性问题（见第二节）；
v1.5.0 完成 **A3 出售系统**（第一阶段主体工作量）；
v1.6.0 完成**深度静态核查 + 运行时自检 + 依赖修正**（见下）。

### v1.6.0 做了什么（2026-09-20）

**背景**：实机测试反馈「配置文件、命令依旧均未正常使用」。
本轮把**整条链路逐层反编译核实**，结论与改动如下。

#### 一、推翻一个错误假设：命令**不需要** `plugin.yml` 的 `commands:` 段

上一轮曾怀疑「`plugin.yml` 缺 `commands:` 导致 `/indra` 不存在」。**这是错的**，
已用三份证据证伪：

1. **官方 TabooLib Gradle 插件根本不生成 `commands:` 段。**
   对 `taboolib-gradle-plugin-2.0.37.jar` 全量 `grep -rl "commands" --include="*.class"`
   返回**空**；`BuilderBukkit` 只写 `name/main/version/homepage/authors/depend/softdepend/loadbefore/folia-supported/description`。
2. **TabooLib 的命令是运行期注册的。**
   `taboolib/platform/BukkitCommand.class` 通过 `Bukkit.getCommandMap()` 拿 `knownCommands`，
   用 `Reflex` 反射造 `PluginCommand` 再注入 —— 与 `plugin.yml` 完全无关。
   宿主是 `platform-bukkit-impl` 模块（`env.properties` 的 `module=` 已含）。
3. **我们的源码用法与真实签名一致。**
   用 Gradle 缓存里的**真实 TabooLib 6.3 jar**（非桩）全量编译 → **0 error**。

#### 二、发现并修掉的真问题

| # | 问题 | 影响 | 处理 |
|---|---|---|---|
| 1 | `tools/verify.sh` 漏下 `adventure-text-serializer-legacy` | 离线校验会对 `LegacyComponentSerializer.legacySection()` 报**假错误** | 补下载 + 兜底从 Gradle 缓存取 |
| 2 | `verify.sh` 只用**手写桩**验签，与真包会漂移 | 「桩过真包不过」的假阴性（本轮就抓到一次） | **默认改用 Gradle 缓存的真实 TabooLib jar 编译**，找不到才退回桩并告警 |
| 3 | `BanDatabaseSql` 2 处 `order(row, desc)` 已弃用 | 将来成为错误 | 改 `orderBy(col, Order.Type.ASC/DESC)` |
| 4 | `BanPlayerListener` 3 处 `disallow(Result, String)` 已弃用 | 同上 | 改传 `Component`（legacy 序列化器转换） |
| 5 | `BanApi` 2 处 `Player.kickPlayer(String)` 已弃用 | 同上 | 改 `kick(Component)` |

现在 `tools/verify.sh` 对着**真实 Paper 26.3 + TabooLib 6.3**：**77 class，0 error，0 warning**。

#### 三、新增运行时自检（关键排查手段）

`Indra.onEnable()` 开头现在会打印：

```
[Indra] 启动自检：
  - taboolib.group = com.indra
  - 数据目录       = .../plugins/Indra
  - 插件版本       = 1.6.0
  - config.yml     = 已生成 (注解管线正常)        ← 或 warning
```

**这段日志是分水岭**：

* **看不到「启动自检」** → `Indra.onEnable()` 没被调用，即 `pluginInstance == null`
  （类扫描集合为空）→ 回到 `IndraBootstrap` 的 groupId 根因，检查
  `taboolib.group` 是否真的注入了。
* **看到了「启动自检」，但 `config.yml` 那行是 warning** → 注解管线没跑，
  同样是类扫描问题。
* **看到了且全部正常** → 插件本身没问题，问题在更下游（模块 / 第三方依赖）。

**⚠️ 前置动作：实机验证 1.6.0。**
重新部署 `dist/Indra-1.6.0.jar` 后，日志必须出现
`[Indra] 正在加载 RPG 基础模块...`、`[Indra] 启动自检：` 与 `[Indra] 启用完成，共加载 N 个模块。`，
且 `config.yml` / `ban.yml` / `sell.yml` 自动生成、`/indra` 与 `/indra sell` 可用。
完整验证清单见 `docs/功能清单.md` 的「实机验证清单」。

验证通过后，按 `docs/功能清单.md` 的优先级推进：

1. ~~A1 Vault + A2 封禁 + A3 出售~~ ✅ **已完成**
2. **A5 统一面板 + A7 面板化** —— 面板是其他功能的展示载体
3. **A4 信标**（Display 实体，技术已验证可行）
4. **B1 / B2 / B4 移植 + A8 后处理效果** —— 有源码或清晰 API
5. **B3 着色器 + A6 TAB 栏** —— 依赖客户端渲染与资源包，需先验证 26.3 着色器管线

> 自检工具：`bash tools/sim/run.sh` —— 一键核对产物是否满足引导类不变式
> （`plugin.yml` main 正确、静态块注入存在、`kotlin/* = 0`、父类入包）。

---

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。

### 衍生与致谢

- **封禁系统**移植自 Phoenix（v1.16.0 PhoenixBan 模块），**出售系统**的规则/界面格式与
  Phoenix VitaSell 保持 100% 兼容——在此向 Phoenix 原作者致谢；
- 本项目基于 [TabooLib 6.3](https://github.com/TabooLib/taboolib) 框架构建，
  感谢框架维护者与社区。

### 实机验证记录（2026-09-21）

```
[18:26:04] [Indra] §b[Indra] §f正在加载 RPG 基础模块...
[18:26:09] [Indra] §b[Indra] §f启动自检：
[18:26:09]   taboolib.group = <未设置>        ← 无需启动参数，组名已焊入 jar
[18:26:09]   config.yml     = 已生成 (注解管线正常)
[18:26:09] [Indra] §a启用完成，共加载 §e8 §a个模块。
[18:26:14] Done (62.368s)!
```
