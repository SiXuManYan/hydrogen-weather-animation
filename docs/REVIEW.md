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

保留 30 个被 README 引用的录屏（约 52.7 MiB）、Manifest 引用的启动图标及其多密度版本、隐藏的预警占位图标。当前工程内没有发现 `.craft` 文件，没有处理此类文件。

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

## 待修复的功能问题

以下项目仍保留原实现。除另有验证说明外，结论基于调用链和静态代码；定位、并发删除和调试手势仍需专门回归。

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

### R8 · P2：城市列表的读改写可以互相覆盖

位置：[WeatherRepository.kt](../app/src/main/java/com/sugar/weather/data/WeatherRepository.kt)，`mutateCities` / `upsertLocatedCity`；[CityStore.kt](../app/src/main/java/com/sugar/weather/data/CityStore.kt)，`saveCities`。

业务先 `cities.first()` 读取，再在独立 `edit` 中覆盖整份列表。若定位回调与手动添加、删除或排序同时进行，两次操作可读取相同旧列表，后落盘者覆盖先落盘者，导致丢城市、恢复已删除城市或丢排序。DataStore 只串行化 `edit`，不会把此前的独立读取变成原子操作。

建议在同一个 `DataStore.edit` 内完成转换与归一化，或用覆盖所有修改入口的互斥保护；只锁 `mutateCities` 无法覆盖单独实现的定位写入。回归验证并发添加 / 删除 / 定位均保留有效结果。

### R9 · P2：重复城市返回的 id 可能根本不在列表中

位置：[CitySearchViewModel.kt](../app/src/main/java/com/sugar/weather/ui/search/CitySearchViewModel.kt)，`addCity`；[WeatherRepository.kt](../app/src/main/java/com/sugar/weather/data/WeatherRepository.kt)，`addCity`。

搜索端先按名称与坐标生成新 id；仓库遇到同坐标城市会忽略添加，但没有返回已存在城市的 id。搜索仍发送新 id 的 `CityAdded` 事件，主页按该 id 查找时失败，无法切换到用户刚选的已有城市。定位城市与热门城市使用不同 id 时可触发。

建议仓库返回实际保留的 City 或 id，搜索事件使用该结果。回归覆盖热门城市、同坐标不同名称与定位城市重复。

### R10 · P2：删除城市后，进行中的刷新还能重新写回缓存

位置：[WeatherRepository.kt](../app/src/main/java/com/sugar/weather/data/WeatherRepository.kt)，`removeCity` / `refreshWeather`。

删除只清理磁盘缓存，没有清理 `_reports`；刷新在开始时解析一次城市，随后即使该城市被删，成功后仍会更新内存并执行 `saveWeather`。管理页批量刷新期间左滑删除城市，就可能在请求完成后留下孤立天气缓存。

建议删除时取消该城市任务、移除内存条目，并让请求完成后的有效性检查与删除协调，避免只检查一次又发生竞态。回归应在请求未完成时删除，再确认磁盘与内存都不含该城市。

## 优化建议

| 优先顺序 | 位置与现状 | 建议与验证 |
| --- | --- | --- |
| 1 | `WeatherRepository` 没有同城市请求合并；页面刷新与管理页批量刷新可重叠 | 用按 cityId 共享的进行中任务合并请求；页面目前已等待磁盘预热。用请求计数验证一次逻辑刷新只产生一组请求 |
| 2 | 天气转换已移到 Default，序列化 / 落盘移到 IO | 后续用帧耗时衡量收益；避免仅凭线程切换声称提升帧率 |
| 3 | 每张城市卡有独立帧回调，仍遍历全量粒子；每次更新创建 `Tilt()` | 复用中性姿态，按卡片面积设置粒子数量，评估统一调度和仅可见卡片更新；先测雨雪多卡场景再确定预算 |
| 4 | 主页面主题、根布局、Canvas 都提供背景；Lint 报告多处 Overdraw | 保留可靠的启动背景，评估移除运行时多余背景；验证首帧、切后台恢复与窗口透明效果 |
| 5 | `WeatherMapper` 把缺失湿度、气压、风速等回退为 0 | 用可空字段和“—”区分缺测与真实 0；保持旧缓存兼容 |
| 6 | 跨日太阳事件和缺日报曲线对齐已修复 | 继续验证极地日出日落缺测与不同城市时区的数据 |
| 7 | release 的 `optimization.enable=false`，版本号在多个位置手动维护 | 独立评估 R8 和资源缩减，先验证 Gson / 旧缓存；版本号改为单一来源 |
| 8 | 温度留白已按窗口重算，图标触摸区与安全区已统一；Manifest 仍指定竖屏 | 后续补充平板与折叠屏布局，不把手机排版改进当作完整大屏适配 |

暂不建议为了“更新”而批量升级依赖、引入复杂注入框架，或逐条消除所有风格型 Lint 提示。先处理影响数据正确性和主要交互的问题，再以性能测量决定动画优化。

## 验证记录

在 macOS arm64、Android Studio JBR 25.0.2 环境运行：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  sh ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

- 清理前后两次通过上述全部任务。清理后 `UnusedResources` 为 0，Lint 仍有 37 条其他提示，主要是重复绘制、固定方向和依赖版本建议。
- 现有 JVM 测试共 16 个，全部通过：9 个白天天气聚合测试、6 个预报时间线测试、1 个模板算术测试。
- 资源扫描同时考虑 XML / Manifest、Kotlin `R` 引用和 ViewBinding 对布局的隐式引用；两份 README 的 30 个录屏链接、Markdown 本地链接 / 标题锚点及 XML 解析检查全部通过。
- R3 的首尾同类、重复降水和筛选后相邻段均有仓库内回归测试。
- 使用 API 30 小屏模拟器检查了首页、搜索键盘 / 热门城市、城市管理首项删除和 15 天长文案布局；没有验证实体设备传感器、定位、大屏、release 混淆或真实帧耗时。
