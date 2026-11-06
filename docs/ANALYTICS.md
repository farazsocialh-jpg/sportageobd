# Diagnostics: analytics and crash reporting

SportageOBD ships with **no active telemetry**. Analytics and crash reporting exist in the code but
do nothing until two independent switches are on:

1. **A Firebase project is configured for the build** (`android/app/google-services.json`), and
2. **The driver opts in** (*Settings → Privacy & diagnostics*, or the onboarding switch).

If either is missing the app behaves exactly as before: no network calls, no identifiers.

## What is collected when consent is given

| Event | Parameters | Why |
|---|---|---|
| `onboarding_completed` | none | Funnel completion |
| `page_view` | `page` (0–5 integer) | Which cockpit pages are actually used |
| `obd_connect_success` / `obd_connect_failure` | none | Adapter compatibility |
| `dtc_scan_completed` | `code_count`, `mil_on` | Whether the diagnostic path works |
| `trip_recorded` | `distance_km` (int), `minutes` | Trip logging reliability |
| `custom_pid_added`, `ai_explanation_requested` | none | Reserved for those features |

Crashlytics receives stack traces plus a random installation ID. **Never** sent: VIN, adapter MAC
address, coordinates, trip contents, DTC text, or anything typed by the user. The tag list is
enforced by construction: `Diagnostics.log` only accepts primitives and callers pass only the keys
above.

## Turning it on for a build

1. Create a Firebase project and add an Android app with package name `com.sportage.obd`
   (and `com.sportage.obd.debug` if you want debug-build reporting).
2. Download `google-services.json` into `android/app/`.
3. Rebuild. The Gradle file detects the file and applies the `google-services` and
   `firebase-crashlytics` plugins; without it those plugins are not applied at all, so no
   configuration is required for forks, CI or local development.
4. Upload the R8 mapping for every release so stack traces de-obfuscate:

   ```bash
   ./gradlew uploadCrashlyticsMappingFileRelease
   ```

   CI does this automatically when `GOOGLE_SERVICES_JSON_BASE64` repository secret is set, and
   always uploads `mapping.txt` as a build artifact.

The base64 secret is created with:

```bash
base64 -w0 android/app/google-services.json   # add as GOOGLE_SERVICES_JSON_BASE64
```

Note that `google-services.json` itself is a public client identifier, not a secret, but it ties the
build to one Firebase project, so it is kept out of the repository to avoid accidental cross-reporting
from forks.

## Where the code lives

- `android/app/src/main/java/com/sportage/obd/diagnostics/Diagnostics.kt` — consent gate, event
  allow-list, Firebase initialisation wrapped in `runCatching` so a build without Firebase cannot
  crash, and `AppPreferences`.
- `android/app/src/main/java/com/sportage/obd/SportageObdApp.kt` — reads consent before any UI and
  installs the crash handler.
- `android/app/src/main/java/com/sportage/obd/MainActivity.kt` — the call sites.

## Revoking consent

`Diagnostics.setEnabled(context, false)` persists the choice, disables Crashlytics collection,
deletes unsent reports and clears the in-memory SDK references. The switch in Settings calls it
immediately, so turning diagnostics off does not require a restart.
