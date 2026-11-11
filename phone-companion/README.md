# Sportage Companion

Android phone companion app (Jetpack Compose) for the SportageOBD head unit.

## Setup

1. HiveMQ Cloud: create a free cluster, set `HOST`, `USERNAME`, `PASSWORD` in
   `app/src/main/java/com/sportage/companion/data/mqtt/MqttConfig.kt` (or move to
   `gradle.properties`/secrets). Port 8883 TLS is used.
2. Google Maps: create an API key (Maps SDK for Android) and set `MAPS_API_KEY`
   in `gradle.properties` or env. The manifest reads it via placeholder.
3. Firebase Auth: add `google-services.json` to `app/` (or `src/debug`, `src/release`)
   for Email/Password and Google Sign-In. The google-services plugin is applied only
   when the file exists. Web client ID in `AuthScreen.kt` must be updated for Google
   Sign-In.
4. Build: `./gradlew :app:assembleDebug`.

## Architecture

- Clean-ish MVVM with Koin DI and unidirectional StateFlow.
- `CompanionDataRepository` owns the HiveMQ connection and exposes telemetry, parked
  location, connection status.
- Topics: `users/{userId}/vehicles/{vehicleId}/telemetry` and `.../parking` (QoS 1
  retained for parking), plus legacy `vehicles/sportage/*` for compatibility with the
  existing head unit app.
- Find My Car reads the retained parked GPS pin and offers walking navigation.