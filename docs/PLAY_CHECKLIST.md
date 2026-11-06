# Play Store release checklist

Everything Play Console asks for, mapped to what this repository already provides and what still
needs a human. Items marked **blocker** stop a release; the rest are quality gates.

## Repository — already done

- [x] Signed release **AAB**: `./gradlew bundleRelease` (signing via `RELEASE_KEYSTORE_*` secrets or
      `android/keystore.properties`)
- [x] R8 minification + resource shrinking, with `mapping.txt` archived by CI
- [x] `targetSdk`/`compileSdk` 35, `minSdk` 28
- [x] Privacy policy in-repo (`PRIVACY.md`) and a publishable web copy (`docs/privacy.html`)
- [x] In-app privacy policy and help, reachable from Settings
- [x] Data safety answers prepared (`docs/DATA_SAFETY.md`)
- [x] First-run onboarding with permission rationale
- [x] Opt-in, off-by-default analytics and crash reporting
- [x] `android:allowBackup="false"` so trip/vehicle data is not silently copied to cloud backup
- [x] Cleartext HTTP disabled (map tiles are HTTPS)
- [x] Lint gates CI with zero errors; 89 JVM unit tests

## Console — needs a human

### Blockers

- [ ] **Privacy policy URL.** Publish `docs/privacy.html`: enable GitHub Pages for the repository
      with the `/docs` folder (Settings → Pages), which yields
      `https://farazsocialh-jpg.github.io/sportageobd/privacy.html`. Paste that URL into
      *App content → Privacy policy*.
- [ ] **Data safety form.** Transcribe `docs/DATA_SAFETY.md`. Note that location/Bluetooth data is
      processed on-device only, so it is *not* "collected" for the form.
- [ ] **Content rating questionnaire.** Category: Utility/Productivity. Expect: no user-generated
      content sharing, no ads, no purchases. The app does read vehicle diagnostics, which is not one
      of the questionnaire's harm categories.
- [ ] **Data deletion request URL / statement.** No account exists; state that all data is local and
      removed by clearing app storage or uninstalling.
- [ ] **Sensitive permission declarations:** `QUERY_ALL_PACKAGES` (launcher app drawer) and
      `FOREGROUND_SERVICE_CONNECTED_DEVICE` (persistent Bluetooth OBD link). Justification text is in
      `docs/DATA_SAFETY.md`.
- [ ] **Play App Signing** enrolment, and upload the AAB to an internal testing track first.

### Store listing

- [ ] App name and short description (80 chars). Suggested: *Car launcher + live OBD-II dashboard*.
- [ ] Full description — reuse the "What the Android app does" section of `README.md`.
- [ ] Feature graphic 1024×500, phone screenshots (min 2, 16:9 or 9:16), 7"/10" tablet screenshots.
- [ ] App icon 512×512 PNG (the adaptive icon assets in `res/mipmap-*` are not a store icon).
- [ ] Category: Personalisation (launcher) or Auto & Vehicles; both are defensible — pick based on
      which role is primary in the listing.
- [ ] Contact email (required by Play; the privacy policy currently points at GitHub issues).
- [ ] 3–5 "What's new" bullets for version 1.0.0.

### Testing tracks

- [ ] Internal testing upload → install from the Play link on a device with an ELM327 adapter.
- [ ] Verify: first-run onboarding, permission denial path, adapter connect, telemetry, DTC scan,
      trip auto-start/stop, CSV and trip export, launcher as default home, upgrade from debug build.
- [ ] Closed testing (12 testers / 14 days) if the account is a new personal developer account.
- [ ] Production rollout at 20% before 100%.

## Known gaps that a reviewer may notice

- The app requests `QUERY_ALL_PACKAGES`; Play policy allows it for launchers, but the declaration must
  be filed or the release is rejected.
- Manufacturer PID presets are labelled experimental; do not describe them as guaranteed in the
  store listing.
- Accessibility and localisation are not complete (see `docs/ROADMAP.md`); neither blocks publishing,
  but both are review-quality issues.
