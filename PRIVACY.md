# SportageOBD Intelligence — Privacy Policy

**Last updated:** 6 November 2026
**Applies to:** the SportageOBD Intelligence Android application (`com.sportage.obd`)

SportageOBD Intelligence ("the app") is a car launcher and OBD-II dashboard. It is designed to
work without an account and without a vendor backend. This policy explains, in plain language,
what the app can access, where that information goes, and what you can switch off.

## Summary

- **No account, no sign-up, no advertising, no data sale.**
- Vehicle data (RPM, speed, temperatures, trouble codes, VIN) is decoded **on the device**.
- Trip history, scan history and CSV logs are stored **only on the device** unless you explicitly
  share or export them.
- Analytics and crash reporting are **off until you opt in** during onboarding or in Settings.
- The only routine network traffic is map tile requests (your coordinates are sent to the tile
  server) and, if you enable it, the diagnostic-reporting services described below.

## 1. Information the app accesses

| Data | Why it is needed | Where it goes |
|---|---|---|
| **Bluetooth adapter details** (device name, MAC address) | To connect to your ELM327 OBD-II adapter and re-connect automatically next time | Stored locally in the app's private preferences |
| **Vehicle data over OBD-II** (RPM, speed, coolant/intake temperature, throttle, load, fuel level, battery voltage, fuel trims, trouble codes, freeze-frame, readiness monitors, VIN when the ECU exposes it) | To display the dashboard, diagnose fault codes, score vehicle health and record trips | Decoded and stored **on the device** only |
| **Approximate and precise location** (GPS) | Speed and heading on the map pane, map centring, trip distance | Used in memory while the app runs; coordinates are included in map tile requests (see §3) |
| **Installed application list** | The launcher's app drawer (`QUERY_ALL_PACKAGES`) | Read on the device to render the drawer; never transmitted |
| **Diagnostic logs you create** (CSV telemetry, trip export, PDF report) | So you can review or share your own data | Written to the app's private storage; only shared when **you** tap Share/Export |
| **Crash traces** | To fix crashes | Written to a private file on the device; sent off-device **only if you enable crash reporting** |

The app does **not** read your contacts, SMS, call log, calendar, photos, microphone, camera or
files outside its own storage.

## 2. Data stored on your device

- Connection preferences (last adapter MAC, units, thresholds, night-dimming level).
- Trip history and diagnostic scan history (local database).
- CSV telemetry logs you choose to record.
- The previous crash trace, so the app can show it to you on the next launch.

You can delete this at any time by clearing the app's storage in Android Settings or uninstalling
the app. Trip and scan history can also be cleared from inside the app.

## 3. Network connections

The app makes network requests only in these cases:

1. **Map tiles.** The map pane loads OpenStreetMap-derived tiles over HTTPS. The tile server
   receives your IP address and the coordinates/zoom of the area you are viewing. This is how every
   online map works.
2. **Optional diagnostics (opt-in).** If you turn on *Share anonymous diagnostics*:
   - **Crash reports** (Firebase Crashlytics) — stack trace, app version, device model, Android
     version, and a random installation identifier. No vehicle data, no location, no VIN.
   - **Usage events** (Firebase Analytics) — coarse product events such as
     `onboarding_completed`, `obd_connect_success`, `dtc_scan_completed`, `page_view`. They carry
     no vehicle identifiers, no coordinates and no free text.
3. **Optional AI explanations (opt-in, off by default).** If a future build exposes the
   bring-your-own-key AI explanation feature and you turn it on, the specific trouble code and the
   sensor snapshot shown on screen are sent to the AI provider you configured. The API key is
   stored on the device and never leaves it except to that provider.

No other traffic is generated. The app contains no advertising SDK and no social SDK.

## 4. Permissions and why they exist

| Permission | Purpose |
|---|---|
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+), `BLUETOOTH`, `BLUETOOTH_ADMIN` (≤ Android 11) | Find, pair with and talk to the OBD-II adapter |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | GPS speed and the map pane |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` | Keep telemetry streaming (and show its notification) while you use navigation or another app |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Map tiles; detect whether tiles can load |
| `QUERY_ALL_PACKAGES` | The launcher's app drawer — a home-screen replacement must be able to list and start installed apps |

## 5. Children

The app is not directed at children and collects no personal information from anyone by default.

## 6. Your choices

- Turn analytics and crash reporting on or off at any time in **Settings → Privacy & diagnostics**.
- Keep the app fully offline by leaving the map pane on the bundled/inline map and diagnostics off.
- Delete local data by clearing app storage.

## 7. Changes

Material changes to this policy will be published in this file with a new "Last updated" date and
noted in the release notes.

## 8. Contact

Questions or data requests: open an issue at
<https://github.com/farazsocialh-jpg/sportageobd/issues>.

---

This document is the single source of truth for the app's privacy statement. A copy is bundled
into the APK (see `android/app/build.gradle.kts`, `copyPrivacyPolicy`) and shown in
**Settings → Privacy policy**. The public web version is [`docs/privacy.html`](docs/privacy.html).
