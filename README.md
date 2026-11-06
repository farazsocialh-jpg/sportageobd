# SportageOBD Intelligence

An Android head-unit launcher that doubles as a live OBD-II instrument cluster, plus a browser
cockpit for the same vehicle — built around a Kia Sportage (QL) 2.0 MPI AWD on ISO 15765-4 CAN.

The repo ships two separate front ends over the same idea: talk to a real ELM327 adapter, decode
Mode 01 PIDs, and show the result on a screen designed to be glanced at while driving.

[![CI Pipeline](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android.yml/badge.svg)](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/Android-9%2B%20(API%2028--35)-brightgreen)
![Web](https://img.shields.io/badge/Web-React%2019%20%2B%20Vite%208-61dafb)

---

## What the Android app does

`com.sportage.obd` is a single-activity Jetpack Compose app that registers itself as a **HOME
launcher** (`CATEGORY_HOME` + `CATEGORY_DEFAULT`), so a head unit or phone boots straight into the
cockpit instead of a normal home screen.

**Six pages**, driven by an `activePage` index and a persistent top status strip:

| Page | What it shows |
|---|---|
| **Home** | Adaptive split: live map on one side, authoritative OBD pane on the other. Landscape is ~58/42 map/gauges (64/36 on ultrawide ≥ 2.0 aspect); portrait flips to 44% map / 56% telemetry. |
| **Gauges & Graphs** | Large speed and RPM readouts with rolling history graphs, plus mini tiles for coolant, intake temp, load, throttle, fuel and battery voltage. |
| **Diagnostics** | Mode 03 trouble-code scan with severity classification, Mode 04 clear, and a scan-state indicator. |
| **Trip Computer** | Distance, duration, max speed and average speed, with reset. |
| **Media & Radio** | FM tuner display with station stepping and play/pause, plus shortcuts into the head unit's own radio and music apps (the FM chip itself belongs to the MCU). |
| **Settings** | Set-as-default-launcher button, km/h ↔ mph toggle, demo-mode switch, polling pause toggle, app drawer shortcut and Bluetooth connection entry. |

**OBD telemetry** — `BluetoothTransport` speaks ELM327 AT commands over RFCOMM SPP and:

- wraps `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`, `ATRV` into an init handshake;
- polls `010C` (RPM) and `010D` (speed) every cycle, then rotates auxiliary PIDs for throttle, engine
  load, coolant, intake air and fuel level;
- falls back through three connection strategies — secure RFCOMM, insecure RFCOMM, then a reflective
  `createRfcommSocket(channel 1)` — for head-unit ROMs that refuse the standard socket;
- measures and displays the real sampling rate (Hz) and estimates the engaged gear from the
  speed/RPM ratio for the 6-speed automatic.

**Also included**

- **Background streaming** — the RFCOMM socket and the PID loop live in the `connectedDevice`
  foreground service (`ObdService`), so telemetry keeps flowing while the launcher UI is
  backgrounded, and a relaunched activity re-attaches to the still-running service. The last
  adapter's MAC is persisted, so a service restarted by the system rebuilds the link on its own.
- **Pause polling** — one tap stops PID traffic when parked with the ignition off, so the adapter
  does not drain the battery.
- **Bench demo mode** — a simulated ECU that drives every gauge, graph and DTC path for testing
  indoors without a car attached.
- **Terminal console** — send raw AT/OBD commands and read the TX/RX log, including adapter
  handshake output.
- **App drawer** — searchable list of every installed app (`QUERY_ALL_PACKAGES`), so the launcher
  never traps the driver.
- **Live map surface** — GPS position and speed, with deep links into Waze (`waze://`, `geo:`,
  `waze.com`).

**Permissions requested at first launch:** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+; the
older `BLUETOOTH`/`BLUETOOTH_ADMIN` pair is declared with `maxSdkVersion="30"`), fine/coarse
location for GPS speed and map centring, `POST_NOTIFICATIONS`, and the connected-device foreground
service permission.

---

## What the web dashboard does

A React 19 + TypeScript + Vite 8 + Tailwind 4 single-page cockpit (`npm run dev`, port 3000)

- **Left dock navigation** — Home, Gauges, Trip, Settings, matching the Android page set.
- **Real hardware only** — `obdEngine` connects through **Web Bluetooth** (BLE UART services for
  Veepeak/Vgate/HM-10/Nordic UART/OBDLink) or **Web Serial** (USB FTDI/CH340 at 38400 baud) and
  performs the same ELM327 handshake and PID polling as the Android client. Nothing is faked
  unless you explicitly start the bench simulation.
- **Home** — geolocation-driven map pane with speed alert threshold, route search and Waze hand-off,
  beside split gauges.
- **Gauges** — all eight sensors with live graph traces.
- **Diagnostics modal** — Mode 03 read / Mode 04 clear backed by a DTC knowledge base (SAE J2012
  style descriptions, symptoms, probable causes and a step-by-step diagnostic checklist).
- **Trip computer** — distance, duration, average and peak speed.
- **Settings** — speed unit, head-unit **resolution presets** (1024×600, 1280×720, 1920×720, or fluid)
  for testing Chinese Android head units in a desktop browser, plus the APK build guide.
- **Media dock** — local/USB track list and FM/AM presets with RDS text.

Browser support matters here: Web Bluetooth needs Chrome/Edge on desktop or Chrome on Android;
**Web Serial is desktop-only**, so USB cables will not work from Android Chrome.

---

## Architecture

**Android** (`android/app/src/main/java/com/sportage/obd/`)

- `MainActivity` — binds the service through `ObdViewModel`, mirrors its flows into Compose state,
  and owns permissions, GPS/trip tracking and the Bluetooth and app-drawer dialogs.
- `ui/CarLauncherScreen.kt` — the whole cockpit: status strip, split home, gauges, diagnostics, trip,
  media and settings pages.
- `bluetooth/BluetoothTransport.kt` — RFCOMM socket lifecycle, command write/read-until-`>` framing.
- `bluetooth/BluetoothDeviceInfo.kt` — permission-checked device name/MAC access.
- `obd/PidDecoders.kt` — SAE J1979 PID, battery voltage and DTC decoding.
- `service/ObdService.kt` — foreground `connectedDevice` service that owns the RFCOMM socket and the
  polling loop, with pause/resume actions, exponential-backoff reconnect, a persistent status
  notification and StateFlow telemetry.
- `repository/ObdRepository.kt` + `viewmodel/ObdViewModel.kt` — start/bind (and re-attach to) the
  service, add bench-demo telemetry and the graph history windows, and expose the flows the launcher
  renders.

**Web** (`src/`)

- `services/obdEngine.ts` — transport + handshake + polling singleton with telemetry/connection/log
  listeners, Web Bluetooth and Web Serial backends, and the bench simulator.
- `services/obdDecoders.ts`, `dtcDatabase.ts`, `vehicleProfile.ts` (2019 Sportage 2.0 MPI AWD, GCC
  maintenance schedule), `launcherApps.ts`, `inCarAudioService.ts`.
- `components/` — `launcher/`, `cockpit/`, `navigation/`, `media/`, `modals/` presentational pieces.

More detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Building & testing

### Android

Prerequisites: JDK 17, Android SDK platform 35 with build-tools 35.0.0. The Gradle 8.9 wrapper is
included, so no separate Gradle install is needed.

```bash
cd android
./gradlew lintDebug                      # Android Lint (zero errors expected)
./gradlew testDebugUnitTest              # 21 JVM unit tests: ELM327 framing + PID/DTC decoding
./gradlew assembleDebug                  # debug APK (package id com.sportage.obd.debug)
./gradlew bundleRelease                  # Play Store AAB (unsigned unless signing configured)
```

Outputs:

- `android/app/build/outputs/apk/debug/app-debug.apk` (package id `com.sportage.obd.debug`)
- `android/app/build/outputs/bundle/release/app-release.aab` — App Bundle for Google Play
- `android/app/build/outputs/apk/release/app-release-unsigned.apk` — **unsigned** unless signing is configured

Min SDK is 28 (Android 9), target and compile SDK 35.

#### Release signing

Release signing is configured via environment variables or `android/keystore.properties`.
See `app/build.gradle.kts` for the exact variable names (`SPORTAGE_KEYSTORE_FILE`,
`SPORTAGE_KEYSTORE_PASSWORD`, `SPORTAGE_KEY_ALIAS`, `SPORTAGE_KEY_PASSWORD`).
With no credentials the release build stays unsigned.

#### Play Store readiness

- `PRIVACY.md` — user-facing privacy policy (also linked from in-app Settings).
- `DATA_SAFETY.md` — Google Play Data Safety form content.
- Firebase Crashlytics + Analytics are integrated; `google-services.json` is required to
  enable them. A placeholder is committed so the build works without real credentials.
- R8/ProGuard is enabled for release builds; mapping.txt is generated automatically.
- In-app onboarding wizard and Privacy Policy screen are on the Settings page.

#### Upload to Play Console

```bash
# Upload mapping files for deobfuscated crash reports:
./gradlew crashlyticsUploadDistributionRelease

# Or manually upload the AAB from:
# android/app/build/outputs/bundle/release/app-release.aab
```

### Web dashboard

Node 20+ and a committed `package-lock.json`:

```bash
npm install
npm run dev      # http://0.0.0.0:3000
npm run lint     # tsc --noEmit
npm run build    # production bundle in dist/
```

### CI

- `.github/workflows/android.yml` — lint, unit tests, debug and release APK builds on pushes and pull
  requests to `main`/`master`, plus manual dispatch.
- `.github/workflows/build-apk.yml` — debug APK build for artifact download.

Both workflows are branch-filtered: work on a feature branch only runs CI once it is pushed to
`main`/`master` or opened as a pull request against them.

---

## Hardware notes

- **Android:** Bluetooth Classic (SPP) ELM327 v1.5+ adapter. Pair it in system settings first; the
  in-app dialog lists bonded devices and can open the terminal for diagnostics.
- **Web:** BLE ELM327 (iOS-style) or a USB serial cable; classic Bluetooth SPP is not reachable from
  browsers.
- The decoder set targets the Sportage's CAN 11-bit/500 kbaud bus but the standard PIDs work on most
  OBD-II compliant vehicles.

---

## User & operational manual

Installation, Bluetooth pairing PINs, launcher setup and troubleshooting: [docs/USER_GUIDE.md](docs/USER_GUIDE.md).
