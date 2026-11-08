# 氢天气 — 项目约定

本文件维护长期开发、审查与交付规则，不另建 `agent.md` 或大小写副本。功能与运行入口见 `README.zh-CN.md` / `README.md`，实现契约见 `docs/DESIGN.md`，审查与验证记录见 `docs/REVIEW.md`。

## 项目边界

- Kotlin + 原生 XML / Android View / ViewBinding + Canvas，沿用现有 ViewModel、Repository 与 AppContainer；不引入 Compose。工具链以构建文件与版本目录为准。
- 当前工具链：AGP 9.5.1（内置 Kotlin）/ Gradle Wrapper 9.6.1；minSdk 29，compileSdk / targetSdk 37，Java 17。主要依赖：Annotation 1.11.0、Core 1.19.1、AppCompat 1.8.0、Activity 1.13.0、Fragment 1.9.1、Lifecycle 2.11.0、Coroutines 1.11.0、Retrofit 3.0.0、Gson 2.15.0、OkHttp 5.5.1、DataStore 1.3.0、WorkManager 2.12.0、Browser 1.10.0、Material 1.14.0；Robolectric 4.17 仅用于本地测试。版本以 `gradle/libs.versions.toml` 为准，升级时同步本文和当前实现文档，默认采用稳定版；历史验证保留原日期。AGP 9.5 要求 Gradle ≥9.6.0、JDK ≥17。
- `app` 单向依赖 `core_framework`，框架不引用 App 资源、BuildConfig 或业务类型。三个 Open-Meteo 服务通过框架 HTTP 工厂共享客户端，页面不得自行创建客户端。
- Open-Meteo 的接口、模型选择、当地日期聚合、缺测和回退规则以 `docs/DESIGN.md` 为准；无有效天气的响应不覆盖成功缓存，损坏缓存按条目跳过。
- 城市身份、坐标和请求代次需匹配，删除或移动后的旧结果不得写回；同城同坐标并发刷新复用任务。Gson 缓存模型和枚举保持历史 JSON 兼容。
- 定位只在前台经系统 LocationManager 按已有授权运行，支持大致位置；不引入 GMS 或后台定位权限。拒绝定位后保留手动选城。
- 桌面微件沿用 RemoteViews + WorkManager 和首城缓存，离线重绘与联网请求分开；不承诺后台准点执行，不增加常驻服务或精确闹钟。
- 场景动画避免每帧分配资源；城市卡使用共享有界帧时钟，只绘制有效可见区域，离开页面释放监听与动画。
- 项目用于非商业学习与演示，保留非官方声明、天气数据署名及来源许可。正文打包白名单沿用当前 Sync 任务，设计文档与录屏不进入 APK。

## 验证

普通代码、资源和文档修改不自动运行 Gradle、安装或设备任务；用户明确要求时按范围运行构建、JVM 测试和 Lint，Lint 要求 0 errors。纯逻辑变更同步对应单元测试源码。脚本、XML 与文档可做静态检查；微件真实显示、传感器、定位及长期后台行为需要独立设备验收，不能以历史通过记录替代。

## 通用开发与交付约定

本节提炼自拼图工坊的通用规则；项目专用架构、数据和隐私契约继续适用，不为复用规范引入其他项目的类名、控件或依赖。

### 工作范围与交付

- 当前用户要求优先；已确认的授权持续有效。修改前读取当前差异、目标代码和相关专题，保留无关未提交内容，完成最小且连贯的改动。
- 在 `main` 上工作，不创建或切换分支，除非用户明确要求。每项连贯功能或修复完成后默认只提交相关文件并 push 到对应远程分支；用户要求暂不提交或推送时优先。远程未配置或推送失败时保留本地提交，报告具体阻塞，不虚报成功，不强制推送。
- 文档随功能维护，优先更新既有相关 Markdown；用户可感知变化才更新用户版本日志。历史日期、来源、许可和各次实际检查范围保留，不能以跨日期汇总覆盖原记录。纯工程维护写开发文档。
- 设计参考、源码实现、编译结果与设备实测分别说明；报告提交号、推送目标、实际检查和仍待验收内容。历史通过结果不覆盖本次差异。

### 分层、状态与生命周期

- 沿用 Kotlin / Android View / XML / ViewBinding 与现有 MVVM，不引入 Compose，不为共享规范迁移存储或扩充功能。
- Fragment / Activity 负责 View 生命周期、权限与 Activity Result、导航、弹窗和渲染；持久化、任务协调及业务状态交给 ViewModel / Repository / 纯规则。已有基类、导航和收集工具优先复用，没有对应实现时不照搬别的项目名称。
- 通过不可变 UiState 和只读 `StateFlow` 暴露状态，一次性事件使用现有 Channel 机制，随 `STARTED` 生命周期收集。需要返回页面后再次可见的任务终态放入状态。
- ViewModel 不持有 Activity、Fragment、View、Binding 或 Dialog。View 任务随 View 销毁取消；跨配置业务任务由 `viewModelScope` 管理，不使用无归属协程。
- `SavedStateHandle` / Bundle / 导航参数只放小型可序列化值；Bitmap、Context、View 和大列表不放进去。明确区分内存快照、任务栈恢复和磁盘持久化。
- 状态回填保持幂等，不能触发额外编辑、保存或任务。异步结果核对请求身份、代次与 View 生命周期；销毁时取消动画、移除监听并释放 Binding。
- 复杂布局与动画拆到页面专用 Controller / Renderer，业务规则下沉；轻量静态页面不机械新增 ViewModel。

### 系统栏、视觉与连续交互

- 保留系统状态栏与导航能力，让背景延伸到边缘，交互区通过 Insets 避让。Dialog / BottomSheet 的独立 Window 单独处理系统栏。
- 明确状态栏、Toolbar、底部导航和 IME 的安全区归属；以 XML 初始 padding / margin 为基准，重复 Insets 不累加。输入与末项滚动空间考虑 `maxOf(navigationBars.bottom, ime.bottom)` 和实际浮层遮挡。
- 同级页面共用标题基线、边距、文字和主题语义；不以负 margin 或固定平移修补单页对齐。只调整覆盖层背景透明度，不降低整组文字和按钮的 alpha。
- 保留页面既有的浮层与滚动模式；新增沉浸式覆盖层时保证内容可绘制到其后方、首屏及末项可操作。工具栏高度按实际测量，含 Tab 或多行时不能假定固定高度。
- 滚动显隐从当前进度接续，快速反向先缓冲，暂停时保留状态；主要使用 translation / alpha，避免每帧改高度、padding 或 visibility 导致重排。
- 详情返回、前后台与窗口变化保留列表锚点和有效交互状态；只有当前可见页驱动共享导航。离开页面恢复临时修改的系统栏外观、共享菜单及监听。
- 根据改动范围考虑浅深色、大字体、窄屏、横屏、宽屏、多窗口、RTL、手势/三键导航、键盘和无障碍；装饰不拦截触摸，隐藏操作退出无障碍与键盘遍历。

### 性能、取消与资源归属

- 文件、数据库、媒体扫描、EXIF 与清理放 IO；图像变换及可脱离 View 的重计算放 Default。主线程不以 `runBlocking` 等待后台任务。
- 相同输入任务去重；高频搜索、滑块和刷新合并请求并保留最终输入。取消不能被通用异常捕获吞掉，旧结果不能覆盖新状态。
- 导出、删除、导入和清理防重复执行；涉及共享资源时，等待旧任务退出后再交接。失败和取消清理临时结果并保留可恢复状态。
- 列表按稳定身份和差异更新，已有数据时后台静默刷新。返回恢复布局状态，输入或窗口不匹配时有明确锚点回退。
- 缓存按字节设上限、限制解码并发；请求身份包含资源版本、目标尺寸与裁切模式。热路径避免每帧分配 Bitmap、启动 IO 或重复布局。
- 明确 Bitmap 的创建、独占、借用、转交和释放；交给 View / Canvas 或共享缓存的 Bitmap 不在取消、淘汰或换页时直接 recycle。不能把缓存上限当成全部图片内存预算。
- 性能收益必须有实际采集依据；静态预期与测量结论分开记录，不宣称未经测量的提升比例。

### 源码、资源与文档

- 新增或修改类、函数及成员变量补充简短中文 KDoc / Javadoc；重要边界加行内原因说明。主构造参数写在类文档的 `@param` / `@property` 中。
- Kotlin 多参数调用优先命名参数；Java / Android API、函数类型和已清楚的简短调用不强行套用。
- 文案进入字符串资源，格式化使用占位符，不拼接后直接 `setText`，不以 Suppress 掩盖 `SetTextI18n`。同步项目当前全部语言目录；尺寸沿用本项目既有资源规范。
- Markdown 使用 UTF-8、语义标题和正常换行，仓库链接用相对路径。保留原始设计、第三方素材许可及来源；文档外链沿用既有受控浏览器路由。

## 数据模型与混淆边界

- 新增或修改可能用于网络请求/响应、业务数据传输的 Kotlin `data class`、Java Bean，以及通过 Gson 等反射读写的缓存模型，统一添加 `androidx.annotation.Keep` 的 `@Keep`。
- 同时检查嵌套 DTO、列表元素、通用响应包装、枚举及其他序列化字段类型；外层加注解不等于自动保留嵌套类。字段名、协议 key 和序列化格式保持兼容。
- 在实际使用注解的模块显式依赖 AndroidX Annotation，并确保 R8 规则保留 `@Keep` 类及成员；不用保留整个业务包代替精确标记。纯 View 状态、Bitmap 容器和布局几何按实际边界判断。
- `@Keep` 只保护模型结构，不代表增加网络能力，也不能替代接口校验、隐私披露或数据迁移。

## 签名、日志与打包

- Debug 不指定 `signingConfig`，使用 Android 默认 `debug.jks`，无需项目密钥或正式签名凭据；Release 固定使用本项目 `app/jks/` 中的签名文件，实际文件名与凭据入口见 `scripts/README.md`。
- Release 口令通过未跟踪的 `app/keystore.properties` 或同名 Gradle 属性提供。项目签名校验仅挂接 Release 打包与签名任务；缺少密钥或凭据时 Release 打包应明确失败，不能产出未签名包。`scripts/package.sh debug` 跳过项目签名检查与凭据输入，`release` / `all` 保留检查。口令不写入源码、脚本、日志或提交。
- Debug 显式 `isDebuggable = true`，由 `BuildConfig.DEBUG` 开启现有调试日志；Release 显式不可调试，不打开调试日志。日志不输出签名口令、会话凭据或完整敏感请求正文。
- APK 按 `<项目前缀>_<versionName>_<yyyy-MM-dd>_<buildType>.apk` 命名，版本名保持原值；`exportDebugApk` / `exportReleaseApk` 导出到 `app/release/`，脚本入口为 `scripts/package.sh`。
- `app/release/`、本机口令及 IDE 状态不随源码提交。构建、脚本静态检查、单测、Lint 与设备验收分别报告；仅用户要求或确有需要且符合本项目验证约定时运行相应检查。
