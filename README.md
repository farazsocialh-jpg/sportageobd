# SportageOBD Intelligence

An Android head-unit launcher that doubles as a live OBD-II instrument cluster, plus a browser
cockpit for the same vehicle — built around a Kia Sportage (QL) 2.0 MPI AWD on ISO 15765-4 CAN.

The repo ships two separate front ends over the same idea: talk to a real ELM327 adapter, decode
Mode 01 PIDs, and show the result on a screen designed to be glanced at while driving.

[![CI Pipeline](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android-build.yml/badge.svg)](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android-build.yml)
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
- **First-run onboarding** — four short screens: the two-in-one idea, adapter pairing, permission
  rationale, and an explicit (off-by-default) privacy choice.
- **Vehicle health score** — bounded 0–100 estimate from battery voltage, coolant, fuel trims,
  stored codes and readiness monitors, with every deduction explained on the Gauges page.
- **Automatic trip log** — every drive with the adapter connected records distance, duration, max and
  average speed, 0–100 km/h time, fuel used (when the ECU reports it) and an eco score; history and
  CSV export live on the Trip page.
- **Extended protocol decoders** — SAE J1979-2 (`0xF400 + PID`) DID mapping, Mode 22/21 UDS framing
  with manufacturer presets marked experimental, and a Mode 06 on-board monitor parser. These are
  unit-tested; the polling loop does not request them yet (see [docs/ROADMAP.md](docs/ROADMAP.md)).
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
./gradlew lintDebug            # Android Lint (CI fails on any error)
./gradlew testDebugUnitTest    # 89 JVM unit tests: ELM327 framing, PID/DTC decoding,
                               # Mode 06, UDS/J1979-2, health score, trips, trip CSV store
./gradlew assembleDebug assembleRelease bundleRelease
```

Outputs:

- `android/app/build/outputs/apk/debug/app-debug.apk` (package id `com.sportage.obd.debug`)
- `android/app/build/outputs/apk/release/app-release.apk` — signed when credentials are configured,
  otherwise `app-release-unsigned.apk`
- `android/app/build/outputs/bundle/release/app-release.aab` — the Play Store upload
- `android/app/build/outputs/mapping/release/mapping.txt` — R8 mapping for de-obfuscating crashes

Release builds run R8 with resource shrinking (the debug APK is ~58 MB, the shrunk release APK is
~4 MB). `SPORTAGE_VERSION_CODE` / `SPORTAGE_VERSION_NAME` override the version from the environment,
which is how CI keeps the Play versionCode monotonic. Signing, AAB and mapping details:
[android/SIGNING.md](android/SIGNING.md).

Min SDK is 28 (Android 9), target and compile SDK 35, version 1.0.0.

### Privacy and diagnostics

No account, no vendor backend, and **no telemetry unless the driver opts in**. Vehicle data, trips
and scans stay on the device. Analytics and crash reporting are consent-gated and inert unless the
build also has a `google-services.json` — see [docs/ANALYTICS.md](docs/ANALYTICS.md).

- [PRIVACY.md](PRIVACY.md) — the policy (also bundled into the APK and shown in Settings)
- [docs/privacy.html](docs/privacy.html) — publishable public copy for the Play listing
- [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) — Data safety form answers
- [docs/PLAY_CHECKLIST.md](docs/PLAY_CHECKLIST.md) — release checklist
- [docs/ROADMAP.md](docs/ROADMAP.md) — what is done, partial, or not started

### Web dashboard

Node 20+ and a committed `package-lock.json`:

```bash
npm install
npm run dev      # http://0.0.0.0:3000
npm run lint     # tsc --noEmit
npm run build    # production bundle in dist/
```

### CI

`.github/workflows/android-build.yml` runs on pushes and pull requests to `main`/`master`, on `v*`
tags, and on manual dispatch:

1. Android Lint (a lint error fails the build)
2. JVM unit tests
3. debug APK, release APK and release AAB (signed when `RELEASE_KEYSTORE_*` secrets exist)
4. R8 `mapping.txt` uploaded as an artifact, and pushed to Crashlytics when
   `GOOGLE_SERVICES_JSON_BASE64` is configured
5. lint/test reports uploaded even when a step fails

Tag builds attach the APK and AAB to a GitHub Release and refuse to publish unsigned artifacts.

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
