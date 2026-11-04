# HydrogenOS 10 Style Weather App Recreation (Unofficial)

[![中文](https://img.shields.io/badge/Language-%E4%B8%AD%E6%96%87-red?style=for-the-badge)](README.zh-CN.md)

> This animation comes from the OnePlus 7 Pro (GM1901) I used, running Android 10 / Hydrogen OS 10.0.10.GM21. The weather app built into that version had a stunning animation: the color layering, light and shadow, and the parallax that follows the phone's tilt were all pushed to the limit. To recreate that effect, I built an unofficial recreation and simulation using native Android View + Canvas, as a tribute to this excellent system app.

In a traditional XML + custom View Android project, this unofficial recreation rebuilds the full home-screen experience of a Hydrogen OS 10 style weather app: weather data comes from the free Open-Meteo APIs, with multi-city management, city search, a 15-day forecast page and two-column detail cards. All 15 weather scene animations are drawn purely with programmatic `Canvas`, switch automatically with the live weather code, and keep the tilt parallax.

The scene animation part **depends on no third-party animation libraries and contains no code, images, fonts, icons or other assets from the original app**. The data part talks to Open-Meteo over the network (free, no API key) and uses the framework LocationManager for positioning. For the full data-source decision record, WMO weather-code mapping tables and page specs, see [docs/DESIGN.md](docs/DESIGN.md).

> **Disclaimer**: This is an unofficial, non-commercial project for learning and visual demonstration. It only recreates / simulates the visual appearance of the weather app. It is not affiliated with, authorized by, or endorsed by OnePlus, Hydrogen OS, or the related weather app. All related names, trademarks and original app assets belong to their respective owners. Do not use this project for commercial purposes.

## Scene Recordings

Each clip is about 1–3.5 MB. Clicking a link opens the raw video file; the browser may play it inline or start a download. GitHub does not support embedding MP4 files in a README.

| Scene | Day recording | Night recording |
| --- | --- | --- |
| Sunny | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sunny_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sunny_night.mp4) |
| Cloudy | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/cloudy_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/cloudy_night.mp4) |
| Overcast | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/overcast_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/overcast_night.mp4) |
| Fog | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/fog_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/fog_night.mp4) |
| Haze | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/haze_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/haze_night.mp4) |
| Sandstorm | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sandstorm_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sandstorm_night.mp4) |
| Light rain | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/light_rain_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/light_rain_night.mp4) |
| Moderate rain | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/rain_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/rain_night.mp4) |
| Heavy rain | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/heavy_rain_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/heavy_rain_night.mp4) |
| Rainstorm | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/storm_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/storm_night.mp4) |
| Thunderstorm | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/thunder_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/thunder_night.mp4) |
| Sleet | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sleet_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/sleet_night.mp4) |
| Light snow | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/light_snow_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/light_snow_night.mp4) |
| Heavy snow | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/snow_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/snow_night.mp4) |
| Hail | [Day](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/hail_day.mp4) | [Night](https://github.com/SiXuManYan/hydrogen-weather-animation/raw/main/docs/scenes/hail_night.mp4) |

> There are currently 30 scene recordings covering all 15 scenes.

---

## 1. Features

### Main weather page (multi-city)

- A single full-screen `WeatherBackdropView` renders the scene animation; a transparent `ViewPager2` on top hosts one page per city, swipe left/right to switch. The background only renders the weather scene of the currently selected city.
- Header overlay: location pin (located city only) + city name + "just now / updated X min ago" (recomputed on a 30 s ticker); on the right, a city-management icon and an overflow menu (About / Animation debug).
- Page content, top to bottom:
  1. Roughly half a screen of pure animated background whitespace;
  2. Current temperature block: large temperature + condition text + high/low (`16/3°C`);
  3. 6-day forecast strip (today + next 5 days): weekday (today column fixed to "Today") / date + weather icon + condition text;
  4. High/low temperature curves: custom-drawn `WeatherTrendView`, pixel-aligned with the 6 columns of the strip, with a vertical gradient band between the two curves;
  5. **"15-day forecast" button** (see §2: an in-app page, not a browser jump);
  6. Hourly strip: 24 hours starting from the next full hour of the observation time (time / icon / temperature), with sunrise and sunset nodes inserted from today's sun times;
  7. Two-column detail cards × 8: air-quality level, PM2.5, wind direction & speed, feels-like temperature, humidity, visibility, UV index, pressure, with row dividers.
- Pull-to-refresh; a page refreshes automatically when its cache is older than 30 minutes.
- With no city on first launch, an empty state is shown (pin + "Add a city to see weather" + "Add city" button) — **no fake default city is preloaded**.

### City management page

- One full-width rounded card per city: the card background is a mini animation rendered by `WeatherCardBackdropView` with that city's weather scene (throttled to 24 fps, particle density scaled by 0.35); text is the city name + `{temp}°{condition}`.
- Long-press to drag and reorder; swipe left to delete (the located city is pinned first and can be neither dragged nor deleted); tapping a card returns to the main page and switches to that city.
- A white circular "+" FAB opens the search page; on page open all cities are refreshed in parallel in the background (concurrency limited to 4).

### Search and add a city

- The search box grabs focus automatically; after a 300 ms debounce the query hits the Open-Meteo geocoding API. Results are ranked by population first (avoiding the short-name trap where "Dalian" only matches villages of the same name), each row shows `city · region · country`.
- 12 built-in popular-city tags (Beijing / Shanghai / Guangzhou / Shenzhen / Chengdu / Hangzhou / Wuhan / Xi'an / Shenyang / Dalian / Harbin / Urumqi — no network needed); tapping a result or a tag adds the city and returns to the main page on it.
- Cities with the same id or the same coordinates (within ~11 m) are de-duplicated.

### About page

- Static page: cloud logo + "Hydro Weather V1.0" + changelog + **"Data source: Open-Meteo (CC-BY 4.0)" attribution line** (required by the license) + placeholder user-agreement / privacy-policy entries.

### Location

- On first launch with no city, location permission (fine + coarse) is requested; refusing keeps the empty state and you can search manually.
- One-shot positioning via the framework `LocationManager` (no GMS dependency): a last-known fix younger than 5 minutes is used directly; otherwise it listens for the first fix with accuracy ≤ 100 m, with a 10 s timeout treated as failure.
- Naming uses the framework Geocoder (locality → subAdminArea → adminArea), falling back to "My location"; the located city is always first in the list.

### Weather scene animation & parallax

- The 15 Canvas scenes switch automatically with the live WMO weather code; day/night follows the live `is_day` flag (estimated from the clock when no data). Tilt parallax, touch-simulated tilt and the pitch/roll debug overlay are all kept, now behind the overflow menu's "Animation debug".

---

## 2. Data Source

All weather and city-search data comes from three free [Open-Meteo](https://open-meteo.com/) endpoints, no API key required:

| Endpoint | Purpose |
| --- | --- |
| `https://api.open-meteo.com/v1/forecast` | Current conditions + hourly + daily (`forecast_days=16`, i.e. today + next 15 days) + sunrise/sunset |
| `https://air-quality-api.open-meteo.com/v1/air-quality` | Live PM2.5 (air-quality level card) |
| `https://geocoding-api.open-meteo.com/v1/search` | City search (search page) |

- **Attribution**: Open-Meteo is licensed under **CC-BY 4.0**, which requires crediting the source — this project shows "Data source: Open-Meteo (CC-BY 4.0)" on its About page. Regarding quota, Open-Meteo is a free public service; the project's on-demand, low-frequency requests (page entry / pull-to-refresh / city management) stay well within the published non-commercial limits, and there is no background polling.
- **Connectivity**: the design session's full curl test of the three endpoints (with field samples) is recorded in [docs/DESIGN.md](docs/DESIGN.md) §1.1 / §12.1; the three endpoints were re-tested while updating this README and all returned HTTP 200.
- **Failure semantics**: a failed forecast request fails the whole refresh (cached data stays on screen with "updated X min ago"; with no cache an error state with retry is shown); a failed air-quality request does not fail the refresh — the previous PM2.5 value is kept (or "—" when missing). All network errors are wrapped into a single `WeatherDataException` type in the data layer.
- **Purpose of the heytap web page**: in the original OnePlus weather app, the "15-day forecast" button opens a mobileLink web page signed by its server (the OPPO/heytap ecosystem). This project does **not** use the heytap APIs or web page (rationale in [docs/DESIGN.md](docs/DESIGN.md) D2/§1.2, endpoints archived in its Appendix A): the button opens an **in-app 15-day detail page** (`FifteenDayActivity`) instead, fed by `daily[1..15]` (tomorrow through +15 days) of the same forecast response, with zero extra network requests; when no cache exists it fetches fresh data, and if the network also fails the page shows "Load failed, check network" with a retry button — there is no "browser jump failed" scenario.

---

## 3. Requirements

| Item | Requirement |
| --- | --- |
| Android Studio | 2026.1+ (this project uses AGP 9.3.3 / Gradle 9.5) |
| JDK | 17+ (needed for command-line builds; you can point `JAVA_HOME` at the JBR bundled with Android Studio) |
| compileSdk / targetSdk | 37 |
| minSdk | 29 |
| Screen orientation | Locked to portrait |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |

---

## 4. How to Run

### Android Studio

1. `File → Open` and select the project root directory you cloned (the directory containing `settings.gradle.kts`).
2. Wait for Gradle Sync to finish (the first sync needs network access to download dependencies).
3. Select the `app` run configuration and click Run.

### Command Line

```bash
# Requires JDK 17+; without a standalone JDK you can use the JBR bundled with Android Studio:
#   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
# The gradlew wrapper in this repo has no executable bit, so invoke it via sh:
sh ./gradlew assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.sugar.weather/.MainActivity
```

The generated APK is located at:

```
app/build/outputs/apk/debug/app-debug.apk
```

> Note: per the user's request, command-line build verification was skipped for this round of changes (`gradlew` was not executed). The commands above are the standard build flow — for the first build, trust the Android Studio Gradle Sync result. Version rationale and the risk list are in [docs/DESIGN.md](docs/DESIGN.md) §10.1/§11.

---

## 5. Controls

- **Swipe left/right**: switch between cities; the background animation follows the current city's weather.
- **Header**: left side is the location pin + city name + update time; the right-side city-management icon opens the "Choose city" page, and the `⋮` overflow menu opens "About" or "Animation debug".
- **Animation debug** (the old bottom-right "Scenes" button moved into the overflow menu): switch weather scene, day/night, touch simulation and debug info, reset the view; the background follows the debug selection until you tap "Restore weather background".
- **Touch-simulated tilt**:
  - When off, the real orientation sensor is used;
  - When on, drag with a finger to simulate tilt (automatically enabled on devices without an orientation sensor);
  - Drag left/right to change roll, drag up/down to change pitch.
- **Pull to refresh** the current city's weather; tap "15-day forecast" to open the in-app detail page.
- **Tilt the phone**: left/right roll drives the horizontal parallax of the scene; front/back pitch drives vertical movement and slight scaling.

> If tilting the phone has no effect, first check whether "Touch-simulated tilt" is enabled under Animation debug; when enabled, it overrides the real sensor data.

---

## 6. Supported Scenes

| SceneType | Name | Implementation |
| --- | --- | --- |
| `SUNNY` | Sunny | 8 polygonal halo layers + 4 hexagonal clouds; switched to meteor arcs at night |
| `CLOUDY` | Cloudy | 9 translucent circular cloud clusters + sun; extra stars at night |
| `OVERCAST` | Overcast | Same as cloudy, using a gray-blue cloud palette |
| `FOG` | Fog | 24 linear-gradient circles drifting, rotating and blending slowly |
| `HAZE` | Haze | Many translucent yellow-brown particles drifting and flickering |
| `SANDSTORM` | Sandstorm | Warm gradient + dust clusters + high-speed horizontal sand particles |
| `LIGHT_RAIN` | Light rain | Raindrop line-segment particles |
| `RAIN` | Moderate rain | Raindrop particles with higher density, speed and line width |
| `HEAVY_RAIN` | Heavy rain | Denser raindrop particles |
| `STORM` | Rainstorm | Highest-density raindrop particles |
| `THUNDER` | Thunderstorm | Raindrop particles + random lightning flashes |
| `SLEET` | Sleet | Light rain + light snow combined |
| `LIGHT_SNOW` | Light snow | A small number of snowflakes (dots / hexagons) |
| `SNOW` | Heavy snow | Dense snowflakes |
| `HAIL` | Hail | Raindrops + white hail particles |

Day/night and scene switching rules (automated since v1):

- The full mapping from live WMO weather_code to `SceneType` is in [docs/DESIGN.md](docs/DESIGN.md) §4; unknown codes fall back to "Cloudy".
- The WMO code table has no haze or dust codes, so `HAZE` and `SANDSTORM` are never selected automatically and can only be reached through "Animation debug"; the same applies to `SLEET`.
- Night follows the live `is_day` flag (current conditions only; hourly/daily entries always use daytime icons); with no data it is estimated from the clock (night from 19:00 to 06:00).

---

## 7. Launching a Specific Scene (for screenshots / acceptance)

`MainActivity` supports the following Intent extras (passing the `scene` extra enters debug preview: the background follows that scene until "Restore weather background" is tapped in Animation debug):

| key | Type | Description |
| --- | --- | --- |
| `scene` | String | `SceneType` name, such as `SUNNY`, `RAIN`, `SNOW` |
| `night` | boolean | Whether to use night mode |
| `simulate` | boolean | Whether to enable touch simulation |
| `pitch` | float | Simulated pitch angle (used with `simulate=true`) |
| `roll` | float | Simulated roll angle (used with `simulate=true`) |
| `debug` | boolean | Whether to show debug info |

Example:

```bash
# Sunny daytime, simulated upright holding pose
adb shell am start -n com.sugar.weather/.MainActivity \
  --es scene SUNNY --ez night false --ez simulate true --ef pitch 90 --ef roll 0

# Thunderstorm at night
adb shell am start -n com.sugar.weather/.MainActivity \
  --es scene THUNDER --ez night true

# Show debug values
adb shell am start -n com.sugar.weather/.MainActivity \
  --es scene SUNNY --ez debug true
```

> If the same Activity is already running, `am start` may only bring the old instance to the foreground. To switch scenes between multiple screenshots, run `adb shell am force-stop com.sugar.weather` first, or exit via the system Back button before launching again.

---

## 8. Code Structure

```
app/src/main/java/com/sugar/weather/
├── MainActivity.kt                 # Main page: ViewPager2 + header overlay + location flow + debug dialog
├── WeatherApplication.kt           # Application, holds the AppContainer
├── di/
│   └── AppContainer.kt             # Hand-written DI: 3 Open-Meteo instances / repository / store / location
├── data/
│   ├── model/                      # City, WeatherReport and other domain models
│   ├── remote/                     # OpenMeteoApi (Retrofit) + DTOs + OkHttpClient
│   ├── CityStore.kt                # DataStore Preferences: city list + weather cache (JSON)
│   ├── WeatherRepository.kt        # Single data gateway: reports/cities StateFlow + refresh/add/remove
│   ├── LocationDataSource.kt       # One-shot positioning via framework LocationManager
│   ├── WeatherCodes.kt             # WMO code → text/scene + AQI/UV/wind mappings (pure functions)
│   ├── WeatherFormat.kt            # Number & unit formatting (pure functions)
│   ├── WeatherMapper.kt            # DTO → domain model + hourly window cropping
│   ├── WeatherDataException.kt     # Unified data-layer error type
│   └── TiltSensorRepository.kt     # Orientation sensor → StateFlow
├── sensor/
│   └── TiltSensorController.kt     # TYPE_ORIENTATION / TYPE_ROTATION_VECTOR wrapper
├── ui/
│   ├── MainViewModel.kt            # Page order / header / backdrop driving + debug state + tilt
│   ├── SceneUiState.kt             # Debug panel state
│   ├── CityPagesAdapter.kt         # ViewPager2 adapter (one WeatherFragment per city)
│   ├── weather/                    # Per-city page: Fragment + ViewModel + adapters + icon mapping
│   ├── city/                       # City management page
│   ├── search/                     # Search & add page (with built-in popular-city table)
│   ├── fifteenday/                 # 15-day forecast page
│   └── about/                      # About page
├── view/
│   ├── WeatherBackdropView.kt      # Full-screen pure Canvas rendering + Choreographer + touch pose callback
│   ├── WeatherTrendView.kt         # High/low temperature curves (custom-drawn)
│   ├── WeatherSingleInfoView.kt    # Two-column detail card cell
│   └── WeatherCardBackdropView.kt  # City-card mini animation background (24 fps throttle)
└── scene/                          # 15 weather scenes (pure Canvas, unchanged this round)
    ├── WeatherScene.kt / SceneType.kt / Tilt.kt
    ├── SunnyScene.kt / SunnySceneConfig.kt
    ├── CloudScene.kt / FogScene.kt / HazeScene.kt / SandstormScene.kt
    └── RainScene.kt / SnowScene.kt / CompositeScene.kt

app/src/main/res/
├── layout/                         # activity_main / fragment_weather / pages and list items
├── menu/menu_main_overflow.xml     # Overflow menu: About / Animation debug
├── drawable/ic_*.xml               # Hand-written vector icons
└── values/                         # colors / dimens / strings / themes
```

---

## 9. Architecture & Data Flow

### 9.1 MVVM layering

- **View layer** (Activities / Fragments / custom Views): only ViewBinding, insets and navigation; user input is submitted to ViewModels. Views hold no business objects and never import data-layer internals.
- **ViewModel layer**: one `WeatherViewModel` per city page, plus `MainViewModel` (page order / header / backdrop driving / debug), `CityManageViewModel`, `CitySearchViewModel` and `FifteenDayViewModel`; all expose `StateFlow<UiState>` and do the text formatting themselves.
- **Repository layer**: `WeatherRepository` is the single data gateway (all network access converges here; switching data sources means touching `data/remote/dto/` + the Mapper only); an in-memory `reports` StateFlow plus a DataStore cache (pre-warmed into memory at startup).
- **Data layer**: Retrofit + Gson (no compiler plugins), DataStore Preferences, framework LocationManager; `WeatherCodes` / `WeatherFormat` / `WeatherMapper` are pure Kotlin functions.
- **Animation layer**: the 15 scenes in `scene/` + `WeatherBackdropView` (60 fps frame loop that never goes through the ViewModel) + `WeatherCardBackdropView` (24 fps for cards).

The full data-source decision record, WMO mapping tables, page visual specs and file-level checklist live in [docs/DESIGN.md](docs/DESIGN.md); this section is an index and does not repeat them.

### 9.2 Runtime sequence diagram

The diagram below reflects the current implementation: the low-frequency weather data flow (location, caching, backdrop driving) plus the two high-frequency flows (sensor pose and the render frame loop). Solid lines are direct calls; dashed lines are StateFlow emissions or callbacks.

```mermaid
sequenceDiagram
    autonumber
    participant MA as MainActivity
    participant VM as MainViewModel
    participant WVM as WeatherViewModel (per city page)
    participant Repo as WeatherRepository
    participant API as Open-Meteo ×3
    participant Store as CityStore (DataStore)
    participant View as WeatherBackdropView
    participant Scene as WeatherScene

    Note over MA: onCreate
    MA->>MA: inflate ActivityMainBinding + CityPagesAdapter
    MA->>VM: initialize(scene, night, simulate, pitch, roll, debug, previewing)
    MA->>VM: collect pages / header / backdrop / sceneState / tilt (STARTED)
    VM-->>MA: initial state (no cities → empty-state guide)

    Note over MA,Store: onStart: location fallback chain
    MA->>MA: maybeLocate() (first launch → request location permission; refusal keeps empty state)
    MA->>Repo: submitLocatedPlace → upsertLocatedCity (Geocoder naming, falls back to "My location")
    Repo->>Store: saveCities (located city pinned at index 0)
    Repo-->>VM: cities StateFlow → pages rebuilt

    Note over WVM,Store: Per-city data flow (low frequency)
    WVM->>Repo: on page entry: refresh only if cache older than 30 min
    Repo->>API: forecast + air-quality in parallel
    API-->>Repo: ForecastResponse / AirQualityResponse
    Repo->>Store: persist weather_cache_{cityId}
    Repo-->>WVM: reports StateFlow → WeatherUiState (rendered after formatting)
    WVM-->>MA: temp block / daily strip / curves / hourly strip / 8 detail cards
    MA->>Repo: pull-to-refresh → refresh() (air-quality failure never fails the whole refresh)

    Note over MA,Scene: Backdrop driving (on page switch / data change)
    MA->>VM: onPageSelected → selectPage(index)
    VM-->>MA: backdrop = wmoScene(code) + is_day
    MA->>View: setScene(type, night)

    Note over MA: User taps "15-day forecast"
    MA->>Repo: cachedOrRefresh(cityId) (cache hit = zero requests; otherwise fetch fresh)
    Repo-->>MA: daily[1..15]; on failure → in-page error + retry

    Note over MA,Scene: Pose flow (high frequency: ~50 Hz sensor / touch simulation)
    MA->>VM: startSensors() (onStart)
    VM-->>MA: tilt StateFlow (sensor ignored while simulation is on)
    MA->>View: setTilt(tilt) → per-layer depth parallax

    Note over MA,Scene: Render frame loop (high frequency, from onResume)
    MA->>View: start()
    loop Each frame (Choreographer, dt ≤ 30ms)
        View->>Scene: update(dt, tilt, night, elapsed)
        View->>Scene: draw(canvas, night)
    end

    Note over MA: Overflow "Animation debug": selectScene / setSimulateTilt / setDebugVisible / resetSimulatedTilt / exitPreview (restore weather background)

    Note over MA: onPause → stop() halts the frame loop; onStop → stopSensors() unregisters the sensor
```

The Activity is only responsible for ViewBinding and event forwarding; `WeatherBackdropView` never accesses `SensorManager` directly and never touches the Repository. Sensor lifecycle and UI state are managed by `MainViewModel` / `TiltSensorRepository`.

---

## 10. Tuning Entry Points

| What you want to change | Where to change it |
| --- | --- |
| Sunny halo radius, rotation speed, colors, depth, night arcs | `SunnySceneConfig.kt` |
| Sunny cloud size / position / alpha | `CLOUD_*` in `SunnySceneConfig.kt` |
| Sunny parallax range, smoothing speed | `HORIZONTAL_RANGE_DP`, `FOLLOW_*` in `SunnySceneConfig.kt` |
| Background colors for each weather type | `SceneType.kt` |
| Cloudy / overcast cloud parameters | The `init` table in `CloudScene.kt` |
| Rain intensity, speed, line width, hail | The `Intensity` config block in `RainScene.kt` |
| Snow amount, flake size, count | `count` and `init` in `SnowScene.kt` |
| Haze / sand particle density | `COUNT` in `HazeScene.kt`, `SandstormScene.kt` |
| City-card animation frame rate / particle density | `WeatherCardBackdropView.kt` (24 fps / 0.35) |
| Temperature curve style (gradient, stroke, labels) | `WeatherTrendView.kt` |

---

## 11. FAQ

### 11.1 Tilting the phone does nothing

- Open `⋮` → "Animation debug" and make sure "Touch-simulated tilt" is off;
- Enable debug info and check whether `pitch` / `roll` change;
- Some devices lack `TYPE_ORIENTATION` and fall back to the rotation vector sensor;
- Emulators have no real pose changes; use touch simulation instead.

### 11.2 The old scene still shows after switching

When the same Activity is launched multiple times via adb `am start`, the system may reuse the old instance. Run `adb shell am force-stop com.sugar.weather` before launching again, or switch scenes in `⋮` → "Animation debug".

### 11.3 The composition differs slightly from the reference image

The halo radius is scaled by screen density; at different resolutions / display scales, the proportion of the screen covered by the color bands varies slightly. `SunnyScene.kt` normalizes it so that "the bottom edge of the outermost ring is at about 1/3 of the screen height"; fine-tune it with the `initialVertical` line.

### 11.4 The page shows "Load failed" or "Updated X min ago"

- "Load failed, check network" + retry: the current city has no cache at all and the request failed — check the network, then retry or pull to refresh;
- "Updated X min ago" with stale data: the request failed but a disk cache exists, so the app still works offline (rendering cached data);
- If a city search returns nothing, try the full city name (e.g. "Dalian City" instead of "Dalian" — see §2, short-name trap).

---

## 12. Scope

- Not implemented: weather warnings (no free China warning data source; the header alert icon is a hidden placeholder), weather videos, home-screen widgets / share / feedback / settings page, background scheduled refresh (foreground only: page entry when cache is older than 30 minutes, pull-to-refresh, batch refresh on the city management page).
- Air quality shows only the PM2.5 concentration and level text; the US AQI field returned by the API stays in the DTO and is not shown in the UI.
- `HAZE` / `SANDSTORM` / `SLEET` are never selected automatically from live data (no matching WMO codes); they are reachable only through "Animation debug".
- Units are fixed to metric (°C, km/h, hPa, km); the user agreement / privacy policy entries on the About page are placeholders without real links.
- The sunny scene is a fairly complete visual simulation based on reference screenshots; the other scenes are programmatic approximations based on similar weather effects.

---

## 13. Copyright and Disclaimer

- This project is an independently implemented learning / demonstration project that recreates and simulates the visual appearance of a weather app through programmatic drawing only.
- The scene animation contains no code, images, fonts, icons or other assets from the original app, and has not been authorized, partnered with, or endorsed by the relevant rights holders.
- Weather data comes from [Open-Meteo](https://open-meteo.com/) under the **CC-BY 4.0** license, credited on the app's About page; Open-Meteo is not affiliated with this project.
- Product names, system names and trademarks such as OnePlus and Hydrogen OS are used only to describe the reference device and reference target, and belong to their respective owners.
- This project grants no commercial-use license; if you need commercial use, please verify and obtain permission from the relevant rights holders yourself.
- If a rights holder believes this project is inappropriate, please contact the author via the repository Issues.
