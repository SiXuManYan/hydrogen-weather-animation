# 天气 API 选型记录

最初调研于 2026-10-26，当前接入说明核对于 2026-11-06。下表中的套餐与额度是历史调研值，本次未重新验证各服务商套餐，后续接入应以官网为准。

## 当前选择

项目使用 Open-Meteo 的 Forecast、Air Quality 和 Geocoding API，无需 API Key，满足非商业天气演示需求。在中国附近的粗略坐标包围盒内，额外使用 CMA GRAPES 覆盖今天起 5 天；逐日描述按城市本地自然日的小时数据聚合。具体合并、回退和展示规则统一见 [设计文档 §1](DESIGN.md#1-数据源)。

选择原因是接口开放、字段足够、最长支持今天起 16 天，且无需维护服务端凭据。限制是免费档只面向非商业用途，有调用额度且没有可用性保证；天气代码需要自行映射，不能直接得到中国 AQI 或中国气象预警。项目关于页已提供 CC BY 4.0 署名，数据传输见 [隐私政策](PRIVACY.md)。

## 候选服务比较

| 服务 | 调研时的免费条件 | 15 天能力 | 凭据 | 适用范围 |
| --- | --- | --- | --- | --- |
| Open-Meteo | 非商业用途，10,000 次/天，另有分钟 / 小时限额 | 最长 16 天，含今天 | 无需 Key | 当前项目、个人演示 |
| 和风天气 QWeather | 每月 50,000 次“天气和环境”类请求，具体接口和套餐另核对 | 提供长预报，是否免费取决于套餐 | 注册并配置认证凭据 | 需要国内天气、AQI、预警时优先评估 |
| 彩云天气 | 学生 / 爱好者免费档，额度周期以官网为准 | 长预报通常需更高套餐 | 需要 | 分钟级降水与短临预报 |
| 高德 / 百度天气 | 平台配额和认证要求各异 | 短期为主，高德约 4 天 | 需要 | 地点服务及短期天气补充 |
| OpenWeatherMap / WeatherAPI | 有免费档，长预报另看订阅 | 免费档通常不足 15 天 | 需要 | 海外项目、短预报需求 |

“提供 15 天接口”不等于免费档包含该接口。选择其他服务时，需要核对预报范围是否含今天、国内可达性、署名义务、日调用限制和商业使用条件。客户端凭据可以被提取，正式分发需按服务商支持的方式限制使用。

## 为什么不接入一加 / heytap 的天气网页

原版的长预报入口会打开 `news.heytapdownload.com` 下的页面，其中 `city_id` 是厂商内部标识。该页面及其内部数据接口没有可依赖的公开开发文档和城市标识转换接口，也没有面向本项目的服务承诺，因此当前不接入。

原天气应用使用的 `weather.1plus.io` 后端同样未接入。项目按钮改为打开应用内预报页，复用 Open-Meteo 报告，相关实现见 [设计文档 §3](DESIGN.md#3-15-天天气预报)。

## 本项目需要核对的字段

| 界面 | 当前字段 / 处理 | 更换服务时的检查点 |
| --- | --- | --- |
| 实况 | `current.temperature_2m`、天气码、湿度、风速风向、气压 | 单位、观测时间、缺失值 |
| 首页 6 天 / 详情 15 天 | 请求 `forecast_days=16`；逐日温度与全天小时聚合 | 是否包含今天、长预报是否完整 |
| 24 小时预报 | 观测时间下一整点起的 `hourly` | 城市时区、跨日、日出日落 |
| 能见度 | 当前小时 `hourly.visibility`，米换算为 km | 实况是否直接提供、单位 |
| 空气质量 | 当前 `pm2_5`，按浓度近似分档 | 当前浓度与 24 小时均值、完整 AQI 的区别 |
| 搜索 / 定位命名 | Geocoding 按名称搜索；系统 Geocoder 反查 | 中文全名匹配、逆地理服务与隐私说明 |

请求参数以 [OpenMeteoApi.kt](../app/src/main/java/com/sugar/weather/data/remote/OpenMeteoApi.kt) 为准，文档不复制整份请求字符串。

## 参考资料

- [Open-Meteo 文档](https://open-meteo.com/en/docs)、[定价](https://open-meteo.com/en/pricing)、[使用条款](https://open-meteo.com/en/terms)
- [和风天气文档](https://dev.qweather.com/docs/api/)、[定价](https://dev.qweather.com/price/)、[2025 年定价调整公告](https://blog.qweather.com/post/update-pricing-253/)
- [彩云天气 API](https://caiyunapp.com/api/weather_intro.html)
- [高德天气查询](https://lbs.amap.com/api/webservice/guide/api-advanced/weatherinfo)
