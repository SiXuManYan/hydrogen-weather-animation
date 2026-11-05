# 更新日志

本项目的重要变更记录。日期均为 UTC+8。

## [1.1] - 2026-11-05

### 数据来源与准确性

- **中国境内坐标**（粗略包围盒：北纬 18°–54°、东经 73°–135°）前 5 天优先使用 **CMA GRAPES** 全球模型
  （Open-Meteo `models=cma_grapes_global`，`forecast_days=5`）。
- 第 6–15 天以及非中国坐标继续使用 Open-Meteo **best_match**；CMA 请求失败时静默回退，不影响整体刷新。
- 逐日天气不再直接使用 Open-Meteo 的 `daily.weather_code`（其含义是“当天最严重的小时天气”），
  改为按城市本地白天 **08:00–20:00** 的逐小时天气聚合：
  - 代表天气码：白天出现次数最多的天气码（同次数取更严重者），用于图标与首页 6 天短文案；
  - 转折描述：连续同类别分段后，保留首段、最长中间段、末段（最多 3 段），生成“毛毛雨转晴”“小雨转中雪”等文案，15 天页展示。
- 空气质量仍来自 Open-Meteo Air Quality API；城市搜索仍来自 Open-Meteo Geocoding API。

### 新增

- 新增 `docs/PRIVACY.md`（中英双语隐私政策）。
- 新增 `docs/free-weather-apis.md`（免费天气 API 调研与选型记录）。
- 新增 `ChinaRegion` 与 `DailyCondition` 单元测试，覆盖白天天气聚合与回退逻辑。
- 关于页新增隐私政策摘要弹窗，并补充数据来源与取样方式说明。

### 变更

- 版本号升级至 **1.1**（versionCode 2）。
- 关于页更新日志、数据来源署名与隐私说明同步更新。

### 修复

- 修复 `CitySearchActivity` 在 `onCreate` 前访问 ViewModel 导致的城市搜索页启动崩溃。
- 修复 `WeatherFragment` 缺少 `fragment-ktx` 导致的 `viewModels` 编译错误。
- 修复 merge 布局 `WeatherSingleInfoView` 的 ViewBinding `inflate` 参数错误。

## [1.0] - 2026-11-05

- 初始版本：致敬氢 OS 一加天气 V2.6.1.1 的开源复刻。
- Open-Meteo 实况、逐小时、逐日与空气质量数据。
- 多城市管理、城市搜索、15 天天气预报、两列详情卡。
- 15 种纯 Canvas 天气场景动画与倾斜视差。
