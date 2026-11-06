# Play Console — Data safety answers

This file mirrors the answers to enter in **Play Console → App content → Data safety** for
`com.sportage.obd`. It is derived from the app's actual behaviour (see `PRIVACY.md`); update both
documents together when data handling changes.

## Does your app collect or share any of the required user data types?

**Yes** — but only when the user opts in to diagnostics. The default build collects nothing
off-device.

| Data type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Approximate location | No (device-only) | No | — | — | Coordinates are used in-app and included in map-tile requests, which Google's guidance treats as service-provider processing, not collection for the developer |
| Precise location | No (device-only) | No | — | — | Same as above |
| Personal info (name, email, phone, address, IDs) | No | No | — | — | No account system |
| Financial info | No | No | — | — | No payments |
| Health & fitness | No | No | — | — | — |
| Messages, contacts, calendar, photos, files, audio | No | No | — | — | — |
| App activity (in-app actions) | Yes | No | **Yes** | Analytics | Only if *Share anonymous diagnostics* is on: coarse events (`onboarding_completed`, `obd_connect_success/failure`, `dtc_scan_completed`, `page_view`) |
| App info & performance (crash logs, diagnostics) | Yes | No | **Yes** | Crash reporting | Only if diagnostics are on: stack trace, app version, device model, Android version, random install ID |
| Device or other IDs | Yes | No | **Yes** | Analytics / crash reporting | Firebase installation ID only |
| Vehicle data (VIN, DTCs, live PIDs) | No | No | — | — | Processed and stored on-device only; never uploaded |

## Data handling details

- **Encrypted in transit:** Yes — all optional reporting and map traffic uses HTTPS/TLS.
- **Data deletion request:** The user can turn diagnostics off and clear app storage at any time;
  no account exists, so there is no server-side profile to delete. Crashlytics data expires under
  Firebase's standard retention.
- **Committed to the Play Data safety declaration:** *"Data is encrypted in transit"*, *"You can
  request that data be deleted"* (via clearing storage / contacting the developer), and *"No data
  shared with third parties"*.

## Foreground service declaration

`ObdService` is declared with `android:foregroundServiceType="connectedDevice"` and is used to keep
the Bluetooth OBD link and telemetry streaming while the user navigates in another app. Play Console
requires the "Connected device" justification:

> Maintains an active Bluetooth connection to the user's OBD-II adapter so the driving dashboard
> keeps receiving telemetry while the user runs a navigation or media app in the foreground.

## `QUERY_ALL_PACKAGES` declaration

Used only to build the launcher's app drawer (the app is a `CATEGORY_HOME` launcher). Play requires
a declaration for this permission; the justification is:

> SportageOBD is a home-screen replacement (launcher). It must enumerate installed applications so
> the driver can open any app from the car dashboard. It does not query packages for advertising,
> analytics or profiling.

The limitation is genuine: if the permission is removed, the app drawer stops working, so it cannot
be narrowed to a handful of packages without removing a core launcher feature.

## Permissions that trigger a Play review

- `QUERY_ALL_PACKAGES` — see above.
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` — see above.
- `BLUETOOTH_SCAN` declares `usesPermissionFlags="neverForLocation"`, so it is not treated as a
  location permission.
