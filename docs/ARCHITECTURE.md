# SportageOBD – System Architecture & Engineering Specification

## 1. Executive Overview

**SportageOBD** is a production-grade native Android automotive launcher and real-time OBD-II telemetry instrument cluster engineered specifically for Kia Sportage (and compatible CAN-bus) head units, tablets, and modern Android devices (such as the Samsung Galaxy S24 Ultra).

The system replaces monolithic activity-bound prototype code with a layered, service-oriented architecture ensuring uninterrupted data streaming, zero dropped UI frames, and crash resilience.

---

## 2. Layered Architectural Model

```
┌────────────────────────────────────────────────────────────────────────┐
│                          JETPACK COMPOSE UI                            │
│  CarLauncherScreen  │  AppDrawerDialog  │  BluetoothDialog  │  Cockpit │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │ StateFlow / Events
┌──────────────────────────────────▼─────────────────────────────────────┐
│                            PRESENTATION LAYER                          │
│                             ObdViewModel                               │
│        (Lifecycle-aware StateFlow exposure, metric averaging, graphs)  │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                             DATA REPOSITORY                            │
│                             ObdRepository                              │
│       (Single Source of Truth, Service Binding, Bench Simulation)      │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │ In-Process Binder / IPC
┌──────────────────────────────────▼─────────────────────────────────────┐
│                      BACKGROUND FOREGROUND SERVICE                     │
│                        ObdService (connectedDevice)                    │
│    (SupervisorJob + Dispatchers.IO, Ongoing Notification, Reconnect)   │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                         TRANSPORT & PROTOCOL                           │
│     BluetoothTransport (RFCOMM)     │     PidDecoders (Mode 01 / 03)   │
└──────────────────────────────────▲─────────────────────────────────────┘
                                   │
                                   ▼
                   ELM327 Bluetooth OBD-II Adapter
                                   │
                     Kia Sportage CAN Bus (ECU)
```

### Layer Responsibilities

1. **Jetpack Compose UI**:
   - Pure, stateless rendering layer observing reactive `StateFlow` primitives.
   - Dynamic responsiveness: adapts between landscape head unit layouts (62:38 map-to-gauge split with left dock) and portrait phone viewports (100% width stacked with bottom dock).
   - Embedded interactive Leaflet / Waze map bridge with JS location update triggers (`window.updateVehicleLocation(lat, lon)`), eliminating white-screen DOM reloads.
   - Integrated **App Drawer** so the launcher never traps the driver.

2. **Presentation Layer (`ObdViewModel`)**:
   - Manages UI state derivation and coroutine scopes tied to `viewModelScope`.
   - Maintains rolling telemetry buffers (e.g. 60-sample window for speed and RPM history graphs).
   - Computes trip computer aggregates (duration, distance, max speed, average speed).

3. **Domain / Repository Layer (`ObdRepository`)**:
   - Acts as the single source of truth for both live vehicle telemetry and virtual bench simulation.
   - Manages binding to `ObdService`.
   - Seamlessly transitions between physical hardware Bluetooth mode and indoor bench demo testing.

4. **Background Execution Layer (`ObdService`)**:
   - Declared as an Android Foreground Service with `android:foregroundServiceType="connectedDevice"`.
   - Runs in an isolated `SupervisorJob() + Dispatchers.IO` coroutine scope, completely independent of Activity lifecycles.
   - Maintains ongoing system notification (`sportage_obd_service_channel`) preventing Android OOM termination.
   - Implements automated exponential backoff reconnection (1s, 2s, 4s, 8s, up to 16s).
   - Supports a **Pause OBD** toggle to eliminate battery drain when parked with engine off.

5. **Transport & Protocol Layer (`BluetoothTransport`, `PidDecoders`)**:
   - Multi-tier RFCOMM socket establishment: Standard RFCOMM -> Insecure RFCOMM -> Reflection on Channel 1 (tailored for Chinese Android head units like TS10, UIS7862, MTK).
   - Active Bluetooth device discovery and bonding dialog.
   - Deterministic parsing of Mode 01 PIDs (010C, 010D, 0105, 010F, 0111, 012F, 0104), ATRV battery voltage, and Mode 03 / Mode 07 Diagnostic Trouble Codes (DTCs).

---

## 3. Concurrency & Threading Model

- **Main Thread (UI)**: Strictly dedicated to Compose rendering and user touch interaction. Zero Bluetooth I/O, socket operations, or blocking reads occur on the main thread.
- **Dispatchers.IO**: Sockets and command polling execute asynchronously. All ELM327 queries are paced with non-blocking `delay(30)` to maximize sampling rate (10–18 Hz) while avoiding CAN-bus buffer saturation.
- **Backpressure & Lifecycle**: Flows are exposed as immutable `StateFlow<T>`, ensuring immediate last-known value delivery without redundant re-emissions.

---

## 4. Android HOME Launcher Integration

- Manifest declarations include `android.intent.category.HOME` and `android.intent.category.DEFAULT`.
- `onBackPressedDispatcher` intercepts Back button events on the Home screen to prevent closing the launcher, ensuring standard automotive infotainment behavior.
- In-app **App Drawer** queries all installed launchable applications via `PackageManager.queryIntentActivities(...)` under the `QUERY_ALL_PACKAGES` permission, providing instant access to native radio, music, navigation, and settings apps.

---

## 5. Governance, diagnostics and newer feature modules

| Module | Responsibility |
|---|---|
| `diagnostics/Diagnostics.kt` | Consent gate + event allow-list in front of Firebase Analytics/Crashlytics. No-op unless the driver opted in **and** the build has `google-services.json`. Also holds `AppPreferences` (onboarding/consent flags). |
| `obd/ExtendedPids.kt` | UDS framing (`62`/`61` positive responses, `7F` negative responses), SAE J1979-2 `0xF400 + PID` mapping, a closed `PidFormula` catalogue (no expression evaluator, so no user code is executed), and manufacturer presets explicitly marked unverified. |
| `obd/Mode06Parser.kt` | SAE J1979 Mode 06 on-board monitor results: `06 00` bitmap decoding, multi-frame parsing, UAS-driven width/signedness/scale, pass window and margin bands. Polled by `ObdService` one TID per 400 ms slot. |
| `obd/ExtendedPids.kt` (`J1979Pid`) | The standardised J1979-2 `0xF400 + PID` block: DID and command derivation plus Mode 01 scaling for twelve live parameters. |
| `ui/MonitorTestsScreen.kt` | Full-screen Mode 06 results: per-monitor pass window, remaining margin, severity band, and the J1979-2 live parameter grid. Opened from the Diagnostics page. |
| `obd/HealthScore.kt` | Deterministic 0–100 health estimate with capped, explained deductions. Pure Kotlin, no Android dependencies. |
| `trips/TripRecorder.kt` | Speed-sample integration with a capped sample gap, 0–100 km/h timing, harsh-acceleration counting and eco score. Starts/stops with the adapter connection in `MainActivity`. |
| `trips/TripStore.kt` | Offline-first CSV history in the app's private storage; the same code path produces the shareable export. |
| `launcher/Settings.kt` | `PageId`, `LauncherSettings`, `DriverProfile`, the `KeyValueStore` seam, `SettingsRepository` and the versioned `SnapshotCodec` shared by preferences and backup files. No Android types, so the whole cockpit configuration is unit tested. |
| `launcher/Automation.kt` | `DriveModeController` (hysteresis with hold times), `AutoPauseController` (parked detection that never overrides a manual pause) and the `NightDim` window helper. |
| `ui/DriveModeScreen.kt` | The driving view: speed, gear, RPM bar, temperatures, critical alert. Swallows pointer events so the cockpit underneath cannot be operated blindly. |
| `ui/HomeOverview.kt` | Unified live-data strip above the home map: health, faults, trip, active driver and pinned apps. |
| `ui/CockpitSettings.kt` | Setup card for layout, automation, profiles and backup/import. |
| `ui/OnboardingScreen.kt`, `ui/InfoDialogs.kt` | First-run wizard (adapter, permissions, privacy consent) and the in-app privacy policy / help dialogs. The policy text is read from `assets/privacy_policy.md`, copied from `PRIVACY.md` at build time. |

Threading note: all of the above are synchronous and small; callers invoke the file-backed trip
store on `Dispatchers.IO`. Telemetry-derived state (health score, trip progress) is updated from the
existing telemetry collector on the main dispatcher because it is pure arithmetic over one sample.

Extended diagnostics (Mode 06 and J1979-2) are singled out deliberately: the ELM327 transport is one
socket with no request/response serialisation, so they are issued **only** from inside the existing
polling loop (one request per 400 ms, alternating between the two families) and never from a second
coroutine. A UI "scan now" sets a flag that the loop reads at its next slot; the loop also stamps a
completion timestamp, which is what clears the UI's scanning state even when the vehicle answers
nothing at all.
