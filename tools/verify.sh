#!/usr/bin/env bash
# ============================================================================
#  Indra 离线编译校验脚本
#  ---------------------------------------------------------------------------
#  用途：不依赖 Gradle / 不依赖 GitHub 直连，仅用 Maven 镜像把
#        「Kotlin 2.3.20 编译器 + Paper 26.3 API + Adventure 5.2.0」
#        拉到本地，然后对 src/main/kotlin 做一次真编译。
#
#  为什么需要它：
#    - GitHub 在部分网络环境不可达，Gradle 首次构建需要拉 TabooLib 插件
#    - 这个脚本只验证「源码语法 + API 签名」是否正确，最快最稳
#
#  用法：bash tools/verify.sh
#  预期：输出 "BUILD OK: NN class, 0 error"
# ============================================================================
set -u

MIRROR="${MIRROR:-https://maven.aliyun.com/repository/public}"
WORK="${WORK:-/tmp/indra-verify}"
KOTLIN_VER="2.3.20"
PAPER_VER="26.3.build.19-alpha"
ADV_VER="5.2.0"

PROJ="$(cd "$(dirname "$0")/.." && pwd)"

say() { printf '\033[36m[verify]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }

# ── 0. JDK 检查 ─────────────────────────────────────────────────────────────
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[ -z "$JAVA_MAJOR" ] && die "未找到 java，请安装 JDK 25"
say "JDK major = $JAVA_MAJOR"
# 运行编译器本身用 JDK 21+ 即可；产出 25 字节码由 -jvm-target 决定
[ "$JAVA_MAJOR" -lt 17 ] && die "JDK 过低（需 >= 17 运行编译器，推荐 25）"

# ── 1. 准备目录 ─────────────────────────────────────────────────────────────
mkdir -p "$WORK/kc" "$WORK/deps" "$WORK/out" "$WORK/src"

# ── 2. 下载 Kotlin 编译器 ───────────────────────────────────────────────────
KC="$WORK/kc"
if [ ! -f "$KC/kc.jar" ]; then
  say "下载 Kotlin $KOTLIN_VER 编译器..."
  BASE="$MIRROR/org/jetbrains/kotlin"
  curl -fsSL -o "$KC/kc.jar" "$BASE/kotlin-compiler-embeddable/$KOTLIN_VER/kotlin-compiler-embeddable-$KOTLIN_VER.jar" || die "编译器下载失败"
  for a in stdlib reflect script-runtime daemon-embeddable; do
    curl -fsSL -o "$KC/kotlin-$a-$KOTLIN_VER.jar" "$BASE/kotlin-$a/$KOTLIN_VER/kotlin-$a-$KOTLIN_VER.jar" || die "$a 下载失败"
  done
  curl -fsSL -o "$KC/trove4j.jar" "$MIRROR/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar" || die "trove4j 下载失败"
  curl -fsSL -o "$KC/annotations.jar" "$MIRROR/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar" || die "annotations 下载失败"
  curl -fsSL -o "$KC/coroutines.jar" "$MIRROR/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar" || die "coroutines 下载失败"
else
  say "Kotlin 编译器已存在，跳过下载"
fi

# ── 3. 下载 Paper 26.3 API ─────────────────────────────────────────────────
D="$WORK/deps"
if [ ! -f "$D/paper-api.jar" ]; then
  say "下载 Paper $PAPER_VER API..."
  curl -fsSL -o "$D/paper-api.jar" "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$PAPER_VER/paper-api-$PAPER_VER.jar" \
    || die "Paper API 下载失败（检查网络或代理）"
  [ "$(stat -c%s "$D/paper-api.jar")" -lt 100000 ] && die "Paper API 文件异常，可能被网关拦截"
fi

# ── 4. 下载 Paper 26.3 的编译期传递依赖 ─────────────────────────────────────
#     ⚠️ Adventure 必须是 5.2.0：Paper 26.3 的 Player 继承 text.object.SkinSource
#     dl <groupId路径> <artifactId> <version>
#       → $MIRROR/<group>/<artifact>/<ver>/<artifact>-<ver>.jar
dl() {
  local f="$D/$2-$3.jar"
  [ -s "$f" ] && return 0
  curl -fsSL -o "$f" "$MIRROR/$1/$2/$3/$2-$3.jar" 2>/dev/null || true
  if [ ! -s "$f" ]; then rm -f "$f"; echo "  缺少 $2-$3" >&2; return 1; fi
}
say "下载 Paper 26.3 传递依赖（Adventure $ADV_VER 等）..."
dl net/kyori          adventure-api                   "$ADV_VER"
dl net/kyori          adventure-key                   "$ADV_VER"
dl net/kyori          adventure-text-minimessage      "$ADV_VER"
dl net/kyori          adventure-text-serializer-gson  "$ADV_VER"
dl net/kyori          adventure-text-serializer-plain "$ADV_VER"
# ⚠️ legacy 序列化器必须单独下载（不是 paper-api 的直接依赖，Gradle 靠传递解析拿到，
#    但本脚本是手工列坐标，漏掉会让所有 `LegacyComponentSerializer.legacySection()`
#    报 "unresolved reference 'legacy'" 的假错误）。
dl net/kyori          adventure-text-serializer-legacy "$ADV_VER"
dl net/kyori          examination-api                 1.3.0
dl com/google/code/gson gson                      2.14.0
dl org/joml           joml                            1.10.9
dl com/google/guava   guava                           33.6.0-jre
dl org/jspecify       jspecify                        1.0.0
dl org/checkerframework checker-qual                  4.2.3
dl org/yaml           snakeyaml                       2.2
# brigadier 不在阿里云，走 Mojang 官方库
if [ ! -s "$D/brigadier-1.3.11.jar" ]; then
  curl -fsSL -o "$D/brigadier-1.3.11.jar" "https://libraries.minecraft.net/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar" 2>/dev/null || true
  [ -s "$D/brigadier-1.3.11.jar" ] || rm -f "$D/brigadier-1.3.11.jar"
fi

# 关键依赖缺失直接失败，避免出现"看似编译通过实则缺包"的假象
[ -s "$D/paper-api.jar" ]                 || die "paper-api 未下载成功"
[ -s "$D/adventure-api-$ADV_VER.jar" ]    || die "adventure-api 未下载成功（Paper 26.3 编译必需）"
[ -s "$D/adventure-key-$ADV_VER.jar" ]    || die "adventure-key 未下载成功"

# ── 5. 收集源码 ─────────────────────────────────────────────────────────────
find "$PROJ/src/main/kotlin" -name '*.kt' > "$WORK/src/indra.txt"
N_SRC="$(wc -l < "$WORK/src/indra.txt")"
[ "$N_SRC" -eq 0 ] && die "src/main/kotlin 下没有 Kotlin 源码"
say "找到 $N_SRC 个源文件"

# ── 5.1 选择 TabooLib 来源：真实 jar（首选） / 离线桩（兜底）───────────────
#
# ★ 2026-09-20 新增「真实 jar 模式」。缘由：
#   桩（tools/stubs）是手写的近似物，一旦与真实 TabooLib 的签名漂移，
#   就会出现「桩编译通过、真包编译失败」的假阴性 —— 而且桩越"宽容"越危险。
#   例：CommandContext 的 int/double 扩展真实位于 ExtraContextKt，
#   CommandContext 本身没有这些方法；若桩把方法写在类里，源码写错也照样过。
#
#   因此现在优先用 **Gradle 缓存里的真实 TabooLib 6.3 jar** 编译。
#   找不到真实 jar（首台干净机器）时才退回桩，并在末尾打印醒目提示。
GRADLE_TB_DIR="${GRADLE_TB_DIR:-$HOME/.gradle/caches/modules-2/files-2.1/io.izzel.taboolib}"
TB_JARS="$(find "$GRADLE_TB_DIR" -name '*-6.3.0-*.jar' 2>/dev/null \
           | grep -vE 'gradle-plugin|gradle/caches/9' | sort -u | tr '\n' ':')"

SRC_LIST="$WORK/src/all.txt"
: > "$SRC_LIST"
MODE="stub"
if [ -n "$TB_JARS" ]; then
  MODE="real"
  say "✔ 使用真实 TabooLib jar 编译（$(echo "$TB_JARS" | tr ':' '\n' | grep -c . ) 个模块）"
  cat "$WORK/src/indra.txt" >> "$SRC_LIST"
else
  STUB_DIR="$WORK/stubs"
  if [ -d "$PROJ/tools/stubs" ]; then
    rm -rf "$STUB_DIR"
    mkdir -p "$STUB_DIR"
    cp -r "$PROJ/tools/stubs/." "$STUB_DIR/"
    say "⚠️ 未找到真实 TabooLib jar，退回到 tools/stubs 离线桩（已刷新）"
    find "$STUB_DIR" -name '*.kt' >> "$SRC_LIST"
  fi
  cat "$WORK/src/indra.txt" >> "$SRC_LIST"
fi

# ── 6. 组 classpath ─────────────────────────────────────────────────────────
#
# ⚠️ legacy 序列化器是 `LegacyComponentSerializer` 的宿主，某些机器上
#    不在 $D（本脚本只拉直接依赖），此时从 Gradle 缓存补一份。
KCP="$(ls "$KC"/*.jar | tr '\n' ':')"
LEGACY_EXTRA=""
if [ ! -s "$D/adventure-text-serializer-legacy-$ADV_VER.jar" ]; then
  _leg="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.kyori/adventure-text-serializer-legacy" \
          -name "*-$ADV_VER.jar" 2>/dev/null | head -1)"
  [ -n "$_leg" ] && LEGACY_EXTRA="$_leg:" && say "从 Gradle 缓存补入 adventure-text-serializer-legacy"
fi
CP="$D/paper-api.jar:${LEGACY_EXTRA}$(ls "$D"/*.jar | grep -v paper-api | tr '\n' ':')$TB_JARS$KC/kotlin-stdlib-$KOTLIN_VER.jar"

# ── 7. 编译 ─────────────────────────────────────────────────────────────────
say "开始编译（jvm-target 25，TabooLib 来源：$MODE）..."
rm -rf "$WORK/out"; mkdir -p "$WORK/out"
LOG="$WORK/compile.log"
java -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -jvm-target 25 -no-stdlib -no-reflect \
  -classpath "$CP" \
  -d "$WORK/out" @"$SRC_LIST" > "$LOG" 2>&1
RC=$?

# 只保留以文件路径开头的真正 error/warning 行
# （过滤 stub 场景下的无害告警、续行、堆栈）
grep -E '^[^ \t].*\.kt:[0-9]+' "$LOG" | grep -vE 'inaccessible|annotations\.Nullable' | head -40

# ── 8. 结果 ─────────────────────────────────────────────────────────────────
if [ "$RC" -eq 0 ]; then
  CNT="$(find "$WORK/out" -name '*.class' | wc -l)"
  MAJ="$(javap -v -cp "$WORK/out" com.indra.rpg.Indra 2>/dev/null | sed -n 's/.*major version: //p' | head -1)"
  printf '\033[32mBUILD OK: %s class, 0 error (major=%s, TabooLib=%s)\033[0m\n' "$CNT" "${MAJ:-?}" "$MODE"
  [ "${MAJ:-0}" = "69" ] && echo "✔ 字节码版本正确（Java 25）"
  if [ "$MODE" = "stub" ]; then
    echo
    echo "⚠️  本次用的是离线桩，**未能**校验真实 TabooLib 签名。"
    echo "   要跑真实校验：先 ./gradlew build 一次（把 TabooLib 拉进 Gradle 缓存），再重跑本脚本。"
  else
    echo "✔ 已对照真实 TabooLib 6.3 校验，签名一致"
  fi
else
  die "编译失败，完整日志：$LOG"
fi
