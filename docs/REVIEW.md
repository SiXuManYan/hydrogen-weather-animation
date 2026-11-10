# 项目审查记录（2026-11-06）

范围：仓库 Markdown、Android 资源与引用、网络 / 缓存 / 定位、页面状态和动画绘制。本文保留审查与历史验证记录；P2 表示审查时确认的功能问题。本轮已处理 R5–R7、R11–R16 及对应性能、布局与收缩配置，按用户要求未运行验证。

## 已完成的文档整理

| 文档 | 修改 |
| --- | --- |
| 中英文 README | 各从 414 行精简为 129 行，保留运行、功能、录屏、调试和文档入口 |
| DESIGN | 从 664 行精简为 269 行，移除开发会话叙述、重复 DTO / UiState / 依赖清单；按当前代码说明边界 |
| free-weather-apis | 保留选型理由和对比，明确历史配额未在本次重新验证，请求参数以代码为准 |
| PRIVACY 与应用内摘要 | 同步移除闲置权限说明，补充系统 Geocoder 可能使用网络服务，以及系统备份的删除边界 |

纠正的主要表述：

- CMA 的“前 5 天”包含今天，16 天报告其余 11 个日期使用 `best_match`；粗略坐标包围盒不等同于精确国界，也未证明该模型始终更准确。
- 头部的 30 秒 ticker 只更新文案；天气缓存检查在页面进入 / 恢复时执行，阈值为 30 分钟。新增微件后，仅在有微件时通过 WorkManager 约每 30 分钟更新首城。
- 城市卡 `density × 0.35` 只负责几何尺寸与速度；本轮另按面积减少粒子数量。全屏动画跟随屏幕刷新率，没有固定限为 60 fps。
- `STORM` 与 `HAZE / SANDSTORM / SLEET` 都没有实况自动映射，需通过调试选择。
- AGP 内置 Kotlin 不等于禁止所有编译器插件或 KSP；已依据官方迁移文档修正文档中的绝对结论。
- 当前 PM2.5 分档使用当前浓度，不能称为完整中国 AQI。

## 已完成的资源清理

Lint 与资源引用图扫描一致确认了以下 6 项闲置资源；未发现 `getIdentifier` 等动态资源查找：

| 资源 | 处理 |
| --- | --- |
| `drawable/ic_sleet.xml` | 删除未被页面或图标映射引用的矢量图；雨夹雪动画实现保留 |
| `color/weather_overlay_scrim_strong` | 删除 |
| `string/scene_button` | 删除旧场景按钮文案 |
| `string/day`、`string/night`、`string/scene_format` | 删除旧昼夜格式文案 |

另删除与默认主题逐字相同的 `values-night/themes.xml`，夜间自动使用默认主题；移除默认主题中低于等于 minSdk 的冗余 `tools:targetApi` 标记。Manifest 中未使用的 `ACCESS_NETWORK_STATE` 一并移除。

保留 30 个被 README 引用的录屏（约 52.7 MiB）与隐藏的预警占位图标。v1.2 绘制了原创 adaptive icon，最低 API 29 不再需要默认机器人 WebP，已移除其 10 个多密度回退图及旧关于页云图。当前工程内没有发现 `.craft` 文件，没有处理此类文件。

## 本轮完成的代码与 UI 改进

| 问题 | 当前处理 |
| --- | --- |
| R1：手动首城被误锁 | 管理页按 `isLocated` 判断源 / 目标卡，手动首项可拖删；后台刷新圈正常显示 |
| R2：恢复页面不复查缓存 | `onResume` 重新检查，等待磁盘预热；首页与详情共享 30 分钟阈值，日期按城市时区的真实今天生成 |
| R3：首尾同类吞掉中间天气 | 保留时间顺序，只合并相邻同类；新增晴→雨→晴、雨→晴→雨及筛选后合并测试 |
| R4：温度曲线不主动更新 | 数据变化后 `invalidate`，相同数据跳过计算；不足 6 天时仍与日报六列对齐，不向空列外推 |
| 跨日逐时展示 | 太阳事件带完整日期，包含次日日出；小时图标按对应日的日出日落选择昼夜 |
| 主线程工作量 | DTO 映射 / 聚合转至 Default，缓存序列化 / 写入转至 IO；搜索标签切换时复用 |
| UI 排版 | 24dp 页边距、48dp 图标触摸区、统一工具栏；温度区约位于可用窗口 36% 处；详情分组、长城市名和窄屏预报重新排版 |
| 安全区与状态覆盖层 | 共用 View insets 工具，覆盖侧边刘海、底部系统栏和搜索键盘；加载 / 错误层移到 FrameLayout，避免作为 SwipeRefreshLayout 的额外内容 |

## v1.2 完成的城市、Logo 与文档改进

| 问题或目标 | 当前处理 |
| --- | --- |
| 删除入口难发现 | 编辑或卡片长按进入多选，蓝色描边、数量、关闭与确认删除；选择按标识保存，可恢复 |
| 多选与排序长按冲突 | 右侧独立排序手柄，支持拖拽及上下移动菜单；普通模式保留左滑确认 |
| R8：城市读改写覆盖 | 统一 `CityStorage.updateCities` 的 DataStore 事务，仓库 Mutex 覆盖定位、添加、重排与删除 |
| R9：重复城市返回错误标识 | 仓库返回实际保留的 City，搜索事件使用其 id |
| R10：删除后缓存复活 | 事务删除磁盘天气并清除内存；网络完成后在共享互斥区核对存储中的城市身份再提交 |
| Logo 不统一 | 原创几何标识、桌面 adaptive icon 与单色图标，导出 SVG / PNG 并记录生成方法 |
| 关于页协议占位、隐私仅摘要 | 五个离线 Markdown 入口，正文由仓库构建同步；补齐中英文法律文本、用户帮助和组件说明 |
| 版本多处硬编码 | Gradle 单一版本配置，关于页读取 BuildConfig；v1.2 / versionCode 3 |

仓库新增 6 个删除回归测试，控制网络请求先开始、城市再删除、结果最后返回的时序，并覆盖并发城市修改、手动首项、定位城市保护、重复地点返回和失败重试。另有 6 个纯状态测试覆盖多选资格、取消最后一项和选择清理。测试不依赖设备，不能代替实际拖拽与无障碍验收。

## v1.3 刷新与 Material 3 改进

| 问题或目标 | 当前处理 |
| --- | --- |
| 下拉与分页、逐小时列表竞争触摸 | `WeatherSwipeRefreshLayout` 按起手方向分发，纵向保留刷新 / 正文滚动；小时列表可滚动时优先横滑，到边界后放行分页 |
| 日报在纵向滚动链中参与拦截 | 日报禁止自身纵向滚动，日报和逐时列表关闭嵌套滚动，只由外层正文提交纵向距离 |
| 刷新圈被顶部安全区裁切、白色箭头不清晰 | 圆圈偏移显式包含状态栏与固定头部，使用 M3 主色与容器底色 |
| 刷新反馈依赖一次性结束事件 | 改用 `WeatherUiState.isRefreshing`，视图重建 / 重新布局可恢复，任务结束或取消均复位 |
| 弹窗仍是 M2 小圆角 | 全局切到 `Theme.Material3.DayNight.NoActionBar`，确认统一使用 `MaterialAlertDialogBuilder` 与 M3 overlay |
| 部分控件仍使用旧样式 | 按钮、开关、场景下拉、搜索输入、Assist Chip、FAB 和加载指示器采用 M3；移除不再使用的 FAB / 搜索背景 |
| 普通页面固定浅色 | 提供明暗 M3 色彩角色，工具栏、正文、章节导航与系统栏图标随系统切换；天气场景色保持独立 |

## 本轮审查问题的处理（含桌面微件）

下表记录本次审查发现与已经实现的处理。**按用户“无需验证、直接完成功能”的要求，本轮未运行构建、测试、Lint、脚本检查或设备验证。** 本节“已处理”指实现已修改；后文历史测试和受控复现来自修复前，不能当作本次改动通过验证的结论。

| 审查问题 | 修复前触发与影响 | 当前实现 |
| --- | --- | --- |
| R5 · P2：背景触摸被分页覆盖 | 已有城市时 ViewPager2 / 滚动内容消费触摸，底层背景无法收到模拟拖动 | `MainActivity.dispatchTouchEvent` 观察首指拖动并更新姿态，不消费、改写或截断原事件；背景 View 仅渲染 |
| R6 · P2：大致位置达不到 100m 门槛 | 仅 COARSE 时持续收到有效城市级坐标仍可能超时 | FINE 理想精度 100m、COARSE 5km；10 秒内记录最佳新鲜位置，超时可回退至 10km 内候选；完成和取消均注销 |
| R7 · P2：已有定位城市不更新坐标 | 移动或重启后仍请求保存的旧坐标 | 已授权时前台静默定位，成功 30 分钟 / 失败 2 分钟节流；位移达到 `max(1km, 精度半径)` 更新原定位城市、失效两层缓存并刷新；离开首页取消定位 |
| R11 · P2：旧并发结果覆盖新天气 | 先发后回的旧观测覆盖较新内存与磁盘，还显示更晚更新时间 | 仓库按城市 id、坐标、添加时间和修订代次共享进行中的请求；提交核对身份与代次，拒绝较旧观测，保留已有报告与更新时间 |
| R12 · P2：缺测成功响应清空缓存 | HTTP 成功但没有有效天气的 DTO 仍覆盖离线报告 | 提交前要求有效实况、观测时间和至少一条日报；缺失返回 Parse 失败，不改写已有内存、磁盘或成功时间 |
| R13 · P2：损坏缓存阻断冷启动 | Gson 成功解析缺列表字段的 JSON 后，摘要恢复调用空引用 | `WeatherReportValidation` 归一化集合、日期、数值和城市归属；存储解析和摘要恢复逐条隔离，坏条目跳过，预热失败也允许启动 |
| R14 · P2：15 天详情恢复不刷新日期 | 退后台过夜后恢复，只收集旧 StateFlow，“明天”可能实际为今天 | `onResume` 先按真实日期重建缓存列表，再检查 30 分钟新鲜度；刷新失败保留有效离线项，报告失效则清空旧位置内容 |
| R15 · P2：微件离线跨日不重绘 | 唯一周期要求联网，无网跨日无法重算高低温与“明天” | 无联网约束的缓存 Worker 约 30 分钟重绘；独立联网 Worker 等待 CONNECTED。UPDATE 迁移旧周期约束，并清理旧即时队列 |
| R16 · P2：新增城市结果传递中断 | 管理页用 startActivity 打开搜索，未接收 setResult，返回首页仍在原城市 | 管理页 Activity Result 接收并转发 cityId；首页读取真实城市快照，同步分页后切到新增或已有城市 |

实现入口：

- 数据与缓存：[WeatherRepository.kt](../app/src/main/java/com/sugar/weather/data/WeatherRepository.kt)、[CityStore.kt](../app/src/main/java/com/sugar/weather/data/CityStore.kt)、[WeatherReportValidation.kt](../app/src/main/java/com/sugar/weather/data/WeatherReportValidation.kt)。
- 定位与触摸：[MainActivity.kt](../app/src/main/java/com/sugar/weather/MainActivity.kt)、[MainViewModel.kt](../app/src/main/java/com/sugar/weather/ui/MainViewModel.kt)、[LocationDataSource.kt](../app/src/main/java/com/sugar/weather/data/LocationDataSource.kt)。
- 页面：[FifteenDayViewModel.kt](../app/src/main/java/com/sugar/weather/ui/fifteenday/FifteenDayViewModel.kt)、[CityManageActivity.kt](../app/src/main/java/com/sugar/weather/ui/city/CityManageActivity.kt)。
- 微件：[WeatherWidgetScheduler.kt](../app/src/main/java/com/sugar/weather/widget/WeatherWidgetScheduler.kt)、[WeatherWidgetCacheWorker.kt](../app/src/main/java/com/sugar/weather/widget/WeatherWidgetCacheWorker.kt)、[WeatherWidgetController.kt](../app/src/main/java/com/sugar/weather/widget/WeatherWidgetController.kt)。

修复前，本地使用当时编译类与受控网络 / 存储边界确认了三项数据问题：A 请求 12:00 / 10℃ 被阻塞，B 请求 12:15 / 20℃ 先提交后，释放 A 会使两层缓存回退；完全缺测 DTO 会返回成功并清空缓存；合法但缺少 hourly/daily 的 JSON 在恢复摘要时抛 NPE。最后一种触发依赖缓存损坏或结构不兼容，正常缓存不会必现。这些是缺陷诊断证据，本轮没有重新执行。

2026-11-07 根据编译错误修正 `CityStore.parseCities`：接收 Gson 的 `Array<City>` 时使用只读投影 `Array<out City?>?`，避免 Kotlin 数组不变性引起的类型不匹配，同时保留空数组元素过滤。沿用用户要求，未运行构建、测试或设备验证。

### 本轮一并实现的优化

| 项目 | 当前处理与约束 |
| --- | --- |
| 城市卡帧调度 | 每张卡仍持有独立场景，统一使用一个最高约 24fps 的延迟帧回调；只更新实际可见卡片，最后一张注销后停止时钟 |
| 粒子预算与分配 | 中性 Tilt 复用；雨雪、霾和沙尘按卡片面积使用全屏 12%–40% 的粒子（至少 12 个），数组和循环均按预算创建；全屏数量保留 |
| 首页背景重复绘制 | 保留主题启动背景；内容挂载后移除窗口背景，去掉根布局纯色填充，由天气 Canvas 提供运行时不透明底色 |
| 缺测数值 | 体感温度、湿度、气压、风速与风向改为可空，缺测显示“—”并隐藏单位；保留原 JSON 字段名，不猜测旧缓存零值是否缺测 |
| 微件紧凑尺寸 | 72dp 时顶部更新栏 16dp，预报日期 / 图标 / 温度为 16 / 24 / 16dp；刷新触摸区维持 48dp |
| Release 收缩 | AGP 9 启用 R8 和资源收缩，使用优化默认规则；显式保留 Gson DTO / 缓存类字段和构造器，不整包保留框架或业务方法 |

本轮没有性能采样，也没有生成优化后的安装包，因此不量化帧率、耗电或体积收益。修复前 Debug APK 为 24,144,169 字节，22 个 DEX 的压缩体积合计 19,754,020 字节，仅作为历史基线。没有为更新而批量升级依赖，也没有为了风格消除全部 Lint 警告。

大屏 / 折叠屏完整布局、TalkBack、极端字体、极地太阳事件、真实桌面调度和 Release 运行行为仍属于后续验收范围；不能把本次逻辑修改表述成已经通过设备验收。未使用已连接的诺基亚手机，未调用任何设备查询或模拟器命令。

修复前的项目审查执行了 `:app:assembleDebug :app:testDebugUnitTest :core_framework:testDebugUnitTest :app:lintDebug :core_framework:lintDebug`：78 个测试（App 65 / Library 13）全部通过，Lint 均为 0 错误、警告分别 41 / 26。该结果与以下记录一样，不覆盖本次修复。

## 验证记录

在 macOS arm64、Android Studio JBR 25.0.2 环境运行：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  sh ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

- v1.2 通过 Debug 构建、Lint 和全部 28 个 JVM 测试：9 个白天天气聚合、6 个预报时间线、6 个多选状态、6 个城市删除 / 并发回归及 1 个模板测试。
- Lint 0 错误、37 条警告，`UnusedResources` 为 0；警告主要为重复绘制、固定方向、旧传感器 API 和依赖版本建议，具体以生成报告为准。
- XML 解析、Markdown 本地链接、图标源文件一致性和 APK 文档内容检查作为本轮静态验证；只打包用户文档，没有将场景 MP4 或研发说明放入 APK。
- 本轮遵循“不使用模拟器验证”的要求，未启动模拟器或连接设备。多选与排序手势、TalkBack、图标遮罩、大字体、定位、动画性能和 release 行为尚未设备验收。
- 此前 API 30 的页面检查属于 v1.1 工作区历史记录，不能作为 v1.2 新交互通过设备验证的结论。

2026-11-06 的刷新与 M3 迁移验证（覆盖当前工作区）：

- Debug 构建和 AndroidTest 测试包编译通过；42 个 JVM 测试全部通过，包括扩充后的 23 个全天气聚合用例。
- Lint 为 0 错误、37 条警告；`UnusedResources`、`SetTextI18n` 均为 0。保留的警告以构建生成报告为准。
- 在用户提出停止测试机操作前，一加 7 Pro / API 29 已完成 `WeatherInteractionTest` 的 6 个测试：斜向下拉触发一次刷新、不回顶不刷新、普通横滑切城、小时列表独立横滑、取消手势后放行分页，以及浅深两种配置下的页面 / 弹窗创建和 M3 28dp 圆角。
- 最初 Gradle connected 流程因 ADB 中断未取得结果；重新连接后直接运行已安装测试包，输出 `OK (6 tests)`。该结果只覆盖无网络测试内容的事件分发与主题创建，不代表真实联网刷新、所有实际页面手势或视觉布局均已验收。
- 用户要求不再调用测试机后，停止设备操作；后续仅做本地构建、测试、XML / 文档链接及差异检查。未运行模拟器，也未完成窄屏 / 大字体、TalkBack、动态主题切换或性能验收。

2026-11-06 的页面切换动画改进：

- 参照 CollageWorkshop 接入四段 280ms 横向动画，通过详情页窗口主题覆盖城市管理、搜索、15 天预报、关于与文档的进入和返回；选城结果改用 ViewPager2 平滑切页。
- 本地 `:app:assembleDebug :app:lintDebug` 通过，Lint 仍为 0 错误、37 条警告；全部主资源 XML 解析和 `git diff --check` 通过。
- 遵循用户要求，没有调用测试机或模拟器；实际动画观感及系统返回手势衔接尚未设备验收。

2026-11-06 的基础框架网络迁移：

- 复制 AndroidFrameworkTemplate 的 `core_framework` 源码 Library 和 MIT 许可证，App 使用 `project(":core_framework")`；不依赖参考工程的本机路径。
- 模块适配宿主 AGP 9 内置 Kotlin / compileSdk 37 / Java 17。修复复制源码中延迟仓库属性 getter 与 `getRepository()` 的 JVM 签名冲突，并补齐 `MutableDataAdapter` 对空条目的 DiffUtil 分支；适配清单见模块 README。没有改动参考工程。
- `WeatherApplication` 初始化框架日志与版本；`WeatherApiClient` 通过库的工厂创建共享 HTTP 客户端和三个 Open-Meteo 服务。保留原超时、DTO、降级和缓存规则；移除 App 自建 Retrofit / OkHttp 以及旧日志依赖，调试日志使用不含查询 / 正文的摘要。
- 本地执行 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core_framework:testDebugUnitTest :app:lintDebug :core_framework:lintDebug` 全部通过。App 49 个测试、Library 13 个测试，共 62 个通过，0 失败 / 错误 / 跳过；两个模块 Lint 均为 0 错误，分别 41 / 26 条警告。
- 新增 7 个 HTTP 用例覆盖三个独立服务地址、默认参数、JSON 解析、匿名 Header、超时、调试日志选择、HTTP / 解析失败、可空搜索结果及协程取消；请求仅发送至本机 MockWebServer。复制库的列表测试改用本机 Robolectric，补充了空条目回归。
- 主资源 XML、Markdown 本地文件链接、上游源码差异与许可证核对通过；构建目录均被 Git 忽略。遵循用户要求，没有连接、查询或运行测试机 / 模拟器，也没有请求真实天气服务。

2026-11-06 的桌面天气微件：

- 新增系统微件声明、横向 RemoteViews、天气与昼夜矢量背景、手动刷新、对应城市跳转，以及首页添加微件入口。后台由 WorkManager 维护共享 30 分钟周期任务，无实例时停止，不请求后台定位。
- 本地通过 Debug / AndroidTest APK 构建、78 个测试（App 65、Library 13）和两个模块 Lint，均为 0 错误，警告仍为 41 / 26。新增 16 个本地用例覆盖城市时区、离线跨日、日期缺测、报告身份、缓存预热、手动刷新、失败保留、前台成功清除失败标记、请求中删除首城及无微件停止。
- 微件设备测试尝试因 ADB 中断未取得测试结果，不能据此声称通过；已按用户要求停止模拟器验证，并清理本轮临时模拟器。真实桌面布局、点击、长期后台调度、重启恢复和大字体尚未完成设备验收。设备测试源码保留供后续维护使用。
- 使用帮助、中英文 README、设计、更新日志、隐私政策和开源说明已随实现更新；安装包中的帮助与政策仍由原同步任务生成。

## 2026-11-08 稳定依赖与工具链升级

核对来源为 [Google Maven](https://dl.google.com/dl/android/maven2/master-index.xml)、[Maven Central](https://repo.maven.apache.org/maven2/)、各构件的 `maven-metadata.xml` 与发布 POM/Gradle Module Metadata；仅采用正式稳定版。AGP 9.5.1 的[官方兼容说明](https://developer.android.com/build/releases/agp-9-5-0-release-notes)要求 Gradle ≥9.6.0、JDK ≥17，Wrapper 从 9.5.0 升至 9.6.1，并用[官方 SHA-256](https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256)校验发行包。

| 组件 | 原版本 | 升级后 |
| --- | --- | --- |
| AndroidX Annotation | 1.10.0 | 1.11.0 |
| Core KTX | 1.19.0 | 1.19.1 |
| AppCompat | 1.7.1 | 1.8.0 |
| Activity KTX | 1.10.1 | 1.13.0 |
| Coroutines | 1.10.2 | 1.11.0 |
| Gson | 2.13.1 | 2.15.0 |
| OkHttp 及关联构件 | 4.12.0 | 5.5.1 |
| Robolectric | 4.16.1 | 4.17 |
| Browser | 1.8.0 | 1.10.0 |
| AGP | 9.3.3 | 9.5.1 |
| Gradle Wrapper | 9.5.0 | 9.6.1 |

Retrofit 3.0.0、Fragment 1.9.1、Lifecycle 2.11.0、DataStore 1.3.0、WorkManager 2.12.0 等已为稳定最新版，保留；Markwon、Timber、JUnit 等未发布更新。OkHttp 5.5.1 保留原坐标，由 Gradle 元数据选择 Android/JVM 变体；旧包名 MockWebServer 5.5.1 仍正式发布，保留现有测试 API，无需迁移 mockwebserver3。Gson 模型与历史 JSON 字段协议不变，缓存旧数据读取仍需后续回归。

同步 AGENTS、双语 README、DESIGN 的当前工具链与网络版本。已有构建缓存中的 OkHttp 成员引用辅助对照未发现缺失；缓存不是当前源码编译证据。实际检查仅包含版本目录 TOML 与引用一致性、升级目标的发布元数据、AAR 的 minSdk / minCompileSdk / AGP 要求、Wrapper 校验值、当前文档版本与本地链接、历史记录保全及 `git diff --check`。本次未运行 Gradle、构建、依赖图解析、单元测试、Lint、安装或设备验收，旧构建/测试结果不覆盖本次升级；没有变更应用版本号或登记新发布。后续按需验证包括本机 HTTP 契约、取消/错误与旧缓存恢复、Robolectric 用例和微件调度。

## 2026-11-11 区县级定位名称

- 原实现以 `locality` 优先且不读取 `subLocality`，区县字段会被市名遮住；未达移动阈值时直接返回，已有城市也无法原地细化名称。本次新增 [LocatedPlaceNaming](../app/src/main/java/com/sugar/weather/data/LocatedPlaceNaming.kt)，优先使用行政字段中的明确区县，缺失时回退到城市和省级名称；过滤空白、分类占位和明确街道 / 乡镇 / 村 / 社区，不从完整地址或门牌猜测区县。地区、自治区和特别行政区不会因“区”后缀被误当成县级区，县级市及海外名称保持原有字段层级。
- 首页沿用前台权限、定位精度与成功 30 分钟 / 失败 2 分钟节流。未达移动阈值时反查已保存坐标，交给仓库只更新名称，保留城市 id、坐标、创建时间、顺序和两层天气，不因改名触发额外天气请求。事务内核对原城市身份、坐标及名称，迟到结果不能覆盖移动后的地点或并发提交的新名称。没有新增服务方、权限或后台定位。
- 新增 9 个 [命名规则测试](../app/src/test/java/com/sugar/weather/data/LocatedPlaceNamingTest.kt)及 5 个 [仓库回归测试](../app/src/test/java/com/sugar/weather/data/CityDeletionTest.kt)，覆盖直辖市 / 普通市的区名、不同字段中的县 / 自治县 / 旗、县级市、地区等上级行政区、街道排除、空白回退、海外层级、原地改名保留缓存和不请求天气，以及移动 / 并发改名后的迟到结果、手动与缺失城市保护。测试源码已补充，本次没有执行测试。
- 双语 README、设计、使用帮助、更新日志及中英文隐私政策随实现更新；版本号保持 1.3，功能记录在未发布条目中。通过 `android-cli` 检索后，另核对本机 Android SDK 的 `Address` / `Geocoder` 官方源码：`subLocality` 可表示市内分区或社区，反查的细度及可用性由系统提供方决定，因此不保证每台设备都能取得区县，大致位置也可能落入相邻区县。
- 实际检查范围：源码调用与事务 / 缓存边界静态核对、UTF-8 与本地 Markdown 文件链接检查、`git diff --check`。遵循仓库验证约定，未运行 Gradle、构建、JVM 测试、Lint、安装或任何设备任务。待设备验收：已有市名在原地更新为区县、精确 / 大致权限、系统缺少区县或反查失败的回退，以及首页、管理页与桌面微件名称同步；历史通过记录不覆盖本次改动。
