# SportageOBD Intelligence

> Production-Ready Android Automotive HOME Launcher & OBD-II Diagnostic Cluster for Kia Sportage (and CAN-bus compatible vehicles).

[![CI Pipeline](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android.yml/badge.svg)](https://github.com/farazsocialh-jpg/sportageobd/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-brightgreen.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Service--Oriented%20MVVM-blue.svg)](docs/ARCHITECTURE.md)

---

## Highlights

- **Permanent Automotive Split Cockpit**: 62:38 landscape layout uniting an interactive GPS map (with Waze external link) beside live digital & analog instrument clusters.
- **Background Foreground Service**: `ObdService` (`android:foregroundServiceType="connectedDevice"`) keeps Bluetooth streaming active indefinitely across app switching.
- **Responsive Layout**: Seamlessly transitions between 16:9 / 16:10 automotive head units and portrait flagship devices (e.g. Samsung Galaxy S24 Ultra).
- **Integrated App Drawer**: Instant searchable drawer querying all installed head unit apps so the driver is never trapped in the launcher.
- **Pause OBD Feature**: One-tap toggle halts high-frequency polling when parked with the engine off to safeguard vehicle battery.
- **Active Bluetooth Discovery & Pairing**: Scans nearby unbonded OBD adapters with 1-click pairing right from the interface.
- **ECU Diagnostics**: Mode 03 DTC scanning with severity categorization, probable cause lookup, and Mode 04 code clearing.
- **Bench Demo Mode**: Built-in realistic ECU simulation for development and indoor demonstration.

---

## Architecture

SportageOBD follows a clean, decoupled service architecture:
- **UI**: Jetpack Compose with responsive `BoxWithConstraints`.
- **ViewModel**: `ObdViewModel` exposing lifecycle-aware `StateFlow` primitives.
- **Repository**: `ObdRepository` coordinating `ObdService` and simulation data.
- **Service**: `ObdService` foreground service with `SupervisorJob() + Dispatchers.IO` and exponential backoff reconnection.
- **Decoders**: `PidDecoders` for Mode 01 PIDs, battery voltage, and Mode 03 / 07 DTCs.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for full design documentation.

---

## Building & Testing

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK (API 34 / 35 with build-tools 34.0.0)
- Gradle 8.2+ (wrapper included)

### Run Unit Tests
```bash
cd android
./gradlew testDebugUnitTest
```

### Build Debug & Release APKs
```bash
cd android
./gradlew assembleDebug assembleRelease
```
Compiled APKs will be located in:
- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`

---

## Web Preview Dashboard

This repository also includes a React + TypeScript interactive preview dashboard for rapid UI iteration and Web Bluetooth testing:

```bash
npm install
npm run build
```

---

## User & Operational Manual

For hardware installation, Bluetooth pairing PINs, launcher setup, and diagnostic troubleshooting, refer to [docs/USER_GUIDE.md](docs/USER_GUIDE.md).
