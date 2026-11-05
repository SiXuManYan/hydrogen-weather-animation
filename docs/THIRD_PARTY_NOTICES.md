# 数据来源与开源组件

更新日期：2026 年 11 月 6 日。

## 天气与地名数据

天气、预报、PM2.5 和城市搜索来自 [Open-Meteo](https://open-meteo.com/)。天气数据按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 署名；免费服务使用条件和请求限制见其[服务条款](https://open-meteo.com/en/terms)。本应用不会额外授予第三方数据或服务的使用权。

中国附近坐标（北纬 18°–54°、东经 73°–135°）额外请求 Open-Meteo 提供的 CMA GRAPES 全球模型，覆盖今天起 5 天，失败时回退 best_match。该范围是粗略包围盒，包含邻国部分地区；其余日期使用 best_match。

本应用对数据进行模型合并、单位转换、取整，以及城市当地 `[08:00, 20:00)` 的天气聚合，因此展示是经过加工的结果。PM2.5 等级是浓度近似分档，不是完整中国 AQI。定位城市的地名还可能由 Android 系统 Geocoder 提供。

## 随应用使用的组件

以下为主要直接运行时依赖；具体版本和传递依赖以当前构建的 Gradle 依赖图及上游许可为准。链接指向官方源码或说明，许可证不由本项目替换。

- [AndroidX / Jetpack](https://android.googlesource.com/platform/frameworks/support/)：Activity、Fragment、AppCompat、Core、Lifecycle、RecyclerView、ViewPager2、SwipeRefreshLayout、DataStore；Apache License 2.0。
- [Material Components for Android](https://github.com/material-components/material-components-android)：界面控件与对话框；Apache License 2.0。
- [Kotlin 标准库](https://github.com/JetBrains/kotlin)：语言运行库；Apache License 2.0。
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)：异步任务与状态流；Apache License 2.0。
- [Retrofit](https://github.com/square/retrofit)：天气与搜索网络接口；Apache License 2.0。
- [Gson](https://github.com/google/gson)：网络响应及本机缓存的 JSON 解析；Apache License 2.0。
- [OkHttp](https://github.com/square/okhttp) 与 [Okio](https://github.com/square/okio)：HTTP 客户端、网络调试与 IO；Apache License 2.0。
- [Markwon](https://github.com/noties/Markwon)：包内 Markdown 阅读；Apache License 2.0。
- [CommonMark Java](https://github.com/commonmark/commonmark-java)：Markwon 使用的 Markdown 解析器；BSD 2-Clause。

上述组件不代表本应用接入了相应公司的广告、统计或账号服务。服务数据处理见隐私政策。

## Logo、动画与字体

新 Logo“晴空轻云”由本项目使用几何路径绘制，桌面图标、关于页与 SVG 共用路径；天气动画使用 Canvas 程序化绘制。它们未使用原天气应用的图片或 Logo，不表示厂商授权。界面使用 Android 系统字体，未随包分发第三方字体文件。

项目整体与各组件的授权分别适用；引用的品牌与商标归各自权利人所有。许可或权利事项可通过[项目 Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues)联系。
