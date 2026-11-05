# 隐私政策 / Privacy Policy

- **生效日期 / Effective date**：2026-11-05
- **适用应用 / Applies to**：氢天气 V1.1（`com.sugar.weather`）
- **项目仓库 / Repository**：<https://github.com/SiXuManYan/hydrogen-weather-animation>

---

## 中文

### 1. 概述

本应用是一个非官方的氢 OS 风格天气应用复刻项目，不提供账号系统，不包含广告、统计分析或崩溃上报 SDK，也不会有后台轮询。天气数据由公开的 Open-Meteo 接口提供。

### 2. 我们处理哪些数据

- **城市列表**：你手动添加或通过定位生成的城市名称、经纬度、排序和添加时间，保存在本机。
- **最近一次天气缓存**：实况、逐小时、逐日预报和空气质量数据，保存在本机，用于离线或网络失败时展示上次结果。
- **定位坐标**：仅在你授权定位且需要定位城市时获取一次，用于查询该坐标的天气和反查城市名称。
- **搜索词**：你在城市搜索页输入的文字，会发送到 Open-Meteo Geocoding API 用于搜索城市。

本应用**没有自建服务器**，不会把上述数据上传到本项目控制的服务器。

### 3. 权限说明

| 权限 | 用途 | 是否必需 |
| --- | --- | --- |
| `INTERNET` | 请求 Open-Meteo 天气、空气质量和城市搜索接口 | 是 |
| `ACCESS_NETWORK_STATE` | 预留网络状态判断；当前版本未主动查询，网络失败由请求异常触发提示 | 否（预留） |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 获取一次城市级坐标，用于定位城市天气；不进行后台定位或轨迹记录 | 否，可拒绝 |

拒绝定位权限时，你仍然可以手动搜索并添加城市。

### 4. 网络请求与第三方

应用会直接请求以下 Open-Meteo 域名：

- `api.open-meteo.com`：实况、逐小时、逐日预报；中国境内还会请求 CMA GRAPES 模型（`models=cma_grapes_global`，前 5 天）。
- `air-quality-api.open-meteo.com`：PM2.5 / 空气质量。
- `geocoding-api.open-meteo.com`：城市搜索与坐标查询。

请求会携带经纬度、搜索词以及网络请求固有的 IP 地址等信息。这些信息由 Open-Meteo 按其隐私政策处理，本项目无法控制其收集、存储和保留行为。Open-Meteo 的使用条款与隐私政策见 <https://open-meteo.com/en/terms>，定价见 <https://open-meteo.com/en/pricing>。

天气数据采用 **CC-BY 4.0** 许可，应用「关于页」已注明数据来源。

### 5. 本地存储与删除

城市列表和天气缓存使用 Android DataStore 保存在应用私有目录中。应用本身不做云同步；但如果设备启用了 Android 系统自动备份，应用数据可能被纳入用户自己的系统备份，本项目无法访问该备份。你可以通过以下方式删除本地数据：

- 在应用内删除城市或清除应用数据；
- 卸载应用；
- 在系统设置中清除应用存储。

### 6. 数据共享与出售

本应用不向第三方出售、出租或共享你的个人数据。除上述 Open-Meteo 请求外，不进行其他网络传输。

### 7. 安全

应用使用 HTTPS 与 Open-Meteo 通信；本地数据受 Android 应用沙箱保护。由于本应用不运营服务器，不存在服务端用户数据库。

### 8. 儿童

本应用不面向儿童单独收集个人信息，也不包含账号、社交或广告功能。

### 9. 政策变更

如果数据来源、权限或处理方式发生变化，我们会在项目仓库更新本文件，并在应用更新日志中注明。重大变更会通过版本更新说明提示。

### 10. 联系方式

如有隐私相关问题，请通过项目仓库的 Issues 反馈：

<https://github.com/SiXuManYan/hydrogen-weather-animation/issues>

---

## English

### 1. Overview

This is an unofficial Hydrogen OS style weather app recreation. It has no account system, no ads, no analytics or crash-reporting SDK, and no background polling. Weather data is provided by the public Open-Meteo APIs.

### 2. Data We Process

- **City list**: names, coordinates, ordering and timestamps of cities you add manually or via location. Stored on your device.
- **Latest weather cache**: current, hourly and daily forecasts plus air quality, stored locally for offline or failure fallback.
- **Location coordinates**: obtained once, only when you grant location permission and a located city is needed, to query weather and resolve a city name.
- **Search queries**: text you enter on the city search page, sent to the Open-Meteo Geocoding API.

The app has **no backend server** and does not upload this data to any server controlled by this project.

### 3. Permissions

| Permission | Purpose | Required |
| --- | --- | --- |
| `INTERNET` | Fetch weather, air quality and city search data from Open-Meteo | Yes |
| `ACCESS_NETWORK_STATE` | Reserved for network-state checks; not queried in the current version, and failures are reported from request exceptions | No (reserved) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Obtain a one-shot city-level coordinate for located-city weather; no background tracking | No, can be denied |

You can still search and add cities manually if location permission is denied.

### 4. Network Requests and Third Parties

The app directly calls these Open-Meteo domains:

- `api.open-meteo.com`: current, hourly and daily forecasts; for locations in China it also requests the CMA GRAPES model (`models=cma_grapes_global`, first 5 days).
- `air-quality-api.open-meteo.com`: PM2.5 / air quality.
- `geocoding-api.open-meteo.com`: city search and coordinate lookup.

Requests include coordinates, search terms and network metadata such as your IP address. Open-Meteo processes this information under its own privacy policy, which this project does not control. See the Open-Meteo terms and privacy policy at <https://open-meteo.com/en/terms>; pricing is at <https://open-meteo.com/en/pricing>.

Weather data is used under the **CC-BY 4.0** license and is credited on the app's About page.

### 5. Local Storage and Deletion

The city list and weather cache are stored in the app's private DataStore directory. The app does not run its own cloud sync; however, if Android system backup is enabled, app data may be included in the user's own system backup, which this project cannot access. You can delete local data by:

- removing cities in the app or clearing app data;
- uninstalling the app;
- clearing app storage in system settings.

### 6. Sharing and Sale

The app does not sell, rent or share your personal data with third parties. Apart from the Open-Meteo requests described above, no other network transmission occurs.

### 7. Security

The app communicates with Open-Meteo over HTTPS, and local data is protected by the Android app sandbox. The project does not operate a server-side user database.

### 8. Children

The app does not knowingly collect personal information from children and has no accounts, social features or ads.

### 9. Changes

If data sources, permissions or processing practices change, this document will be updated in the repository and noted in the app changelog. Material changes will be highlighted in release notes.

### 10. Contact

For privacy questions, please open an issue in the project repository:

<https://github.com/SiXuManYan/hydrogen-weather-animation/issues>
