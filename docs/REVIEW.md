# 项目审查记录（2026-11-06）

范围：仓库 Markdown、Android 资源与引用、网络 / 缓存 / 定位、页面状态和动画绘制。本文区分已完成的整理与尚未修改的行为问题；P2 表示应修复的功能问题，后续优化另列。

## 已完成的文档整理

| 文档 | 修改 |
| --- | --- |
| 中英文 README | 各从 414 行精简为 129 行，保留运行、功能、录屏、调试和文档入口 |
| DESIGN | 从 664 行精简为 269 行，移除开发会话叙述、重复 DTO / UiState / 依赖清单；按当前代码说明边界 |
| free-weather-apis | 保留选型理由和对比，明确历史配额未在本次重新验证，请求参数以代码为准 |
| PRIVACY 与应用内摘要 | 同步移除闲置权限说明，补充系统 Geocoder 可能使用网络服务，以及系统备份的删除边界 |

纠正的主要表述：

- CMA 的“前 5 天”包含今天，16 天报告其余 11 个日期使用 `best_match`；粗略坐标包围盒不等同于精确国界，也未证明该模型始终更准确。
- 头部的 30 秒 ticker 只更新文案；天气缓存检查在页面进入 / 恢复时执行，阈值为 30 分钟，没有后台轮询。
- 城市卡 `density × 0.35` 缩小粒子尺寸与速度，不减少粒子数量；全屏动画跟随屏幕刷新率，没有固定限为 60 fps。
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

## 待修复的功能问题

以下项目仍保留原实现。结论基于调用链和静态代码；定位和调试手势仍需专门回归。城市并发和删除缓存问题 R8–R10 已在 v1.2 处理，见上表。

### R5 · P2：触摸模拟挂在被分页内容覆盖的背景上

位置：[activity_main.xml](../app/src/main/res/layout/activity_main.xml)，背景与 ViewPager2 层级；[WeatherBackdropView.kt](../app/src/main/java/com/sugar/weather/view/WeatherBackdropView.kt)，`onTouchEvent`。

模拟手势仅由最底层背景 View 接收，但上方 ViewPager2 铺满窗口；已有城市时，分页和 NestedScrollView 会消费触摸。透明背景不会让触摸自动穿透，代码也没有从覆盖层转发，因此开启模拟后不能依靠在天气页拖动来改变姿态。

建议提供明确的调试触摸区域，或仅在调试模式下由统一手势入口转发；同时验证下拉刷新、逐小时横向滚动和城市分页仍可使用。

### R6 · P2：大致位置权限与固定 100 m 精度门槛冲突

位置：[LocationDataSource.kt](../app/src/main/java/com/sugar/weather/data/LocationDataSource.kt)，`hasPermission` / `awaitGoodFix`。

权限检查接受 COARSE，但新位置必须满足 `accuracy <= 100f`。仅允许大致位置且没有 5 分钟内历史位置时，系统提供的位置通常达不到这个门槛，即使持续收到有效坐标也会等待到 10 秒后失败。

建议按当前权限设置城市级精度门槛，或在超时前返回足够新鲜的最佳位置。回归覆盖仅 COARSE、FINE、关闭定位和超时。

### R7 · P2：已有定位城市永远不会重新获取坐标

位置：[MainActivity.kt](../app/src/main/java/com/sugar/weather/MainActivity.kt)，`maybeLocate`。

只有无城市，或“没有定位城市且已有权限”时才定位。一旦创建 `isLocated=true` 的城市，以后即使换城市、重启应用也不会再定位；下拉刷新只查询保存的旧坐标。

建议在合理的前台时机更新位置，并对频率、距离和缓存失效设置规则，避免每次恢复都弹权限或强制请求。回归需要模拟移动后更新原定位城市，而不是新增一张定位卡。

## 优化建议

| 优先顺序 | 位置与现状 | 建议与验证 |
| --- | --- | --- |
| 1 | `WeatherRepository` 没有同城市请求合并；页面刷新与管理页批量刷新可重叠 | 用按 cityId 共享的进行中任务合并请求；页面目前已等待磁盘预热。用请求计数验证一次逻辑刷新只产生一组请求 |
| 2 | 天气转换已移到 Default，序列化 / 落盘移到 IO | 后续用帧耗时衡量收益；避免仅凭线程切换声称提升帧率 |
| 3 | 每张城市卡有独立帧回调，仍遍历全量粒子；每次更新创建 `Tilt()` | 复用中性姿态，按卡片面积设置粒子数量，评估统一调度和仅可见卡片更新；先测雨雪多卡场景再确定预算 |
| 4 | 主页面主题、根布局、Canvas 都提供背景；Lint 报告多处 Overdraw | 保留可靠的启动背景，评估移除运行时多余背景；验证首帧、切后台恢复与窗口透明效果 |
| 5 | `WeatherMapper` 把缺失湿度、气压、风速等回退为 0 | 用可空字段和“—”区分缺测与真实 0；保持旧缓存兼容 |
| 6 | 跨日太阳事件和缺日报曲线对齐已修复 | 继续验证极地日出日落缺测与不同城市时区的数据 |
| 7 | release 的 `optimization.enable=false`；版本号已改为单一来源 | 独立评估 R8 和资源缩减，先验证 Gson / 旧缓存 |
| 8 | 温度留白已按窗口重算，图标触摸区与安全区已统一；Manifest 仍指定竖屏 | 后续补充平板与折叠屏布局，不把手机排版改进当作完整大屏适配 |

暂不建议为了“更新”而批量升级依赖、引入复杂注入框架，或逐条消除所有风格型 Lint 提示。先处理影响数据正确性和主要交互的问题，再以性能测量决定动画优化。

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
