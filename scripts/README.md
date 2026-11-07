# 项目签名与 APK 打包

[项目入口](../README.md) · [开发约定](../AGENTS.md) · [签名模板](../app/keystore.properties.example)

## 首次配置

Debug 不指定 `signingConfig`，使用 Android 默认 `debug.jks`，无需项目密钥或正式签名凭据。Release 固定使用 `app/jks/weather.jks`，不接受外部 `storeFile` 替换。保留现有 Release 优化策略；Debug 显式可调试，日志由 `BuildConfig.DEBUG` 开启，Release 不可调试。

1. 准备 Android SDK 37 及兼容项目 AGP 的 JDK（推荐 Android Studio 自带 JBR）。本机 SDK 路径仍写在未跟踪的 `local.properties`。
2. 仅打包 Release 时，将项目签名文件放在上述固定位置；不要创建新密钥替换已有发布身份。
3. 仅打包 Release 时，复制 `app/keystore.properties.example` 为 `app/keystore.properties`，填写 `storePassword`、`keyAlias`、`keyPassword`。这三个值逐项优先读取同名 Gradle 属性，其次读取本机文件。

口令文件被 Git 忽略。Release CI 推荐注入 `ORG_GRADLE_PROJECT_storePassword`、`ORG_GRADLE_PROJECT_keyAlias`、`ORG_GRADLE_PROJECT_keyPassword`，避免把口令写进命令行或日志。IDE Sync、纯编译及 Debug 打包不要求项目签名凭据；仅 Release APK/AAB 打包阶段检查项目密钥文件及必填项，缺失时失败，不输出未签名 Release 包。实际密码、别名与私钥可用性由 Android 签名任务继续验证。

## 脚本与产物

在仓库根目录运行；脚本也支持从其他当前目录调用：

```bash
bash scripts/package.sh             # 默认 Release
bash scripts/package.sh debug       # Debug
bash scripts/package.sh release     # Release
bash scripts/package.sh all         # 两个变体
bash scripts/package.sh all --offline
bash scripts/package.sh --help
```

`debug` 跳过项目密钥检查和凭据输入。`release` / `all` 没有本机凭据文件时，脚本在终端交互读取尚未提供的凭据，密码输入不回显、不落盘；keyPassword 回车可复用 storePassword。非交互运行需提供完整环境变量；已有文件为空或不完整时，由 Gradle 明确报告缺项。脚本优先使用已有 JAVA_HOME，未设置时尝试 macOS Android Studio 的 JBR；不会安装、卸载、清空项目或操作设备。

脚本调用 `:app:exportDebugApk` / `:app:exportReleaseApk`，从 AGP 公开 APK Artifact 复制到 `app/release/`。文件名为：

```text
HydrogenWeather_<versionName>_<yyyy-MM-dd>_<debug|release>.apk
```

日期取本机构建日期，版本名保持原值；同日同版本同变体覆盖同名导出文件，旧日期的文件保留。`app/release/` 已被忽略。Gradle 构建目录中的 APK 也使用相同文件名。外部本机快捷脚本只定位项目并调用本仓库脚本，实际流程随 Git 维护。

## 数据模型与验证边界

网络 DTO、可传输业务模型、反射缓存与其嵌套类型使用 `androidx.annotation.Keep`；相关模块直接依赖 AndroidX Annotation，App R8 规则保留这些类型及成员。UI 状态、View/Bitmap 容器等仍按实际用途判断，不以全包保留代替模型标记。这项保护不新增网络请求、数据上传或业务功能。

本次工程调整仅做静态源码检查、Git 忽略与差异检查、Bash 语法和隔离的打包流程替身检查；未运行 Gradle、Android 构建、单测、Lint、安装或设备操作，也未验证实际签名证书。首次真实 Release 打包前需填写本机凭据；Debug 可直接在 Android Studio 运行或通过脚本打包。真实打包后仍需核对 APK 的签名身份和两个变体的可调试属性。

## English

Debug leaves `signingConfig` unset and uses Android's default `debug.jks`, without a project keystore or release credentials. Release uses `app/jks/weather.jks`. For Release, copy `app/keystore.properties.example` to `app/keystore.properties` and fill `storePassword`, `keyAlias`, and `keyPassword`; same-named Gradle properties take precedence. Secrets stay local or in CI environment variables. Only Release APK/AAB packaging validates the project keystore and required credentials; missing configuration stops Release packaging rather than producing unsigned output. Debug packaging, IDE Sync and compilation do not require project signing credentials. Debug is debuggable with existing `BuildConfig.DEBUG` logging; Release is not debuggable and keeps its existing optimization policy.

Run `bash scripts/package.sh [debug|release|all] [Gradle options]`; the default is release. The debug option skips project-keystore checks and credential prompts. For release/all without a credentials file, interactive runs prompt without echoing or saving passwords; unattended runs require the three `ORG_GRADLE_PROJECT_*` variables listed above. The script honors JAVA_HOME, otherwise tries Android Studio's macOS JBR, and never installs or launches the app.

Both export tasks copy the public AGP APK artifact to `app/release/HydrogenWeather_<versionName>_<yyyy-MM-dd>_<buildType>.apk`, preserving the original version name and using the local build date. Same-day matching exports overwrite that filename; older dates remain. Credentials and exported APKs are ignored by Git.

Network/transfer models, reflective cache records and nested types use AndroidX `@Keep`, with an explicit annotation dependency and App R8 rules. This change was checked statically and with isolated script substitutes only: no Gradle build, Android tests, Lint, device workflow or real signing verification has been run. Supply local signing credentials before the first real Release package build; Debug can be run directly from Android Studio or the script.
