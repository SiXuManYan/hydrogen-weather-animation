# Hydrogen Weather Privacy Policy

Updated: November 6, 2026. Applies to the current version of `com.sugar.weather`.

## 1. Overview

The app has no accounts, ads, analytics or crash-reporting SDK, no project-operated server and no project-operated cloud sync. This policy describes actual data handling, third-party requests and deletion.

Weather and search require connectivity. The agreement, privacy policy, help, changelog and component notices are bundled for offline reading.

## 2. Cities and weather

Private DataStore stores city names, coordinates, identifiers, location flags, order and addition times, plus the latest current, hourly, daily and air-quality data and fetch times. These support city management, weather queries and offline display.

Manual-city coordinates come from search results or built-in popular cities; located-city coordinates come from the authorized system location service. Requests go directly to Open-Meteo, without a project backend.

## 3. Optional location permission

The app declares `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` to attempt city location according to home-screen logic. You can deny or revoke them in Android settings and continue to add cities manually.

Android `LocationManager` reads recent cached locations or requests a one-time update; listeners stop on success, timeout or cancellation. There is no background location permission, continuous background tracking or route history. Approximate-location success and updates to an existing located city's coordinates have current limitations described in help.

Coordinates are used for weather and passed to the system `Geocoder` for city naming. Geocoder may connect to a device or system provider's network service; its provider and practices depend on the device system.

Revoking permission does not remove a saved located city or stop weather queries for its saved coordinates. Clear app storage to remove that local location record.

## 4. Search and network metadata

After a short debounce, city search sends the entered query for candidate names, coordinates and administrative details. Input is not separately persisted as search history; added results are saved as cities.

The app declares `INTERNET`. Services receive normal network metadata such as IP address, time and protocol information. The app does not attach advertising identifiers, contacts, photos, accounts or unique device identifiers.

## 5. Third-party services

- `api.open-meteo.com` receives city coordinates and forecast parameters for current, hourly, daily and solar data. Some coordinates near China trigger an additional CMA GRAPES model request to the same service.
- `air-quality-api.open-meteo.com` receives coordinates and air-quality parameters for PM2.5 and related data.
- `geocoding-api.open-meteo.com` receives search text and search parameters for candidate places.
- Android location and Geocoder providers process location and place naming according to permission and system implementation.

Open-Meteo handles received information under its [terms and privacy policy](https://open-meteo.com/en/terms). Requests may be processed by infrastructure outside your country, subject to the provider's policy and deployment. The project does not sell or rent personal data or upload city, location or activity records to a project server.

UI, storage, network and Markdown components are listed in About → Data sources and open-source components. No separate advertising or analytics service is integrated.

## 6. Sensors and debug logs

Orientation sensors are read locally for tilt animation. Readings are not persisted or uploaded; simulated tilt is available without a sensor.

Debug packages enable HTTP BASIC logs, which may include request URLs containing coordinates or search terms, status codes and duration in device debugging logs. Logs are not automatically sent to maintainers. Release packages disable this network logging. Remove sensitive details before sharing logs.

## 7. Retention, deletion and backup

Cities and the latest weather remain locally until replaced or deleted:

- In city management, long-press or tap Edit, select manual cities and confirm deletion. This removes entries, in-memory reports and disk caches. In-flight requests cannot restore a deleted city's weather.
- The located city cannot currently be removed individually. Revoking permission does not clear its coordinates.
- Android App info → Storage → Clear storage, or uninstalling, removes private on-device copies. There is no account-deletion or cloud-deletion workflow.
- Android backup is enabled. System backup or device transfer may copy cities and weather and restore eligible data after reinstalling or migration. Device or account backup settings govern existing backups; clearing local storage does not delete them.

There is no separate search-history, location-route or server-activity profile to export. Maintainers cannot access or delete your local data or system backups for you.

## 8. Local documents and websites

Reading bundled documents makes no network request. Tapping an `http` or `https` link hands its address to the device browser. The app uses no WebView and does not attach city, coordinates or weather caches to document links.

The browser and destination may handle IP addresses, cookies, logins and access logs under their own policies. Decide what to submit when visiting Issues or third-party sites.

## 9. Security and choices

Weather and search use HTTPS. Android's sandbox protects local app storage; the app adds no separate database password or encrypted cloud backup. Manage device unlock, debugging permissions and backup accounts carefully.

You may deny or revoke location, select cities manually, delete manual cities, stop using the app or clear storage. Offline mode supports cached weather and local documents, but cannot obtain new search results or weather.

## 10. Minors and changes

The app does not create separate profiles or advertising audiences for minors. Minors should manage location permission and public feedback with a guardian and avoid posting precise locations or identity information.

Changes to permissions, providers or processing will update the policy date and changelog. Bundled policy applies to the installed version; the repository may contain a later version.

## 11. Contact

Use [project Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues) for privacy questions. Submissions are public by default; include only necessary troubleshooting details, without sensitive location, identity or log information.
