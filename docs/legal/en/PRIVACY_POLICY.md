# Hydrogen Weather Privacy Policy

Updated: November 6, 2026.

## 1. What is processed

Hydrogen Weather has no accounts, ads, analytics, crash reporting or project-operated server. Personal data is not sold or rented.

- **Cities and weather:** names, coordinates, location flags, order, the latest weather and update times are stored locally for city switching and offline viewing.
- **City search:** entered city names are sent to the search service. Search history is not stored separately; added cities are saved locally.
- **Orientation:** readings are used only for local tilt animation and are neither persisted nor uploaded.

## 2. Location is optional

The system asks for precise or approximate location permission. You can deny or revoke it and add cities manually.

With permission, the app reads a recent system location or requests one update to find the city and query weather. Coordinates are also passed to the system place-name service. Location stops on success, timeout or cancellation; there is no background location permission or route history. System location and place-name services may connect to the network under the device provider's rules.

Revoking permission does not remove a saved located city or stop weather queries for its saved coordinates. Clear app storage to remove it. Location success and update limitations are described in Help.

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

Debug packages may log request coordinates, search text, status codes and duration locally. Release packages disable these network logs; neither automatically sends logs to the author. Remove sensitive details before sharing feedback.

## 6. Choices and contact

You can revoke location, select cities manually, delete manual cities, clear storage or stop using the app. Cached weather and local documents remain readable offline. Manage device security, debugging permissions and backup accounts carefully. Minors should manage location permission and public feedback with a guardian.

Changes to data handling or providers will update the policy date and changelog. Bundled policy applies to the installed version. Components are listed under Data sources and open-source components.

For privacy questions, contact the author through [project Issues](https://github.com/SiXuManYan/hydrogen-weather-animation/issues). Submissions are public by default; omit precise locations and identity information.
