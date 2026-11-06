# Delivery status and roadmap

This file is the honest ledger of the production-readiness work: what is implemented, what is
partially implemented, and what has not been started. Anything marked *partial* has working code
and tests where the logic is testable, but is not yet connected to a real device or UI end to end.

Last updated: 6 November 2026.

---

## Phase 1 — Governance & Play Store compliance

| Item | Status | Where |
|---|---|---|
| Privacy policy | **Done** | `PRIVACY.md`, public copy at `docs/privacy.html` |
| In-app privacy policy link | **Done** | Settings → Privacy & diagnostics; onboarding step 3 |
| Data safety answers | **Done** | `docs/DATA_SAFETY.md` |
| Signed AAB for release | **Done** | `bundleRelease`; signing from env or `keystore.properties` |
| R8 + ProGuard keep rules | **Done** | `isMinifyEnabled`/`isShrinkResources`; `proguard-rules.pro` |
| `mapping.txt` generated and uploaded | **Done** | CI artifact `sportage-obd-mapping`; Crashlytics upload when configured |
| targetSdk/compileSdk 35 | Already satisfied | `app/build.gradle.kts` |
| QUERY_ALL_PACKAGES justification | **Done** | `docs/DATA_SAFETY.md` (launcher app drawer) |
| Crash reporting | **Done, gated** | Firebase Crashlytics, dormant without `google-services.json` and consent — see `docs/ANALYTICS.md` |
| Analytics with consent | **Done** | `diagnostics/Diagnostics.kt`, off by default |
| Unit tests for PID decoding / fallbacks / service lifecycle | **Partly done** | PID and ELM327 framing covered (89 tests total); connection fallback and service lifecycle still have no automated coverage — they need an emulator or hardware |
| CI: lint, tests, AAB, artifacts | **Done** | `.github/workflows/android-build.yml` |
| Onboarding wizard | **Done** | `ui/OnboardingScreen.kt` |
| Contextual help | **Done** | Settings → Help & quick start (`ui/InfoDialogs.kt`) |

## Phase 2 — OBD & diagnostics

| Item | Status | Notes |
|---|---|---|
| Manufacturer Mode 22/21 decoding | **Partial** | `obd/ExtendedPids.kt`: UDS framing, formula catalogue, five brand presets, custom PID model. The presets are explicitly **unverified** — DIDs differ per model year and module, and confirming them needs a real car. No UI editor for custom PIDs yet. |
| OBDonUDS (SAE J1979-2) | **Done, unverified on hardware** | Standardised `0xF400 + PID` DIDs are polled from `ObdService` every 400 ms, decoded by `J1979Pid` and shown on the monitor-tests screen. DIDs the ECU refuses are cached and not asked again. Only the standardised `0xF400` block is read; manufacturer DIDs still need the editor below. |
| Mode 06 on-board monitor tests | **Done, unverified on hardware** | `ObdService` reads the `06 00` supported-test bitmap, then polls one test per slot (falling back to a curated probe list when no bitmap is published), caching TIDs the ECU rejects. Diagnostics → *On-board monitor tests* plots each value inside its pass window with the remaining margin. Parsing, bitmap decoding, labels and severity bands are unit tested; no ECU has been attached. |
| AI explanations | **Not started** | Requires an API key mechanism and a privacy review; deliberately deferred rather than half-wired. |
| Shareable PDF reports | **Not started** | CSV export of trips exists today. |
| Health score | **Done** | `obd/HealthScore.kt` + score strip on the Gauges page; every deduction is explained. |
| Trend graphs | **Partial** | Live 60-sample speed/RPM history already existed; no persisted long-term trends. |
| Predictive maintenance notifications | **Not started** | Depends on persisted trends. |
| Automatic trip logging | **Done** | `trips/TripRecorder.kt`; starts/stops with the adapter connection. |
| Offline-first trip storage | **Done, not Room** | `trips/TripStore.kt` stores CSV in the app directory. Room was traded for CSV because the only queries are "list" and "export", and it keeps the export path identical to the storage path. If Room is required later, `TripStore` is the single seam to replace. |
| Trip history screen + CSV export | **Done** | Journey history strip on the Trip page, share via `FileProvider`. |

## Phase 3 — Launcher & cockpit

Not started: Drive Mode, infotainment widgets, deep customisation (icon packs, stacks, grid sizes,
gestures), automation rules. The current launcher already has a fixed six-page cockpit, app drawer,
media/radio page and a map pane; these items are additive and need product decisions (for example
whether "Drive Mode" is speed-triggered from OBD or from a foreground-service state machine).

## Phase 4 — Two-in-one synergy

Not started: unified live-data home page, driver profiles, cloud sync, crowdsourced repair database.
Cloud sync in particular needs a backend choice and a Play Data safety update before any code.

## Phase 5 — Final polish and release

| Item | Status |
|---|---|
| Accessibility pass (content descriptions, contrast, touch targets) | **Not started** — this needs an audit with TalkBack and is the highest-value remaining polish item |
| Localisation | **Not started** — all copy is currently English literals; extraction to `strings.xml` is a prerequisite |
| Store listing assets, content rating questionnaire | **Not started** — checklist in `docs/PLAY_CHECKLIST.md` |
| Physical-device regression run | **Not possible in this environment** — no Android device or OBD adapter is attached |

## Known limitations of the current build

- The health score, trip recorder and Mode 06/22 parsers are validated by unit tests only. They have
  not been exercised against a real ECU. Mode 06 and J1979-2 are wired into the polling loop and the
  UI, and exercise themselves against the built-in demo ECU, but a real vehicle may still reveal
  bitmap or multi-frame differences that no fixture predicts.
- Manufacturer PID presets are best-effort starting points, labelled "experimental" in the UI; treat
  them as untrusted until confirmed on the specific vehicle.
- No instrumented (androidTest) suite exists yet, so service lifecycle and permission flows are
  unverified automatically.
