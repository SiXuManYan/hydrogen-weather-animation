# 氢 OS 一加天气致敬版 —— 数据源决策与实现设计文档

> 项目:`hydrogen-weather-animation`(包 `com.sugar.weather`)
> 基线:AGP 9.3.3 内置 Kotlin、minSdk 29 / targetSdk 37、ViewBinding 已启用
> 依据:APK 侦察报告(net.oneplus.weather v2.6.1.1 静态解包)、数据源侦察报告、5 张真机截图(2026-11-05)、本会话对 Open-Meteo / Maven Central 的实测。
> 本文档为 v1 实现的完整决策记录,实现者按此执行时**不需要再做任何选型决策**。

---

## 0. 决策速览

| # | 决策项 | 结论 |
|---|--------|------|
| D1 | 天气数据源 | **Open-Meteo**(forecast + air-quality + geocoding 三接口),本会话 curl 实测全部 200 |
| D2 | heytap 接口 | **不用**。OPPO 内部域名、无文档/SLA、cityId 无公开编码规则与转换接口,仅作附录记录 |
| D3 | weather.1plus.io | **不用**。需 Bearer 鉴权 + 硬编码 apiKey,服务存活未验证(静态解包未发请求) |
| D4 | JSON/网络 | **Retrofit 3.0.0 + converter-gson 3.0.0(Gson 反射)**,零编译器插件,兼容 AGP 9 内置 Kotlin |
| D5 | 本地存储 | **DataStore Preferences 1.3.0**,城市列表与天气缓存存 JSON 字符串;不用 Room(需 KSP) |
| D6 | 依赖注入 | **手写 AppContainer + Application**,不用 Hilt(需 KAPT/KSP,同 D5 风险) |
| D7 | 定位 | **框架 LocationManager**(不依赖 GMS),Android 10 运行时权限,失败/无权限回退手动选城 |
| D8 | 默认城市 | **不预置假城市**;首启无权限/定位失败 → 空状态引导添加城市 |
| D9 | 『15 天天气预报』 | **应用内详情页**(不做浏览器跳转)。Open-Meteo `forecast_days=16` = 今天+未来 15 天,零额外请求 |
| D10 | 多城市主页 | **ViewPager2 + 每城市一个 WeatherFragment + WeatherViewModel**,全局单实例 WeatherBackdropView |
| D11 | 告警图标/天气视频/小部件/分享/设置页 | v1 不做(无预警数据源;其余超出致敬范围) |

---

## 1. 数据源决策(D1~D3)

### 1.1 结论:Open-Meteo,三接口分工

| 接口 | 用途 | 本会话实测(2026-11-05, lat=38.907 lon=121.592) |
|------|------|------|
| `https://api.open-meteo.com/v1/forecast` | 实况 + 逐时 + 逐日 + 日出日落 | 200;`timezone=Asia/Shanghai`(auto);current 10 字段;hourly 384 点(16 天);daily 16 天;`daily.sunrise[0]=2026-11-05T06:24` |
| `https://air-quality-api.open-meteo.com/v1/air-quality` | PM2.5 浓度 | 200;`current={time:2026-11-05T01:00, pm2_5:28.5, us_aqi:114}` |
| `https://geocoding-api.open-meteo.com/v1/search` | 城市搜索 | 200;`name=大连市` → 辽宁·大连市(38.91222, 121.60222, population=7450785) |

实测命令(会话内执行):

```bash
curl "https://api.open-meteo.com/v1/forecast?latitude=38.907&longitude=121.592&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_direction_10m,pressure_msl&hourly=temperature_2m,weather_code,visibility&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,wind_speed_10m_max&timezone=auto&forecast_days=16&wind_speed_unit=kmh"
curl "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=38.907&longitude=121.592&current=pm2_5,us_aqi&timezone=auto"
curl "https://geocoding-api.open-meteo.com/v1/search?name=大连市&count=10&language=zh&format=json"
```

关键实测输出(节选):

- forecast:`current sample: {'time': '2026-11-05T01:30', 'interval': 900, 'temperature_2m': 13.5, 'relative_humidity_2m': 89, 'apparent_temperature': 12.2, 'is_day': 0, 'weather_code': 55, 'wind_speed_10m': 12.3, 'wind_direction_10m': 243, 'pressure_msl': 1017.3}`;`hourly len: 384 first: 2026-11-05T00:00 last: 2026-11-20T23:00`;`daily len: 16 dates: ['2026-11-05'..'2026-11-20']`;`hourly visibility 样本: [4980.0, 4520.0, 2280.0, ...]`(**单位为米**)。
- air-quality:`current: {'time': '2026-11-05T01:00', 'interval': 3600, 'pm2_5': 28.5, 'us_aqi': 114}`。
- geocoding:`大连` 仅命中 5 个同名村镇(福建/海南/江苏/广西/河南,population 均为 null);`大连市` 命中辽宁·大连市。**搜索策略必须应对短名陷阱**(见 §6.4)。

免费、无需 key、CC-BY 4.0(仅需在关于页署名,见 §7.5)。与数据源侦察报告结论一致:该报告实测延迟 1.28~1.56s、三域名均可达。

### 1.2 为什么不用 heytap 接口

数据源侦察报告已实测 `https://i.browser.heytapmobi.com/weather/{dailyForecast,hourlyForecast,current,airQuality}/data?cityId=...&f=json` 裸 curl 200、连测 5 次稳定。**仍然否决**,理由按保守原则取高权重项:

1. **域名归属**:OPPO 内部网关(`Server: nginx + X-Gateway-Host`),非公开 API,无文档无 SLA;
2. **cityId 黑盒**:`00389060121585` 这类 id 仅在 OPPO 体系内有效,**没有公开的经纬度↔cityId 转换接口**(报告第 5 条明说)——我们无法为自己的城市列表生成 id,除非把每个城市硬编码,不可持续;
3. **获取方式靠逆向**:接口地址只能从页面 SSR HTML 的 `weatherConfig.apiMap` 反推,官方随时改版、加鉴权或下线;
4. **氢 OS 已停止维护**,该页面服务的生命周期完全不受我们控制。

**处理方式**:不写代码、不做兜底。本文档附录 A 保留侦察报告的全部 endpoint 备查,若未来 Open-Meteo 不可用再评估。

### 1.3 为什么不用 weather.1plus.io(原版一加天气接口)

APK 侦察报告明确:该服务需先 `POST /v1/auth` 用硬编码 apiKey 换 Bearer token;全部结论来自静态解包、**未发任何网络请求,服务是否存活未验证**。把致敬版绑在一个 2020 年 App 的后端上风险不可控。否决。

### 1.4 备选与切换成本(侦察报告 fallbackSource)

- met.no Locationforecast 2.0(免费,要求 User-Agent 登记真实标识,限低频);
- 和风天气免费开发层(需注册取 key,每日约 1000 次)。

**架构保证**:所有网络访问收敛到 `WeatherRepository` 一个类内(§9),DTO 只存在于 `data/remote/dto/`;切换数据源 = 新增一个 DTO 包 + 修改 Mapper,UI 与 ViewModel 零改动。

---

## 2. 技术选型(D4~D6)

### 2.1 JSON 库与网络层:Retrofit + Gson(免编译器插件)

**决策**:Retrofit 3.0.0 + converter-gson 3.0.0 + Gson 2.13.1(反射解析)。

**理由(均有本会话证据)**:

1. **AGP 9 内置 Kotlin 不支持 `org.jetbrains.kotlin.plugin.*` 编译器子插件**。官方迁移指南([developer.android.com/build/migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin))与 AGP 9.0 发布说明确认内置 Kotlin 运行时捆绑 KGP 2.2.10;kotlinx-serialization 必须应用 `org.jetbrains.kotlin.plugin.serialization`,在内置 Kotlin 下有兼容缺口(社区同样报告 parcelize 等子插件缺失)。本会话 WebSearch 证实两条 workaround(`android.builtInKotlin=false` 退回 KGP,或换新版本插件)都会改动现有构建配置——项目当前 `app/build.gradle.kts:1-3` 只应用 `com.android.application` 一个插件,`build.gradle.kts` 顶层亦无 Kotlin 插件,**打破这个状态不划算**。
2. **Retrofit/Gson 是纯运行时依赖,零插件**。Maven Central 实查:retrofit 3.0.0 POM 依赖 `okhttp 4.12.0` + `kotlin-stdlib 2.1.21`;converter-gson 3.0.0 POM 依赖 `gson 2.13.1`。与内置 Kotlin 的 2.2.x 运行时兼容(stdlib 向后兼容)。
3. 与原版一致:APK 侦察报告显示原版用的就是 Gson 适配器(LocalSource '1'→TwcWeather / '2'→MojiWeather)。

**替代方案(记录,不采用)**:

- Moshi 反射版(KotlinJsonAdapterFactory):同样免插件,但多引入一个库,收益(kotlin 类型安全反射解析)对 8 个数据类规模不显著;
- kotlinx-serialization:若未来 AGP 内置 Kotlin 官方支持子插件,可作为首选重构——DTO 全在 `data/remote/dto/` 包内,届时替换成本可控。

**注意**:不使用 `kotlin-reflect`(Gson 反射不需要)、不用 Moshi codegen / kotlinx-serialization,即**不引入任何 KSP/KAPT 注解处理器**。

### 2.2 本地存储:DataStore Preferences(D5)

**决策**:`androidx.datastore:datastore-preferences:1.3.0`(Google Maven 实查最新稳定版,maven-metadata 尾部 `1.3.0-rc01 → 1.3.0`)。

不使用 Room 的理由:Room 需要 KSP 注解处理器,而 KSP 在 AGP 9 内置 Kotlin 下的兼容性与 serialization 插件同源未验证(§2.1 第 1 条);本项目持久化需求只有"城市列表(≤ 十几个对象)"与"每城市最近一次天气",JSON 字符串足够。

**键位设计**(统一在 `data/CityStore.kt` 中定义常量):

| Preferences Key | 值 | 说明 |
|---|---|---|
| `city_list_v1` | `String`(JSON 数组) | `List<City>` 经 Gson 序列化;读失败/为空 → 返回空列表并重建默认空态 |
| `weather_cache_{cityId}` | `String`(JSON) | `CachedWeather(report, fetchedAtMillis)` 序列化;离线时读它渲染上次数据 |

数据类(见 §8.2 `data/model/City.kt`):`City(id: String, name: String, latitude: Double, longitude: Double, isLocated: Boolean, displayOrder: Int, addedAtMillis: Long)`,`id` 用 `UUID.randomUUID().toString()` 生成一次并持久化。

**读写纪律**:所有 DataStore 读写都封装在 `CityStore` 内,suspend/Flow 形式;ViewModel 与 View 不得直接触碰 DataStore。

### 2.3 依赖注入:手写 AppContainer(D6)

不用 Hilt:需要 KAPT/KSP,同 §2.2 的风险;本项目依赖图只有 6 个单例,手写容器最保守。

```kotlin
// di/AppContainer.kt
class AppContainer(context: Context) {
    val cityStore: CityStore
    val openMeteoApi: OpenMeteoApi          // forecast
    val airQualityApi: OpenMeteoApi         // air-quality 域名,同一接口类不同 baseUrl
    val geocodingApi: OpenMeteoApi          // geocoding 域名
    val weatherRepository: WeatherRepository
    val locationDataSource: LocationDataSource
}
// WeatherApplication : Application() { lateinit var container: AppContainer }
```

`WeatherApplication` 需在 `AndroidManifest.xml` 注册 `android:name=".WeatherApplication"`。

### 2.4 定位:框架 LocationManager(D7、D8)

**决策**:`android.location.LocationManager`,**不引入 GMS / play-services-location**。理由(ask 明示 + APK 报告第 6 条):原版 App 核心定位走高德 SDK,主功能理论上不依赖 GMS;致敬版目标机型是"老一加国产机",可能没有 Google 服务。框架 LocationManager 在任何 Android 10+ 设备都可用。

**实现要点**:

- Provider 策略:`getCurrentLocation` 不可用时(Android 10 支持 `LocationManager.getCurrentLocation(Locale, ...)`,API 30+;**minSdk 29 上不可用**)→ 用 `getLastKnownLocation(NETWORK|GPS)` 立即取值 + `requestLocationUpdates` 监听首个满足精度阈值的修复后注销(一次性定位,不常驻)。
- 超时:10 秒;超时未得修复 → 视为失败。
- 运行时权限:`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` 同时声明于 Manifest;请求用 `ActivityResultContracts.RequestMultiplePermissions`。用户拒绝 fine 但同意 coarse → 用 coarse 结果;全拒绝 → 回退。
- **回退链**(决策 D8):
  1. 首启无权限/定位失败/超时 → **不预置任何默认城市**(错误的城市比空状态更误导),主页面显示空状态:居中图钉图标 + "添加城市,查看天气" + "添加城市"按钮 → 跳搜索页;
  2. 已有城市列表时,定位成功 → 插入/更新 `isLocated=true` 的城市(若已存在则更新坐标并保持 displayOrder);
  3. 定位城市命名:Open-Meteo **没有逆地理接口**(geocoding API 仅支持 name 搜索);用框架 `android.location.Geocoder`(非 GMS,`Geocoder.isPresent()` 检查),取 `locality ?: subAdminArea ?: adminArea`;Geocoder 失败 → 显示 **"我的位置"**;
  4. 定位城市固定 displayOrder=0 位置(截图 005350 中定位卡带图钉在列表首位),不可删除、不参与拖拽换位。

---

## 3. 『15 天天气预报』按钮(D9)

**APK 侦察结论**:原版按钮打开的是天气响应里的 `dailyForecasts[0].mobileLink` 字段(`getExtendedForecastUrl`),URL 不在 APK 内,由 weather.1plus.io 服务端签发;且 APK 里**没有**城市名→city_id 的公开转换接口(1plus 用服务端签发 location key;heytap cityId 无公开转换规则)。→ 按 ask 的条件分支走 **"否则"分支**。

**决策:应用内 15 日预报详情页,替代浏览器跳转**。

- 数据:主页面同一份 `WeatherReport.daily`(16 条 = 今天 + 未来 15 天,`forecast_days=16` 实测确认),详情页取 `daily[1..15]`(明天起 15 天),**零额外网络请求**;
- 入口按钮保留在原版位置(温度曲线与逐小时条之间,截图 005332),描边胶囊样式;
- 文案仍为「15 天天气预报」;
- **失败回退**:详情页打开时无该城市缓存(极端情况:主页有数据但被清理)→ `WeatherRepository.refreshWeather(cityId)` 现取;网络也失败 → 页内显示"加载失败,请检查网络"+ 重试按钮。不存在"跳浏览器失败"的场景,因为不再跳浏览器。

---

## 4. WMO weather_code 完整映射表(f)

Open-Meteo 的 `weather_code` 是 WMO 标准码(0–99)。映射到现有 `scene/SceneType.kt:9-28` 的枚举(该枚举 15 个值全部复用,**不修改**)。表中「场景」列即 `SceneType` 名,背景色取自该枚举的 `dayBackground/nightBackground`(与 APK 侦察报告的 `weather_*` 调色板一致,如晴昼 `#FF4A97D2`)。

| code | 中文名(昼) | 中文名(夜) | SceneType | 备注 |
|------|------------|------------|-----------|------|
| 0 | 晴 | 晴 | SUNNY | 夜间图标用月亮 |
| 1 | 晴间少云 | 晴间少云 | SUNNY | mostly clear,归晴 |
| 2 | 多云 | 多云 | CLOUDY | partly cloudy |
| 3 | 阴 | 阴 | OVERCAST | overcast |
| 45 | 雾 | 雾 | FOG | |
| 48 | 雾凇 | 雾凇 | FOG | depositing rime fog |
| 51 | 毛毛雨(轻) | 毛毛雨(轻) | LIGHT_RAIN | LIGHT_RAIN 日背景 `#FF1899B9` 正是原版「毛毛雨」色 |
| 53 | 毛毛雨 | 毛毛雨 | LIGHT_RAIN | |
| 55 | 毛毛雨(浓) | 毛毛雨(浓) | LIGHT_RAIN | 实测 current code=55 |
| 56 | 冻毛毛雨(轻) | 冻毛毛雨(轻) | LIGHT_RAIN | freezing drizzle |
| 57 | 冻毛毛雨(浓) | 冻毛毛雨(浓) | LIGHT_RAIN | |
| 61 | 小雨 | 小雨 | LIGHT_RAIN | |
| 63 | 中雨 | 中雨 | RAIN | |
| 65 | 大雨 | 大雨 | HEAVY_RAIN | |
| 66 | 冻雨(轻) | 冻雨(轻) | RAIN | freezing rain |
| 67 | 冻雨(强) | 冻雨(强) | RAIN | |
| 71 | 小雪 | 小雪 | LIGHT_SNOW | |
| 73 | 中雪 | 中雪 | SNOW | |
| 75 | 大雪 | 大雪 | SNOW | |
| 77 | 雪粒 | 雪粒 | LIGHT_SNOW | snow grains |
| 80 | 阵雨(弱) | 阵雨(弱) | RAIN | rain showers |
| 81 | 阵雨 | 阵雨 | RAIN | |
| 82 | 强阵雨 | 强阵雨 | HEAVY_RAIN | violent showers |
| 85 | 阵雪(弱) | 阵雪(弱) | LIGHT_SNOW | snow showers |
| 86 | 阵雪(强) | 阵雪(强) | SNOW | |
| 95 | 雷阵雨 | 雷阵雨 | THUNDER | 实测 daily[0] code=95(雷暴,与截图当天天气吻合) |
| 96 | 雷阵雨伴冰雹(弱) | 雷阵雨伴冰雹(弱) | HAIL | thunderstorm + hail |
| 99 | 雷阵雨伴冰雹(强) | 雷阵雨伴冰雹(强) | HAIL | |

**昼夜判定**:仅实况用 `current.is_day == 0`(实测字段存在,取值 0/1)。逐时/逐日条目图标一律昼间样式。SLEET(雨夹雪)不在 WMO 码表内,WMO 无对应码 → **该场景仅由调试入口可达**(见下)。

**霾 / 扬沙 / 浮尘的诚实说明**:WMO weather_code **不包含**霾(需气溶胶观测)与沙尘码段;Open-Meteo forecast 响应没有逐时 dust/aerosol 字段,air-quality API 虽有 `dust` 变量但官方无"扬沙/浮尘"阈值语义。因此 **HAZE、SANDSTORM 两个场景 v1 不做自动映射**,仅通过保留的调试面板手动进入(§7.6);映射函数对未知 code 一律回退 `CLOUDY`,不臆测。

---

## 5. 展示映射表(g)

### 5.1 空气质量等级(依据中国 HJ 633-2012,PM2.5 24 小时平均浓度限值,单位 μg/m³)

| PM2.5 浓度 x | 等级文字(截图样式) |
|---|---|
| 0 ≤ x ≤ 35 | 优 |
| 35 < x ≤ 75 | 良 |
| 75 < x ≤ 115 | 轻度污染 |
| 115 < x ≤ 150 | 中度污染 |
| 150 < x ≤ 250 | 重度污染 |
| x > 250 | 严重污染 |

- 截图 005344 只显示等级文字(「良」)与 PM2.5 数值(「19.0」),不显示 AQI 数字 → **v1 不展示 `us_aqi`**(接口已实测可用,字段保留在 DTO 备用,不进 UI);
- PM2.5 显示 1 位小数 + 无单位后缀(与截图一致),标签「PM2.5」;
- 空气质量卡的等级文字直接由上表得出。

### 5.2 紫外线指数(来源 `daily.uv_index_max[0]`,即今天的值)

| uv_index_max | 文字 |
|---|---|
| 0 ≤ x ≤ 2 | 最弱 |
| 3 ≤ x ≤ 4 | 弱 |
| 5 ≤ x ≤ 6 | 中等 |
| 7 ≤ x ≤ 9 | 强 |
| x ≥ 10 | 很强 |

实测样本:today `uv_index_max=3.4` → 显示「弱」。null(夜间/极地)→ 显示「—」。

### 5.3 风向 16 方位(来源 `current.wind_direction_10m`,度)

公式:`idx = round(deg / 22.5) mod 16`(deg 归一化到 [0,360))。

| idx | 文字 | idx | 文字 |
|---|---|---|---|
| 0 | 北风 | 8 | 南风 |
| 1 | 东北偏北风 | 9 | 西南偏南风 |
| 2 | 东北风 | 10 | 西南风 |
| 3 | 东北偏东风 | 11 | 西南偏西风 |
| 4 | 东风 | 12 | 西风 |
| 5 | 东南偏东风 | 13 | 西北偏西风 |
| 6 | 东南风 | 14 | 西北风 |
| 7 | 东南偏南风 | 15 | 西北偏北风 |

`wind_speed_10m < 0.5 km/h` 时风向显示「静风」。方向 + 数值拼合:`{方位}风`,值 `{%.1f} km/h`(截图 005344:「西南风 / 39.0 km/h」)。

### 5.4 其余数值格式(全部对齐截图 005344)

| 项 | 来源 | 格式 | 截图示例 |
|---|---|---|---|
| 当前温度 | `current.temperature_2m` | 四舍五入整数 + `°` | 14° |
| 高/低温 | `daily.temperature_2m_max/min[0]` | `{max}/{min}°C`,各四舍五入整数 | 16/3°C |
| 体感温度 | `current.apparent_temperature` | 四舍五入整数 + `°` | 8° |
| 湿度 | `current.relative_humidity_2m` | 1 位小数 + ` %` | 84.0 % |
| 能见度 | **当前小时**的 `hourly.visibility`(米)/1000 | 1 位小数 + ` km` | 8.0 km |
| 气压 | `current.pressure_msl` | 四舍五入整数 + ` hPa` | 1007 hPa |
| 逐时温度 | `hourly.temperature_2m` | 四舍五入整数 + `°` | 14° |

单位固定公制;`wind_speed_unit=kmh` 请求参数(实测生效)。

---

## 6. 页面结构与导航(h)

### 6.1 总览

| 页面 | 载体 | 对应原版 | 截图基准 |
|---|---|---|---|
| 主页面(多城市) | `MainActivity` + `ViewPager2` + 每城市 `WeatherFragment` | MainActivity(ViewPager + GLSurfaceView) | 005322 / 005332 / 005344 |
| 城市管理 | `CityManageActivity` | CityListActivity | 005350 |
| 搜索添加 | `CitySearchActivity` | CitySearchActivity | — |
| 15 天预报 | `FifteenDayActivity` | 外跳 mobileLink 网页 | — |
| 关于 | `AboutActivity` | AboutActivity | 005425 |

导航:主页溢出菜单 → 关于 / 动画调试;顶部城市管理图标 → CityManageActivity(选择城市回传 cityId → 主页切页);FAB「+」→ CitySearchActivity(添加成功 → 返回主页并切到新城市);主页「15 天天气预报」→ FifteenDayActivity。

### 6.2 主页面(D10 方案,已定)

**结构**:`MainActivity` 保留全屏 `WeatherBackdropView`(现状,`activity_main.xml:10-13`),其上放透明背景的 `ViewPager2`,再上是头部 overlay(替换现有调试覆盖层,调试功能移入溢出菜单,见 §7.6)。

```
FrameLayout(root)
 ├ WeatherBackdropView            ← 现有,单实例,永远只渲染当前选中城市
 ├ ViewPager2(pages = 每城市一个 WeatherFragment,页面背景透明)
 └ 头部 overlay LinearLayout(系统栏内边距,沿用现有 insets 逻辑 MainActivity.kt:58-69)
    ├ 图钉 icon + [城市名 TextView / 「刚刚更新」 TextView]
    └ [告警 icon(GONE,见 §6.7)] [城市管理 icon] [溢出 ⋮ → 关于 / 动画调试]
```

**为什么背景不放进每页 Fragment**(备选方案弃用理由):N 个页面各自跑 60fps Canvas 循环,滑页时两个背景同帧渲染、切换瞬间割裂;原版就是**单个 GLSurfaceView 共享背景**(APK 报告 uiNotes),单实例方案一致且省电。

**数据流**:每页 `WeatherViewModel(cityId)` 从 `WeatherRepository.reports: StateFlow<Map<String, WeatherReport>>` 取本城市数据;`MainViewModel` 组合 `cities + currentPageIndex + reports` 产出 `BackdropState(sceneType, isNight)` → `weatherBackdrop.setScene(type, night)`(现有 API,`WeatherBackdropView.kt:76-87`,零修改)。`onPageSelected` → `MainViewModel.selectPage(index)`。

**单页内部布局**(`fragment_weather.xml`,SwipeRefreshLayout → NestedScrollView → 纵向 LinearLayout,全部背景透明,文字白色):

1. 顶部留白(避开 Activity 头部 overlay;对齐截图 005322,温度块前保留约 **0.5 屏高**的纯背景动画留白——14° 位于屏幕约 55-60% 处。由 `WeatherFragment` 按 `0.5 × 屏高` 设置 Spacer,修订前文档规定的固定 72dp 与截图不符,已废弃);
2. **当前温度块**:`14°` sans-serif-light 88sp 白;下一行 `小雨 16/3°C >`(描述 20sp + 高低温 20sp 次要色 + `>`);
3. **6 天日报条 + 温度曲线**:自绘 `WeatherTrendView`(单 View 整块绘制,见 §6.3);
4. **『15 天天气预报』按钮**:MaterialButton outlined 样式,胶囊圆角 20dp,描边 `#89FFFFFF`,文字白色 14sp,水平居中;
5. **逐小时条**:`HorizontalScrollView` + 内部 `LinearLayout`,每列宽 64dp:时间(01:00)/图标(36dp)/温度;**日出日落特殊节点**——从 `daily[0].sunrise/sunset` 生成 `{time:"06:25", icon:日出}` 插入到第一个晚于该时刻的时辰前(截图 005332 最后一列即 06:25 日出图标);共 24 时列 + ≤2 个日节点;起点 = 当前整点的下一小时(实测 `current.time=01:30` 且截图 00:53 首列为 01:00,即"下一整点"规则);
6. **两列详情卡**:`GridLayout(2 列)`,8 个 `WeatherSingleInfoView`:空气质量(良)、PM2.5(19.0)、西南风(39.0 km/h)、体感温度(8°)、湿度(84.0 %)、能见度(8.0 km)、紫外线(最弱)、气压(1007 hPa)。行间分割线 `#1AFFFFFF` 1dp(截图可见浅分割)。

**下拉刷新**:SwipeRefreshLayout 包整页,触发 `WeatherViewModel.refresh()`(天气 + 空气质量两个请求);无网络 → 顶部更新时间变「X 分钟前更新」+ Toast「更新失败」。

### 6.3 WeatherTrendView(自绘,新建)

一个自定义 View 完成截图 005332 的整块内容(6 列日头 + 双温度曲线),**不用 RecyclerView**——列与曲线必须逐像素对齐,单 View 内部统一坐标最稳(原版 HourForecastView 等也是自绘)。

- 输入:`List<DailyForecast>` 前 6 条(`daily[0..5]`,即今天+5 天,截图 005322:今天 11月5日 ~ 周二 11月10日);
- 绘制顺序:每列——星期(今天/周五/…,今天列固定「今天」)+ 日期(M月d日)→ 昼间天气图标(`Canvas` 矢量绘制或 drawable,§6.6)→ 天气文字;下方——高温折线(圆点 + 点上方数值°)+ 低温折线(圆点 + 点下方数值°)+ 两线之间垂直渐变填充带(`LinearGradient`,上 `#40FFFFFF` → 下 `#08FFFFFF`);
- Y 轴:6 天中 (maxMax, minMin) 归一化,曲线区高度 120dp,平滑用二阶贝塞尔(相邻点中点连线,CubicTo);
- 列宽 = width/6,等分;
- 尺寸:`onMeasure` 固定高度 280dp + 图标区,宽度 match_parent;
- 颜色全部取自 `SceneType.background(isNight)` 同系白色/次要白,不新增调色板。

### 6.4 城市搜索页(短名陷阱对策,实测驱动)

- 顶部搜索框(白底圆角、hint `#89FFFFFF`、输入 `#FFFFFFFF`,APK uiNotes 配色)+ 热门城市 tag 区(`ChipGroup`,tag 背景 `#FFEBEBEB`、圆角 20dp、ripple `#29000000`,APK uiNotes 实测值);
- **热门城市为内置常量表**(不走搜索):北京/上海/广州/深圳/成都/杭州/武汉/西安/沈阳/大连/哈尔滨/乌鲁木齐 + 城市级经纬度。理由:实测 `name=大连` 只返回 5 个同名村镇,**短中文名不可靠**;热门城市坐标是常识级数据,硬编码 12 条最稳;
- 文本搜索:TextWatcher 300ms 防抖 + `geocodingApi.searchCity(name=query, count=10, language="zh")`;
- **结果排序策略(本会话实测驱动)**:`population` 非空且大的排前(实测 大连市 population=7450785,同名村镇为 null),同 population 按 name 长度升序;列表行显示 `name · admin1 · country`;
- 搜索框 hint 文案引导:「输入城市全名,如 大连市」;
- 点击结果 → `repository.addCity(City(...))` → finish → 主页切到新城市。

### 6.5 城市管理页

- 深色 Toolbar「选择城市」+ 浅灰页面底(截图 005350:工具栏 `#FF1E1F22` 近似取样、页面 `#FFF2F3F5` 近似取样);
- `RecyclerView` 垂直列表,每项 = 全宽圆角 `CardView`(radius 24dp,高 160dp,左右边距 16dp,卡间距 12dp):
  - 背景:`WeatherCardBackdropView`(新,§6.6)以该城市 `sceneType.createScene()` 渲染小动画(截图可见雨丝卡/夜空月亮卡),帧率限 24fps、粒子密度按卡片面积缩放(系数 0.35),`onPause` 全部暂停;
  - 文字:城市名(带图钉 if `isLocated`)+ 第二行 `{temp}°{desc}`(如「14°小雨」);文字白色,取自该城市天气缓存;
- 交互:`ItemTouchHelper` 长按拖拽排序(写回 `displayOrder`);左滑删除(定位城市除外,§2.4 第 4 条);点击卡片 → `setResult(cityId)` 返回主页切页;
- 右下角白色圆形 FAB「+」(截图 005350:白底黑 + 号,56dp)→ 搜索页;
- 打开页面时 `CityManageViewModel` 并行刷新全部城市天气(并发限 4,`Semaphore`),卡片文字取 `reports` 缓存。

### 6.6 图标策略

全部线条风格白色矢量(截图 005322/005332 的描边图标观感),`res/drawable/` 手写 vector XML,清单:

`ic_location_pin, ic_alert, ic_city_manage, ic_more_vert, ic_back, ic_add, ic_search, ic_sunrise, ic_sunset, ic_sun, ic_moon, ic_partly_cloudy, ic_cloudy, ic_overcast, ic_fog, ic_drizzle, ic_rain, ic_heavy_rain, ic_thunder, ic_snow, ic_hail, ic_sleet`

`ui/weather/WeatherIcons.kt` 提供 `fun iconRes(code: Int, night: Boolean): Int`(按 §4 表),夜间且 code∈{0,1} 返回月亮图标。逐日/逐时一律昼间图标。

### 6.7 原版有而 v1 不做的功能(明示)

| 功能 | 不做原因 |
|---|---|
| 告警(WeatherWarningActivity,头部 ! 图标) | Open-Meteo 无中国官方预警接口;头部图标 v1 隐藏(GONE),布局留位 |
| 天气视频(weather_video_layout) | 超出致敬范围 |
| 桌面小部件 / 分享 / 反馈 / 设置页 | v1 范围外;单位固定公制 |
| 暗码调试页(MockLocation/SecretCodeReceiver) | APK 报告第 7 条:调试功能,可不复刻 |
| 后台定时刷新(AlarmUpdateJob/BootReceiver) | v1 仅前台刷新(进入页面/下拉),避免 WorkManager 引入 |

---

## 7. 关于页

白底(`#FFF7F7F7`)居中:云朵 logo(自绘 vector)+「天气 V1.0」(取 `strings.xml` 的 `app_name` 与 `BuildConfig` 不可用时硬编码版本常量,见 §2.4 AGP 9 默认关闭 buildConfig,直接在 `AppContainer` 放 `APP_VERSION = "1.0"`)+ 更新日志文本 + 底部「用户协议 / 隐私政策」链接 + **「数据来源:Open-Meteo(CC-BY 4.0)」署名行(合规必须)**。静态页面,无 ViewModel,无状态。

---

## 8. MVVM 纪律与各页 UiState(i)

### 8.1 分层纪律(与现有代码风格一致,`MainViewModel.kt:15-22` 已示范)

1. **View(Activity/Fragment/自定义 View)**:只做 ViewBinding、 insets、跳转、把用户输入提交给 ViewModel;不持有任何业务对象,不 import `data.remote.*`;
2. **ViewModel**:持有 `StateFlow<UiState>`;状态变更只经 ViewModel;不持有 Activity/View 引用;
3. **Repository**:只暴露 `suspend` 函数与 `Flow`;内部可调 Retrofit/DataStore/LocationManager;
4. **数据层(`data/`)**:禁止 import 任何 `androidx.*` UI 类与 `ui.*` 类;允许 `android.location`、`android.content.Context`(系统 API 不算 UI);
5. 高频数据(传感器 Tilt)维持现状的独立 StateFlow,不并入 UiState(`MainActivity.kt:114-129` 现有模式)。

### 8.2 各 ViewModel 的 UiState 字段清单

**WeatherViewModel(每城市页)** → `StateFlow<WeatherUiState>`:

```kotlin
data class WeatherUiState(
    val cityId: String,
    val cityName: String,                 // 含「我的位置」回退名
    val isLocated: Boolean,               // 图钉
    val status: WeatherStatus,            // Loading / Success / Error(无缓存且请求失败)
    val fetchedAtMillis: Long?,           // 「刚刚更新 / X 分钟前更新」
    val tempText: String?,                // 「14°」
    val conditionText: String?,           // 「小雨」
    val highLowText: String?,             // 「16/3°C」
    val trend: List<DailyForecast>?,      // WeatherTrendView 输入,daily[0..5]
    val hourly: List<HourForecast>?,      // 已裁剪:下一整点起 24h + 日出日落节点
    val detailCards: List<DetailCardUi>?, // 8 张卡,已按 §5 格式化
    val sceneType: SceneType,             // 供 MainViewModel 合成背景;默认 SUNNY
    val isNight: Boolean                  // current.is_day==0;无数据时按时钟估算
)
data class DetailCardUi(val label: String, val value: String, val unit: String)
```

**CityManageViewModel** → `StateFlow<CityManageUiState>`:

```kotlin
data class CityManageUiState(
    val cities: List<CityCardUi>,         // 已按 displayOrder 排序
    val loading: Boolean                  // 批量刷新中
)
data class CityCardUi(
    val cityId: String, val name: String, val isLocated: Boolean,
    val tempText: String?, val descText: String?,
    val sceneType: SceneType, val isNight: Boolean
)
```

**CitySearchViewModel** → `StateFlow<CitySearchUiState>`:

```kotlin
data class CitySearchUiState(
    val query: String,
    val searching: Boolean,
    val results: List<CitySearchResultUi>, // population 优先排序,§6.4
    val popular: List<PopularCity>,        // 内置 12 城
    val error: String?
)
data class CitySearchResultUi(val name: String, val admin1: String?, val country: String?, val lat: Double, val lon: Double)
```

**FifteenDayViewModel** → `StateFlow<FifteenDayUiState>`:

```kotlin
data class FifteenDayUiState(
    val cityName: String,
    val days: List<FifteenDayRowUi>,       // daily[1..15]
    val loading: Boolean, val error: Boolean
)
data class FifteenDayRowUi(
    val weekText: String, val dateText: String, val desc: String,
    val iconRes: Int, val precipProbText: String?,    // 「降水概率 35%」可空
    val tempHighText: String, val tempLowText: String
)
```

**MainViewModel(改造现有)**:保留 `sceneState`(调试面板用)与 `tilt`;新增:

```kotlin
data class CityPagesState(val cities: List<City>, val selectedIndex: Int)
data class BackdropState(val sceneType: SceneType, val isNight: Boolean)
val pages: StateFlow<CityPagesState>
val backdrop: StateFlow<BackdropState>
fun selectPage(index: Int)
fun onCityListChanged()   // 从城市管理/搜索页返回后重建页序
```

**AboutActivity**:无 ViewModel(静态页)。

---

## 9. 数据流与刷新策略

```
OpenMeteoApi(3 实例)
   │ suspend
WeatherRepository ── reports: StateFlow<Map<cityId, WeatherReport>>(内存)
   │                        │
   ├ refreshWeather(cityId): Result<WeatherReport>   // forecast+airQuality 并行,任一失败整体失败?否:
   │   // 决策:forecast 失败 → 整体失败;airQuality 失败 → 保留旧 pm25,卡片显示「—」
   ├ CityStore(DataStore)  // 持久化 cities 与 weather_cache_{cityId}
   └ searchCity(query): List<CitySearchResult>
LocationDataSource(框架)── suspend currentLocation(): LocatedCity?
```

**刷新触发**(v1 无后台任务):进入主页面缓存 > 30 分钟的城市自动刷新;下拉刷新;城市管理/搜索页返回后当前页刷新;切页到无缓存城市即刷。

**离线表现**:有缓存 → 渲染缓存 + 更新时间显示真实差值;无缓存 → `status=Error` + 重试按钮。

**时间处理**:响应内时间为城市本地时间(`timezone=auto`,实测返回 `Asia/Shanghai`);`hourly.time`/`daily.time` 为 ISO 字符串(`2026-11-05T00:00`),DTO 存原始 String,Mapper 转 `LocalDateTime`/`LocalDate`(java.time,minSdk 29 原生支持,无需 desugaring)。

---

## 10. 文件级实现清单(j)

### 10.1 gradle/libs.versions.toml 追加(AGP 9 语法,与现有 alias 风格一致)

```toml
[versions]  # 追加
retrofit = "3.0.0"
gson = "2.13.1"
okhttp = "4.12.0"
datastore = "1.3.0"
viewpager2 = "1.1.0"
recyclerview = "1.4.0"
swiperefreshlayout = "1.2.0"
constraintlayout = "2.2.2"

[libraries]  # 追加
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-viewpager2 = { group = "androidx.viewpager2", name = "viewpager2", version.ref = "viewpager2" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
androidx-swiperefreshlayout = { group = "androidx.swiperefreshlayout", name = "swiperefreshlayout", version.ref = "swiperefreshlayout" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }

# [plugins] 不新增任何条目 —— 全部依赖免编译器插件,这是 D4 的核心收益
```

`app/build.gradle.kts` dependencies 追加:

```kotlin
implementation(libs.retrofit)
implementation(libs.retrofit.converter.gson)
implementation(libs.gson)
implementation(libs.okhttp.logging.interceptor)
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.viewpager2)
implementation(libs.androidx.recyclerview)
implementation(libs.androidx.swiperefreshlayout)
implementation(libs.androidx.constraintlayout)
```

版本依据(本会话 Maven Central / Google Maven 实查):retrofit 与 converter-gson 最新稳定 3.0.0;converter-gson 3.0.0 传递 gson 2.13.1(显式声明同版防漂移);retrofit 3.0.0 传递 okhttp 4.12.0、kotlin-stdlib 2.1.21;datastore-preferences 1.3.0;viewpager2 1.1.0;recyclerview 1.4.0;swiperefreshlayout 1.2.0;constraintlayout 2.2.2。okhttp 5.x 虽已稳定(5.5.1)但 4.12 是多年生产验证版,保守取 4.12。

### 10.2 新建文件(相对 `app/src/main/java/com/sugar/weather/`)

| 文件 | 职责与关键签名 |
|---|---|
| `WeatherApplication.kt` | `class WeatherApplication : Application`;`lateinit var container: AppContainer` |
| `di/AppContainer.kt` | 手写 DI 容器:`val cityStore/openMeteoApi/airQualityApi/geocodingApi/weatherRepository/locationDataSource`、`const val APP_VERSION = "1.0"` |
| `data/model/City.kt` | `data class City(id, name, latitude, longitude, isLocated, displayOrder, addedAtMillis)` |
| `data/model/WeatherReport.kt` | `WeatherReport(cityId, fetchedAtMillis, timezone, current: CurrentWeather?, daily: List<DailyForecast>, hourly: List<HourForecast>)`;`CurrentWeather(temperatureC, apparentTemperatureC, humidityPercent, pressureHpa, windKmh, windDirectionDeg, visibilityKm: Double?, weatherCode, isNight, pm25: Double?)`;`DailyForecast(date: LocalDate, weatherCode, tempMaxC, tempMinC, precipProbabilityMaxPercent: Int?, uvIndexMax: Double?, sunrise: LocalTime, sunset: LocalTime)`;`HourForecast(time: LocalDateTime, temperatureC, weatherCode)`;`CitySearchResult(name, admin1, country, lat, lon, population: Long?)` |
| `data/remote/dto/ForecastResponse.kt` | 与 Open-Meteo 实测结构一一对应的 DTO:`ForecastResponse(latitude, longitude, timezone, current: CurrentBlock, hourly: HourlyBlock, daily: DailyBlock)`;字段名 snake_case 原样 |
| `data/remote/dto/AirQualityResponse.kt` | `AirQualityResponse(current: AirCurrentBlock)`;`AirCurrentBlock(time, pm2_5: Double?, us_aqi: Double?)` |
| `data/remote/dto/GeocodingResponse.kt` | `GeocodingResponse(results: List<GeocodingPlace>?)`;`GeocodingPlace(id, name, latitude, longitude, admin1?, country?, population?, timezone?)` |
| `data/remote/OpenMeteoApi.kt` | `interface OpenMeteoApi { @GET("v1/forecast") suspend fun forecast(@Query("latitude") lat: Double, @Query("longitude") lon: Double, @Query("current") current: String, @Query("hourly") hourly: String, @Query("daily") daily: String, @Query("timezone") tz: String = "auto", @Query("forecast_days") days: Int = 16, @Query("wind_speed_unit") unit: String = "kmh"): ForecastResponse; @GET("v1/air-quality") suspend fun airQuality(...): AirQualityResponse; @GET("v1/search") suspend fun searchCity(@Query("name") name: String, @Query("count") count: Int = 10, @Query("language") lang: String = "zh", @Query("format") f: String = "json"): GeocodingResponse }`;current/hourly/daily 参数串定义为常量(值即 §1.1 实测命令中的串) |
| `data/remote/WeatherApiClient.kt` | `object WeatherApiClient { fun openMeteo(baseUrl: String, debug: Boolean): OpenMeteoApi }`;共享 OkHttpClient(connect 10s / read 15s),`logging-interceptor` 仅 `ApplicationInfo.FLAG_DEBUGGABLE` 时 BASIC 级 |
| `data/WeatherCodes.kt` | 纯函数映射(§4/§5 全部表):`fun wmoDesc(code: Int): String`、`fun wmoScene(code: Int): SceneType`、`fun aqiLevelText(pm25: Double): String`、`fun uvLevelText(uv: Double?): String`、`fun windDirText(deg: Double?): String`;**可单测**(不依赖 Android) |
| `data/WeatherMapper.kt` | `fun ForecastResponse.toDomain(cityId: String, fetchedAt: Long, pm25: Double?): WeatherReport`、`fun GeocodingResponse.sortedResults(): List<CitySearchResult>`(population 优先) |
| `data/CityStore.kt` | DataStore 封装:`val cities: Flow<List<City>>`、`suspend fun saveCities(list: List<City>)`、`suspend fun cachedWeather(cityId: String): WeatherReport?`、`suspend fun saveWeather(cityId: String, report: WeatherReport)`;键位常量见 §2.2 |
| `data/WeatherRepository.kt` | `val reports: StateFlow<Map<String, WeatherReport>>`、`val cities: StateFlow<List<City>>`(由 CityStore.flow 转换)、`suspend fun refreshWeather(cityId: String): Result<WeatherReport>`、`suspend fun addCity(city: City)`、`suspend fun removeCity(cityId: String)`、`suspend fun reorderCities(orderedIds: List<String>)`、`suspend fun upsertLocatedCity(name: String?, lat: Double, lon: Double): City`、`suspend fun searchCity(query: String): List<CitySearchResult>` |
| `data/LocationDataSource.kt` | `fun hasPermission(): Boolean`、`suspend fun currentLocation(timeoutMs: Long = 10_000): LocationFix?`;`sealed class LocationFix { data class Fixed(lat, lon); object Denied; object Unavailable }`;getLastKnown + requestLocationUpdates 一次性策略(§2.4) |
| `ui/weather/WeatherFragment.kt` | 每城市一页:绑定 fragment_weather.xml,收集 WeatherUiState 渲染,SwipeRefresh 回调 `viewModel.refresh()` |
| `ui/weather/WeatherViewModel.kt` | `class WeatherViewModel(app: Application, private val cityId: String): AndroidViewModel`;`val uiState: StateFlow<WeatherUiState>`、`fun refresh()`;用 `viewModelFactory { initializer { ... } }` 创建(lifecycle 2.11 已有,零新增依赖) |
| `ui/weather/WeatherUiState.kt` | §8.2 数据类 |
| `ui/weather/WeatherIcons.kt` | `fun iconRes(code: Int, night: Boolean): Int`(§6.6) |
| `ui/city/CityManageActivity.kt` | RecyclerView + ItemTouchHelper(拖拽/左滑)+ FAB;`setResult(cityId)` 回传 |
| `ui/city/CityManageViewModel.kt` / `CityManageUiState.kt` | §8.2;`fun moveCity(from: Int, to: Int)`、`fun removeCity(cityId: String)` |
| `ui/city/CityCardAdapter.kt` | `ListAdapter<CityCardUi, ViewHolder>`(DiffUtil) |
| `ui/search/CitySearchActivity.kt` | 搜索框 + ChipGroup 热门城市 + 结果列表 |
| `ui/search/CitySearchViewModel.kt` / `CitySearchUiState.kt` | 300ms 防抖搜索;`fun onQueryChanged(q: String)`、`fun onPickPopular(city: PopularCity)` |
| `ui/search/SearchResultAdapter.kt` | 结果列表 adapter |
| `ui/fifteenday/FifteenDayActivity.kt` | 15 日详情页(§3),接收 `EXTRA_CITY_ID` |
| `ui/fifteenday/FifteenDayViewModel.kt` / `FifteenDayUiState.kt` / `FifteenDayAdapter.kt` | 从 Repository 缓存取 daily[1..15],缺失时补拉 |
| `ui/about/AboutActivity.kt` | 静态关于页(§7),无 ViewModel |
| `view/WeatherTrendView.kt` | §6.3;`fun setDays(days: List<DailyForecast>, night: Boolean)` |
| `view/WeatherSingleInfoView.kt` | 详情卡:label(次要色 13sp)+ value(白 22sp,unit 小号);`fun bind(card: DetailCardUi)` |
| `view/WeatherCardBackdropView.kt` | 城市卡小动画背景:内部持有 `WeatherScene`,24fps Choreographer 节流,粒子密度 0.35;`fun setScene(type: SceneType, night: Boolean)`、`fun pause()/resume()` |

新建资源:

- 布局:`res/layout/fragment_weather.xml`、`activity_city_manage.xml`、`item_city_card.xml`、`activity_city_search.xml`、`item_search_result.xml`、`activity_fifteen_day.xml`、`item_fifteen_day_row.xml`、`activity_about.xml`;
- 图标:`ic_*.xml` 22 个矢量(§6.6 清单)+ `ic_logo_cloud.xml`(关于页);
- `values/colors.xml` 追加:`text_primary_on_dark #D8FFFFFF`、`text_secondary_on_dark #89FFFFFF`、`city_manage_toolbar_bg #FF1E1F22`、`city_manage_page_bg #FFF2F3F5`、`about_page_bg #FFF7F7F7`、`about_text_primary #FF263238`、`city_tag_bg #FFEBEBEB`、`divider_on_dark #1AFFFFFF`(APK uiNotes 实测色 + 截图近似取样两处已标注);
- `values/dimens.xml`:`city_card_radius 24dp`、`city_card_height 160dp`、`hour_item_width 64dp`、`trend_view_height 280dp`、`temp_big_size 88sp`;
- `values/strings.xml` 追加:`fifteen_day_forecast`(15 天天气预报)、`just_now`(刚刚更新)、`updated_min_ago`(%1$d 分钟前更新)、`choose_city`(选择城市)、`search_hint`(输入城市全名,如 大连市)、`add_city`(添加城市)、`empty_state_hint`(添加城市,查看天气)、`offline_toast`(更新失败,请检查网络)、`retry`(重试)、`data_source_credit`(数据来源:Open-Meteo (CC-BY 4.0))等。

### 10.3 修改文件

| 文件 | 改动 |
|---|---|
| `MainActivity.kt` | 头部 overlay 改造(城市名/更新时间/图标)、ViewPager2 接入、`onPageSelected→selectPage`、收集 `backdrop` 驱动 `weatherBackdrop.setScene`;现有场景调试对话框移到溢出菜单「动画调试」入口,**逻辑零改动保留**(MainViewModel 的 sceneState/tilt 不动) |
| `ui/MainViewModel.kt` | 新增 `pages/backdrop/selectPage`(§8.2),注入 `AppContainer.weatherRepository` |
| `res/layout/activity_main.xml` | WeatherBackdropView 保留,新增 ViewPager2 与头部 overlay,移除现有调试覆盖层(移入 fragment/dialog) |
| `AndroidManifest.xml` | 追加权限 `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`;注册 `WeatherApplication`(name)与 4 个 Activity |
| `gradle/libs.versions.toml`、`app/build.gradle.kts` | §10.1 |
| `values/strings.xml`、`values/colors.xml` | §10.2 资源追加 |

不修改:`scene/` 全部文件、`view/WeatherBackdropView.kt`、`sensor/TiltSensorController.kt`、`data/TiltSensorRepository.kt`、`settings.gradle.kts`、`build.gradle.kts`(根)。

---

## 11. 风险与未验证项(诚实清单)

1. **Gradle 构建未运行**:本文档所有版本号来自 Maven Central / Google Maven metadata 实查,但本次仅允许改 docs/,未执行 `gradlew build` 验证依赖解析——实现后第一个动作应是 sync 构建。
2. **kotlinx-serialization 在 AGP 9 内置 Kotlin 的兼容性**:官方文档与社区报告表明有缺口,但**本会话未实际编译验证**;正因如此才选了免插件方案,该风险被规避而非被证明。
3. **Geocoder 逆地理**:框架 Geocoder 在无 GMS 设备上可能 `isPresent()==false` 或返回 null,已写回退名「我的位置」;具体设备表现未实测(无真机自动化手段)。
4. **Open-Meteo 长期可用性**:公益免费服务,无限流承诺;缓解 = 接口层单类收敛(§1.4)+ 缓存兜底。
5. **heytap 接口**:本会话**未**重放其请求(不采信 = 不依赖;数据源侦察报告的实测结论仅作附录存档)。
6. **截图取样色**:城市管理工具栏/页面底色为截图目测近似值,实现时可微调;APK uiNotes 实测色(文字白、tag 灰等)为准值。
7. **ViewPager2 与逐小时条横向手势**:内外层横向滚动并存,标准嵌套滚动行为下边界切页可能有轻微抢手感;v1 不做特殊处理,记录为已知小瑕疵。

---

## 12. 证据附录

### 12.1 本会话执行的检查

| 命令 | 结果要点 |
|---|---|
| `curl api.open-meteo.com/v1/forecast?...`(参数见 §1.1) | 200;timezone=Asia/Shanghai;current 10 字段;hourly 384 点;daily 16 天;current.code=55/is_day=0 |
| `curl air-quality-api.open-meteo.com/v1/air-quality?current=pm2_5,us_aqi` | 200;pm2_5=28.5,us_aqi=114 |
| `curl geocoding-api.open-meteo.com/v1/search?name=大连` | 5 个同名村镇,population 均 null |
| `curl geocoding-api.open-meteo.com/v1/search?name=大连市` | 辽宁·大连市 38.91222/121.60222 pop=7450785 |
| `curl hourly=visibility`(forecast) | 单位米(样本 4980.0/2280.0/…) |
| Maven Central metadata ×5 + Google Maven metadata ×5 | retrofit/converter-gson 3.0.0;gson 至 2.15.0(取 2.13.1 对齐 converter-gson);okhttp 至 5.5.1(取 4.12.0);datastore-preferences 1.3.0;viewpager2 1.1.0;recyclerview 1.4.0;swiperefreshlayout 1.2.0;constraintlayout 2.2.2 |
| `curl retrofit-3.0.0.pom` / `converter-gson-3.0.0.pom` | 传递依赖 okhttp 4.12.0 + kotlin-stdlib 2.1.21 / gson 2.13.1 |
| WebSearch(AGP 9 built-in Kotlin) | 内置 Kotlin 捆绑 KGP 2.2.10;`org.jetbrains.kotlin.plugin.*` 子插件不支持,workaround 均需改构建配置 |

### 12.2 输入材料(来自 ask 提供,未在本会话重放)

- APK 侦察报告:weather.1plus.io 接口族、鉴权流程、cityId 机制(location key)、uiNotes 配色/结构、riskNotes 1-7;
- 数据源侦察报告:heytap 4+1 endpoint 实测(200/5 次)、Open-Meteo 三接口首测、fallbackSource;
- 5 张真机截图(2026-11-05):主页面三段、城市管理、关于页——本文档所有视觉规格的来源。

### 12.3 附录 A:heytap 接口存档(未采用,备查)

```
https://i.browser.heytapmobi.com/weather/dailyForecast/data?cityId={OPPO_city_id}&placeId=&streetSwitch=&appVersion=null&f=json
https://i.browser.heytapmobi.com/weather/hourlyForecast/data?cityId={OPPO_city_id}&f=json
https://i.browser.heytapmobi.com/weather/current/data?cityId={OPPO_city_id}&f=json
https://i.browser.heytapmobi.com/weather/airQuality/data?cityId={OPPO_city_id}&f=json
https://news.heytapdownload.com/weather/indexnew.do?language=zh-cn&unit=c&country=CN&city_id={OPPO_city_id}&...
```

依赖前提(OPPO 体系内 cityId)无法满足 → 不实现。若未来启用,按 §1.4 的单类收敛路径新增 `data/remote/heytap/` 即可。

---

## 13. 实现落地记录(v1 已完成)

用户侧功能与运行说明见 `README.md` / `README.zh-CN.md`(与本节互为索引,不重复)。本节只记录实现与设计文本的偏差与复核结果:

1. **§6.2 第 5 条(逐小时条)实现调整**:实现为横向 `RecyclerView`(`LinearLayoutManager(HORIZONTAL)`),而非 HorizontalScrollView + LinearLayout,见 `res/layout/fragment_weather.xml` 的 `recycler_hourly`;数据仍按「下一整点起 24h + 日出日落节点」裁剪(`WeatherMapper.hourlyWindow` + `WeatherViewModel.buildHourlyRows`)。
2. **§6.3(日报条 + 曲线)实现调整**:原「单 View 整块绘制、不用 RecyclerView」在实现时拆为两个载体——日报条用 `RecyclerView`(`GridLayoutManager` span=6,`ui/weather/DailyTrendAdapter.kt`)画星期/日期/图标/天气文字;`view/WeatherTrendView.kt` 只画高低双温度曲线,列中心 `(i + 0.5) × width / 6` 与 GridLayoutManager 等分列对齐,视觉上仍是整块。
3. 其余与 §10 清单一致,实际新增还包括:`data/WeatherFormat.kt`、`data/WeatherDataException.kt`、`ui/weather/DailyTrendAdapter.kt`、`HourlyAdapter.kt`、`item_daily_forecast.xml`、`item_hour_forecast.xml`、`menu/menu_main_overflow.xml`;内置热门城市表(12 城)落在 `ui/search/CitySearchUiState.kt` 的 `PopularCity.ALL`。
4. **连通性复核**:本文档更新会话对三接口复测 curl,forecast / air-quality / geocoding 均返回 200(2026-11-05)。
5. **构建仍未验证**:§11 第 1 条仍有效——本次按用户要求跳过命令行编译验证,未执行 `gradlew`;仓库 `gradlew` 无可执行位,命令行构建用 `sh ./gradlew`。
