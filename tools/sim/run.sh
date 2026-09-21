#!/usr/bin/env bash
# =============================================================================
# Indra 引导类根因 —— 修复前后行为对照仿真
#
# 用途：在不启动 Minecraft 服务端的前提下，证明「禁用 ASM 后 ProjectInfoKt
#      groupId 未被改写」确实会导致 ClassVisitorHandler 阶段一过滤结果为空集，
#      以及 Java 静态块注入 taboolib.group 之后如何恢复。
#
# 依赖：仅需 JDK 25（与构建机一致），不需要 Gradle、不需要 Paper API。
#
# 用法：
#   bash tools/sim/run.sh
#   bash tools/sim/run.sh dist/Indra-1.5.0.jar     # 指定要核验的产物
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT="$(pwd)"

# 默认核验最新产物：取 dist 下版本号最大的那个 jar
if [ $# -ge 1 ]; then
    JAR="$1"
else
    JAR="$(ls -1 dist/Indra-*.jar 2>/dev/null | sort -V | tail -1 || true)"
    if [ -z "$JAR" ]; then
        echo "dist/ 下没有找到 Indra-*.jar，请先执行 ./gradlew build" >&2
        exit 1
    fi
fi

echo "工作目录: $ROOT"
echo "核验产物: $JAR"
echo

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if ! javac -d "$TMP" tools/sim/Sim.java 2>&1; then
    echo "编译 Sim.java 失败 —— 请确认 JDK 已安装（建议 25）" >&2
    exit 1
fi

java -cp "$TMP" Sim "$ROOT/$JAR"
