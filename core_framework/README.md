# 本项目的基础框架 Library

本模块复制自 `AndroidFrameworkTemplate/core_framework`，上游版本为 `1d656176d900a199bd16c6be9e5b70bde43690a1`。保留原包名、运行时代码、资源、单元测试和 [MIT 许可证](LICENSE)；不依赖本机的参考项目路径。

Gradle 配置适配宿主版本目录、AGP 9 内置 Kotlin、compileSdk 37 和 Java 17。`MutableDataAdapter` 增加空条目的差分处理及回归用例，兼容宿主 Kotlin 对 DiffUtil 非空参数的约束，保留可空列表契约；`Framework` 的私有延迟仓库属性更名，消除其 getter 与公开 `getRepository()` 的 JVM 签名冲突。其他运行时代码和资源沿用上游。未复制示例 App、Gradle Wrapper、本机构建配置或产物。后续同步上游时，请保留这些适配。

天气 App 在 `WeatherApplication` 中调用 `Framework.init` 注入日志与版本信息，随后由 `WeatherApiClient` 使用 `NetworkModule.createOkHttpClient` 和 `createApiService`。三个 Open-Meteo 域名共用同一个客户端；连接 / 读取 / 写入超时分别为 10 / 15 / 10 秒。Debug 使用框架摘要日志，Release 不安装 HTTP 日志拦截器。没有配置认证、client ID 或版本 Header。

当前只使用基础初始化与 HTTP 工厂；框架偏好、仓库和 WebSocket 不随初始化启动。天气接口与 DTO、业务异常、原 `weather_data` 缓存仍由 App 管理，不套用框架的 `ApiResponse` 示例结构。网络接入说明见 [设计文档](../docs/DESIGN.md#21-网络json-与-kotlin)。

本地验证命令（不会连接 Android 设备）：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :core_framework:testDebugUnitTest \
  :app:lintDebug :core_framework:lintDebug
```

`WeatherApiClientTest` 使用本机 MockWebServer 验证真实 HTTP 编解码、三个服务地址、错误映射和协程取消，不调用外部天气 API 或测试机。

复制的列表适配器测试使用 Robolectric 在本机 JVM 提供 Android Observable，避免 SDK 单元测试桩的空初始化导致误报；该依赖仅用于 Library 的测试，不进入 App 安装包，也不启动设备或模拟器。
