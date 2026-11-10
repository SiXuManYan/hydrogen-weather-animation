# Hydrogen Weather Privacy Policy

Updated: November 11, 2026.

## 1. What is processed

Hydrogen Weather has no accounts, ads, analytics, crash reporting or project-operated server. Personal data is not sold or rented.

- **Cities and weather:** names, coordinates, location flags, order, the latest weather and update times are stored locally for city switching and offline viewing.
- **City search:** entered city names are sent to the search service. Search history is not stored separately; added cities are saved locally.
- **Orientation:** readings are used only for local tilt animation and are neither persisted nor uploaded.

After a home screen weather widget is added, the app reads the first city's local weather cache about every 30 minutes and redraws it using the city's current calendar date. This step needs no network. A separate weather task waits for connectivity before using saved coordinates to check and update weather from the providers below, including while the app interface is closed; manual refresh uses that queue too. All widget instances share work. Removing the last widget stops both cached redraws and weather requests. Widgets do not acquire background device location; the system launcher displays their contents, and system power policies may delay tasks.

## 2. Location is optional

The system asks for precise or approximate location permission. Both support finding your city. You can deny or revoke permission and add cities manually.

With permission, the app reads a recent system location or requests one update while the weather home page is visible, to find the city and query weather. Coordinates are also passed to the system place-name service, preferring a district or county name and falling back to a city or province. Roads, house numbers and full addresses are not used as location names. With permission already granted, foreground resume silently checks location, waiting at least 30 minutes after success or 2 minutes after failure before another attempt. Even without significant movement, saved coordinates are passed to the system place-name service to refine the name while retaining coordinates and cached weather. Moving at least 1km from the saved location and beyond the reported accuracy radius updates the existing located city's name, coordinates and weather; intermediate positions are not kept as a list.

Each location request stops on success, timeout, cancellation or when the home page leaves the foreground. The app neither requests background location permission nor acquires background location, and keeps no route history. System location and place-name services may connect to the network under the device provider's rules.

Revoking permission does not remove a saved located city or stop weather queries for its saved coordinates. Clear app storage to remove it. Manual city selection remains available when system location is unavailable; see Help for controls.

## 3. Who receives requests

Weather and search requests go directly to Open-Meteo without a project backend:

- `api.open-meteo.com` receives city coordinates and forecast parameters for weather and solar events.
- `air-quality-api.open-meteo.com` receives city coordinates for PM2.5 and related data.
- `geocoding-api.open-meteo.com` receives search text for candidate cities.

Providers receive normal network metadata such as IP addresses and request times. The app attaches no advertising or unique device identifiers, contacts or photos. Requests use HTTPS and may be processed by infrastructure outside your country. See [Open-Meteo's terms and privacy information](https://open-meteo.com/en/terms).

## 4. Retention and deletion

- Cities and the latest weather remain in private app storage until updated or deleted.
- Deleting a manual city also deletes its local weather cache. The located city cannot currently be removed individually.
- Android App info → Storage → Clear storage, or uninstalling, removes private on-device copies.
- Android backup or device transfer may restore cities and weather. Device or account settings govern existing backups; clearing local data does not delete system backups.

The author cannot access or delete your local data or system backups. There is no cloud account or separate activity profile to export.

## 5. Documents, websites and logs

The agreement, policy, help, changelog and component notices are available offline. Website links show a prompt first, then open a browser tab after confirmation, falling back to the system browser. Links do not include cities, coordinates or weather caches, and the app uses no WebView.

Browsers and websites may use existing logins, cookies, IP addresses and access records under their own policies. Copying the Douyin account writes it to the clipboard only after an explicit tap; the app does not read the clipboard.

Debug HTTP logs record only the method, host, status code, duration or error category locally. They omit coordinates, search text, paths, headers and bodies. Release packages disable these logs; neither automatically sends logs to the author. Remove any other sensitive details before sharing feedback.

## 6. Choices and contact

You can revoke location, select cities manually, delete manual cities, clear storage or stop using the app. Cached weather and local documents remain readable offline. Manage device security, debugging permissions and backup accounts carefully. Minors should manage location permission and public feedback with a guardian.

Changes to data handling or providers will update the policy date and changelog. Bundled policy applies to the installed version. Components are listed under Data sources and open-source components.

For privacy questions, contact the author through [project Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues). Submissions are public by default; omit precise locations and identity information.
