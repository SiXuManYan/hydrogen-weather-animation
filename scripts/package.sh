#!/bin/bash
# 构建 APK；Debug 使用默认签名，Release 使用项目签名，兼容 Bash 3.2。
set -euo pipefail

PROJECT_NAME='hydrogen-weather-animation'
KEYSTORE_NAME='weather.jks'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# 帮助在任何本机环境检查之前返回，便于新机器先查看配置方式。
usage() {
    cat <<USAGE
项目：$PROJECT_NAME
用法：$0 [debug|release|all] [Gradle 参数...]
默认构建 release；all 同时构建 debug 和 release。
Debug 使用 Android 默认 debug.jks，无需项目签名凭据。
Release 使用 app/jks/$KEYSTORE_NAME；两个变体的 APK 均导出到 app/release/。
Release 签名凭据：app/keystore.properties（参照 .example），或同名 Gradle 属性。
release/all 无凭据文件时交互输入，非交互运行请设置：
  ORG_GRADLE_PROJECT_storePassword
  ORG_GRADLE_PROJECT_keyAlias
  ORG_GRADLE_PROJECT_keyPassword
示例：$0 all --offline
USAGE
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
esac

variant="release"
case "${1:-}" in
    debug|release|all) variant="$1"; shift ;;
    ""|-*) ;;
    *) printf '不支持的变体：%s\n' "$1" >&2; usage >&2; exit 2 ;;
esac

if [[ ! -f "$PROJECT_ROOT/gradlew" || ! -f "$PROJECT_ROOT/app/build.gradle.kts" ]]; then
    printf '项目目录不完整：%s\n' "$PROJECT_ROOT" >&2
    exit 1
fi
# 只有包含 Release 的打包才需要项目密钥。
if [[ "$variant" != "debug" && ! -f "$PROJECT_ROOT/app/jks/$KEYSTORE_NAME" ]]; then
    printf '缺少项目签名文件：%s/app/jks/%s\n' "$PROJECT_ROOT" "$KEYSTORE_NAME" >&2
    exit 1
fi

# 优先使用调用方的 JDK；未指定时尝试 Android Studio 自带 JBR。
if [[ -z "${JAVA_HOME:-}" && -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java' ]]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
        printf 'JAVA_HOME 中没有可执行的 java，请配置 Android Studio JBR 或 JDK 17+。\n' >&2
        exit 1
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
elif ! command -v java >/dev/null 2>&1; then
    printf '未找到 Java，请配置 JAVA_HOME（推荐 Android Studio JBR）。\n' >&2
    exit 1
fi

# Debug 跳过项目凭据；Release 已有文件由 Gradle 读取，交互口令仅传给本次进程。
if [[ "$variant" != "debug" && ! -f "$PROJECT_ROOT/app/keystore.properties" ]]; then
    if [[ -z "${ORG_GRADLE_PROJECT_storePassword:-}" || -z "${ORG_GRADLE_PROJECT_keyAlias:-}" || -z "${ORG_GRADLE_PROJECT_keyPassword:-}" ]]; then
        if [[ ! -t 0 ]]; then
            printf '缺少签名凭据。请填写 app/keystore.properties，或设置帮助中列出的三个环境变量。\n' >&2
            exit 1
        fi
        if [[ -z "${ORG_GRADLE_PROJECT_keyAlias:-}" ]]; then
            read -r -p '签名 keyAlias：' ORG_GRADLE_PROJECT_keyAlias
        fi
        if [[ -z "${ORG_GRADLE_PROJECT_storePassword:-}" ]]; then
            read -r -s -p '签名 storePassword：' ORG_GRADLE_PROJECT_storePassword
            printf '\n'
        fi
        if [[ -z "${ORG_GRADLE_PROJECT_keyPassword:-}" ]]; then
            read -r -s -p '签名 keyPassword（回车复用 storePassword）：' ORG_GRADLE_PROJECT_keyPassword
            printf '\n'
            ORG_GRADLE_PROJECT_keyPassword="${ORG_GRADLE_PROJECT_keyPassword:-$ORG_GRADLE_PROJECT_storePassword}"
        fi
    fi
    if [[ -z "${ORG_GRADLE_PROJECT_storePassword:-}" || -z "${ORG_GRADLE_PROJECT_keyAlias:-}" || -z "${ORG_GRADLE_PROJECT_keyPassword:-}" ]]; then
        printf '签名口令和 keyAlias 不能为空。\n' >&2
        exit 1
    fi
    export ORG_GRADLE_PROJECT_storePassword ORG_GRADLE_PROJECT_keyAlias ORG_GRADLE_PROJECT_keyPassword
fi

tasks=()
case "$variant" in
    debug) tasks=(":app:exportDebugApk") ;;
    release) tasks=(":app:exportReleaseApk") ;;
    all) tasks=(":app:exportDebugApk" ":app:exportReleaseApk") ;;
esac
cd "$PROJECT_ROOT"
printf '正在打包 %s（%s）…\n' "$PROJECT_NAME" "$variant"
# sh 调用兼容未设置可执行位的 Gradle Wrapper；Gradle 失败时立即保留原退出码。
sh ./gradlew "${tasks[@]}" "$@"
printf '打包完成，APK 目录：%s/app/release/\n' "$PROJECT_ROOT"
