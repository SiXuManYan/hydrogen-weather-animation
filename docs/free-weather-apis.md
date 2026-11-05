# 免费天气 API 调研与选型

> 调研日期：2026-10-26
>
> 最后更新：2026-11-05（补充当前接入状态）
>
> 本文用于记录本项目接入真实天气数据时的数据源选型。各平台的免费额度、授权范围和接口字段可能随时调整，接入前请以官网和控制台的最新说明为准。

## 1. 结论

- **当前实现**：工程已接入 Open-Meteo 的 forecast / air-quality / geocoding 三个免费接口；中国境内坐标前 5 天额外使用 **CMA GRAPES** 模型（`models=cma_grapes_global`），第 6–15 天回退 `best_match`。日天气按白天 08:00–20:00 的逐小时数据聚合，不直接使用当天最严重的小时天气码。实现细节见 [DESIGN.md](DESIGN.md) §1.1.1，隐私说明见 [PRIVACY.md](PRIVACY.md)。
- **零成本开发 / 开源演示**：优先选择 **Open-Meteo**。无需注册和 Key，支持 16 天日预报、逐小时预报和空气质量数据，非商业用途每天 1 万次调用额度。
- **面向国内用户 / 后续商业化**：优先选择 **和风天气 QWeather**。中文天气现象、15 天预报、AQI、灾害预警等字段完整，2025-03-01 起每月提供 5 万次“天气和环境”类免费请求，超出后按量计费。
- **需要分钟级降水**：再考虑 **彩云天气**。免费版面向学生和天气爱好者，免费数据以逐小时和短期预报为主，15 天及分钟级降水通常需要付费。
- **只做地点搜索和短期天气**：高德 / 百度地图的天气接口可作为补充，但预报天数较短，不适合直接实现 15 天页面。
- **不要接入一加天气的 HeyTap 页面接口**。那是厂商内部 H5 页面，不是公开 API，详见下一节。

## 2. 关于一加天气的“15 天天气预报”页面

一加天气点击“15 天天气预报”后，会在浏览器中打开 `news.heytapdownload.com` 下的一个 H5 页面，例如：

```text
https://news.heytapdownload.com/weather/indexnew.do?language=zh-cn&unit=c&country=CN&city_id=00389540121531&template_rule=heytap&openSource=15day_weather...
```

其中：

- `news.heytapdownload.com` 是 OPPO / 一加（欢太）体系内的服务域名；
- `city_id=00389540121531` 是厂商内部的城市标识，不是公开的城市编码；
- `template_rule=heytap`、`openSource=15day_weather` 是内部页面参数；
- 页面使用 hash 路由（`#/indexnew`），本质上是一个前端页面，数据来自其内部接口。

**结论：这不是免费开放 API。** 它没有公开文档、调用配额和稳定性承诺，接口可能带有签名、鉴权或随时变更，也不适合放进公开仓库的 App 中作为长期数据源。原生天气页跳转浏览器，只能说明厂商把长预报交给了 H5，并不代表存在可用的开放接口。

## 3. 候选平台对比

| 平台 | 免费额度 | 15 天预报 | 是否需要 Key | 适用场景 |
| --- | --- | --- | --- | --- |
| Open-Meteo | 非商业用途 10,000 次/天（600 次/分、5,000 次/小时）；无 SLA | 支持，最长 16 天 | 不需要 | 个人开发、开源 Demo、快速验证 |
| 和风天气 QWeather | 每月 50,000 次“天气和环境”类请求（2025-03-01 起） | 支持 | 需要 | 国内产品、中文界面、需要 AQI / 预警 |
| 彩云天气 | 免费版面向学生 / 爱好者，页面标注 10,000 次额度（周期以官网为准） | 免费版以短期预报为主，15 天需更高套餐 | 需要 | 分钟级降水、国内短临预报 |
| 高德地图天气 | 有免费配额 | 约 4 天 | 需要，且需要签名 | 地点搜索、逆地理、短期天气 |
| 百度地图天气 | 有免费配额 | 较短 | 需要 | 国内地点与基础天气 |
| OpenWeatherMap / WeatherAPI | 有免费档 | 免费档通常只有 3–8 天 | 需要 | 海外项目、非 15 天需求 |

> 表中的“15 天”指平台是否提供对应预报接口，不代表免费额度一定能覆盖全部数据。具体以各平台控制台的套餐和账单为准。

## 4. 平台说明

### 4.1 Open-Meteo

**优点**

- 无需注册、无需 API Key，直接发 HTTP 请求即可；
- 日预报最长 16 天，满足 15 天页面；
- 逐小时数据包含温度、体感温度、湿度、能见度、天气代码、风速风向、降水概率等；
- 空气质量有独立的 Air Quality API，可获取 PM2.5、欧洲 AQI、美国 AQI；
- 免费额度足够个人开发和日常演示。

**限制**

- 免费 API **仅限非商业用途**，商业使用需要购买付费服务；
- 免费档不提供可用性保障（no uptime guarantee）；
- 数据许可为 CC BY 4.0，需要署名，例如在“关于”页面或 README 中注明 `Weather data by Open-Meteo.com`；
- 天气现象返回 WMO 天气代码，需要自行映射成中文文案和图标；
- AQI 提供的是欧洲 / 美国标准，不是中国 AQI 标准；若要显示“优 / 良 / 轻度污染”等中文分级，需要自行按中国标准换算，或改用和风天气。

**日预报 + 逐小时请求示例**

以下坐标以大连市甘井子区附近为例，实际接入时应由定位或城市搜索得到经纬度：

```text
https://api.open-meteo.com/v1/forecast
  ?latitude=38.95
  &longitude=121.58
  &daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,precipitation_probability_max,wind_speed_10m_max,wind_direction_10m_dominant
  &hourly=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,weather_code,visibility,wind_speed_10m,wind_direction_10m
  &timezone=Asia%2FShanghai
  &forecast_days=15
  &wind_speed_unit=kmh
```

> 实际请求时请去掉换行，或将参数正确编码。

**空气质量请求示例**

```text
https://air-quality-api.open-meteo.com/v1/air-quality
  ?latitude=38.95
  &longitude=121.58
  &hourly=pm2_5,us_aqi,european_aqi
  &timezone=Asia%2FShanghai
  &forecast_days=5
```

### 4.2 和风天气 QWeather

**优点**

- 国内数据源，中文天气现象、风向、空气质量等级等字段可直接使用；
- 提供 15 天预报、逐小时预报、实况、空气质量、灾害预警、生活指数等接口；
- 免费额度覆盖“天气和环境”类 API，适合开发阶段；
- 字段和本项目要复刻的国内天气 App 界面比较接近。

**限制**

- 需要注册账号并申请 Key；
- 新版接口使用控制台分配的 API Host，不要硬编码旧版公共域名；
- 免费额度用完后按量计费；
- 商业使用需要遵守其服务条款，不能把免费额度当作无限量使用；
- Key 放在 Android APK 中可能被提取。开源项目不要把真实 Key 提交到仓库；正式分发建议做服务端代理，或让用户填写自己的 Key。

**常用接口（v7）**

| 数据 | 接口 |
| --- | --- |
| 15 天预报 | `/v7/weather/15d?location={LocationID}` |
| 24 小时逐小时预报 | `/v7/weather/24h?location={LocationID}` |
| 实况天气 | `/v7/weather/now?location={LocationID}` |
| 空气质量 | `/v7/air/now?location={LocationID}` |

> 具体路径、参数和返回字段以和风天气开发文档为准。

### 4.3 彩云天气

- 免费版面向学生和天气爱好者，需注册并申请 Key；
- 优势是分钟级降水临近预报，适合做“未来两小时是否下雨”的功能；
- 免费版以逐小时和短期日预报为主，15 天预报和分钟级降水一般需要更高套餐；
- 如果本项目只需要 15 天页面和基础空气质量，彩云的免费档不是最优选择。

### 4.4 高德 / 百度地图天气

- 高德天气查询需要 Web 服务 Key，并按平台要求配置签名；
- 适合按 `adcode` 查询实况和短期预报，也方便配合地点搜索、逆地理编码使用；
- 预报天数较短（高德约 4 天），无法单独支撑 15 天预报页面；
- 百度的免费额度和预报天数同样有限，选型前请核对最新文档。

### 4.5 OpenWeatherMap / WeatherAPI

- OpenWeatherMap 免费档主要是当前天气和 5 天 / 3 小时预报，One Call 3.0 的 8 天预报有独立订阅和额度限制；
- WeatherAPI 免费档通常只有 3 天预报；
- 两者在中文天气现象和国内 AQI 分级上不如和风天气直接，不建议仅为 15 天需求接入。

## 5. 与本项目 UI 的字段对应

以参考截图中的信息结构为例：

| 界面信息 | Open-Meteo | 和风天气 |
| --- | --- | --- |
| 当前温度 | `current=temperature_2m` | `/v7/weather/now` 的 `temp` |
| 今日最高 / 最低温 | `daily=temperature_2m_max,temperature_2m_min` | `15d` 的 `tempMax` / `tempMin` |
| 未来 6 天天气 | `daily` 的 `weather_code`（展示层按白天逐小时聚合） | `15d` 的 `textDay` / `iconDay` |
| 15 天预报 | `forecast_days=16`（今天 + 未来 15 天） | `/v7/weather/15d` |
| 逐小时温度 | `hourly=temperature_2m` | `/v7/weather/24h` 的 `temp` |
| 体感温度 | `current` / `hourly` 的 `apparent_temperature` | `now` / `24h` 的 `feelsLike` |
| 湿度 | `hourly=relative_humidity_2m` | `now` / `24h` / `15d` 的 `humidity` |
| 能见度 | `hourly=visibility` | `now` / `24h` / `15d` 的 `vis` |
| 风向 / 风速 | `wind_direction_10m` / `wind_speed_10m` | `windDir` / `windSpeed` |
| 空气质量 / PM2.5 | Air Quality API 的 `us_aqi` / `european_aqi` / `pm2_5` | `/v7/air/now` 的 `aqi` / `category` / `pm2p5` |
| 天气图标 / 文案 | 需要把 WMO `weather_code` 映射为中文和图标 | 直接使用 `icon` / `text` |
| 更新时间 | `current.time` 或响应头 | 响应中的 `updateTime` |

> Open-Meteo 的 `visibility` 是逐小时变量，当前实况若需要能见度，可从当前小时对应的逐小时数据中取。

## 6. 当前接入状态

1. **数据链路已接入**：Open-Meteo 的 forecast / air-quality / geocoding 三个接口已接入；DTO 位于 `data/remote/dto/`，映射在 `data/WeatherMapper.kt`，网络访问统一收敛在 `WeatherRepository`。
2. **权限**：Manifest 已声明 `INTERNET`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`，并保留未使用的 `ACCESS_NETWORK_STATE`；定位权限仅在需要定位城市时申请，拒绝后仍可手动搜索添加城市。
3. **缓存与刷新**：内存 `reports` + DataStore `weather_cache_{cityId}`；进入页面时缓存超过 30 分钟才刷新，另支持下拉刷新与城市管理页并行刷新（并发 4）。
4. **署名与取样**：关于页与 README 已标注 Open-Meteo CC-BY 4.0；中国区前 5 天使用 CMA GRAPES、日天气按白天 08:00–20:00 逐小时聚合的规则也已注明。

## 7. 参考链接

以下链接均在 2026-10-26 核对：

- [Open-Meteo 官网](https://open-meteo.com/)
- [Open-Meteo 定价](https://open-meteo.com/en/pricing)
- [Open-Meteo 使用条款](https://open-meteo.com/en/terms)
- [Open-Meteo 开发文档](https://open-meteo.com/en/docs)
- [和风天气开发文档](https://dev.qweather.com/docs/api/)
- [和风天气定价](https://dev.qweather.com/price/)
- [和风天气 2025 年定价调整公告](https://blog.qweather.com/post/update-pricing-253/)
- [彩云天气 API 数据类型及价目表](https://caiyunapp.com/api/weather_intro.html)
- [高德开放平台天气查询](https://lbs.amap.com/api/webservice/guide/api-advanced/weatherinfo)
