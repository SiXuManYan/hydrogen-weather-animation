# 隐私政策 / Privacy Policy

- 生效日期 / Effective date：2026-11-05
- 最后更新 / Last updated：2026-11-06
- 适用应用 / App：氢天气 V1.1（`com.sugar.weather`）
- [项目仓库 / Repository](https://github.com/SiXuManYan/hydrogen-weather-animation)

## 中文

### 1. 概述

本应用是非官方的氢 OS 风格天气复刻项目，没有账号、广告、统计分析或崩溃上报 SDK，没有自建服务器，也不在后台定时查询天气。

### 2. 处理的数据与权限

| 数据 | 用途与去向 |
| --- | --- |
| 城市名称、经纬度、排序和添加时间 | 保存在本机，用于城市管理和查询天气 |
| 最近一次实况、逐时、逐日和空气质量数据 | 保存在本机，供离线或刷新失败时展示 |
| 定位坐标 | 经授权后按需获取，用于查询天气及由系统 Geocoder 解析城市名称；不记录轨迹 |
| 城市搜索词 | 发送到 Open-Meteo Geocoding API 获取搜索结果 |

应用声明 `INTERNET` 权限以联网；`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` 为可选的定位权限。拒绝定位后仍可手动搜索添加城市，不进行后台定位。

### 3. 第三方服务

天气与搜索请求直接发送到以下域名：

- `api.open-meteo.com`：实况、逐时、逐日；部分坐标额外请求 CMA GRAPES 模型。
- `air-quality-api.open-meteo.com`：PM2.5 和空气质量数据。
- `geocoding-api.open-meteo.com`：城市名称搜索。

请求包含对应城市的经纬度或搜索词，以及网络请求固有的 IP 地址等信息。Open-Meteo 按其自身政策处理这些信息，见 [使用条款与隐私政策](https://open-meteo.com/en/terms)。天气数据按 CC BY 4.0 在应用关于页署名。

定位城市命名还会调用 Android 系统 Geocoder。该服务可能通过设备或系统提供方的网络服务处理坐标，其提供方和数据处理方式由设备系统决定。

本项目不向自有服务器上传数据，不出售或出租个人数据；上述服务请求涉及的数据传输按相应第三方的政策处理。

### 4. 本地存储、备份与删除

城市列表和天气缓存保存在应用私有 DataStore 中。应用没有自建云同步；Android 自动备份或设备迁移可能复制这些数据，项目无法访问用户的系统备份。

- 在应用内删除可删除的城市，会移除城市条目及对应磁盘缓存。定位城市当前不可删除。
- 清除应用存储或卸载应用可删除本机的应用数据。
- 系统备份副本及其恢复由设备备份设置管理，清除本机数据不等于清除已有备份。

天气请求使用 HTTPS，本地数据受 Android 应用沙箱保护。

### 5. 儿童、政策更新与联系

本应用不面向儿童单独收集个人信息。数据来源、权限或处理方式变化时，会更新本文件并在更新日志中说明。

隐私问题请通过 [仓库 Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues) 联系。

## English

### 1. Overview

This is an unofficial Hydrogen OS style weather recreation. It has no accounts, ads, analytics or crash-reporting SDK, no project-operated backend, and no scheduled background weather polling.

### 2. Data and permissions

| Data | Purpose and destination |
| --- | --- |
| City names, coordinates, order and timestamps | Stored on-device for city management and weather queries |
| Latest current, hourly, daily and air-quality data | Stored on-device for offline use or failed-refresh fallback |
| Location coordinates | Obtained as needed after permission, for weather and city naming through the system Geocoder; no route history |
| City search queries | Sent to the Open-Meteo Geocoding API for search results |

The app declares `INTERNET` for network access. `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` are optional; manual city search remains available if denied. The app does not access location in the background.

### 3. Third-party services

Weather and search requests go directly to:

- `api.open-meteo.com`: current, hourly and daily weather, including an additional CMA GRAPES request for some coordinates.
- `air-quality-api.open-meteo.com`: PM2.5 and air-quality data.
- `geocoding-api.open-meteo.com`: city-name searches.

Requests include the relevant city coordinates or search terms, plus network metadata such as the IP address. Open-Meteo handles this information under its own policies; see its [terms and privacy policy](https://open-meteo.com/en/terms). Weather data is credited under CC BY 4.0 on the About page.

Located-city naming also calls Android's system Geocoder. It may process coordinates through a device or system provider's network service. The provider and its processing practices depend on the device system.

The project does not upload data to its own servers, sell or rent personal data. Transfers required by the services above are subject to their respective policies.

### 4. Local storage, backup and deletion

Cities and weather caches are stored in the app's private DataStore. The app has no project-operated cloud sync; Android backup or device transfer may copy this data. The project cannot access the user's system backups.

- Removing a removable city in the app deletes its entry and disk cache. The located city currently cannot be removed.
- Clearing app storage or uninstalling removes the app's on-device data.
- Backup copies and restoration are managed through device backup settings. Clearing local data does not delete existing backups.

Weather requests use HTTPS. Local data is protected by Android's application sandbox.

### 5. Children, updates and contact

The app does not separately collect information from children. Changes to data sources, permissions or processing will be reflected here and in the changelog.

For privacy questions, use [repository Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues).
