# Android Bluetooth and map repair

Build: `2026-11-05.f`, version `1.0.2`, version code `3`. The change set is scoped to `android/`; the React/Vite web application, its package files, and root APKs are unchanged.

## Bluetooth findings and corrections

- **Stable connection state:** The service previously exposed the current transport's flow through a getter, then replaced the transport. Existing repository collectors could remain subscribed to the obsolete flow. A lifetime-stable relay now follows the active transport and rejects stale emissions.
- **One selected device:** Paired and BLE rows share address-scoped presentation logic. A test with twelve devices requires exactly one “Connecting...” label and disables all additional connection attempts.
- **Single-flight and cancellation:** Repository and service guards replace the activity's arbitrary 1.2-second unlock. Rejected duplicate requests restore actual service state. Cancel clears pending binds, cancels connection/reconnection jobs, closes the pending socket/GATT, and prevents late callbacks from reviving an obsolete connection.
- **Classic Bluetooth:** Socket attempts retain bounded deadlines, are cancellation-aware, close failed sockets, and handle permission revocation. Commands are serialized so polling, diagnostics, and terminal traffic cannot consume one another's replies. Missing prompts time out as link failures rather than appearing to be successful empty responses.
- **BLE interoperability:** Standard short UUIDs are normalized correctly. Both legacy Android and Android 13+ notification callbacks are implemented. Notification/indication setup waits for the descriptor acknowledgement before initialization; writes are serialized, checked, and chunked for the default MTU. Response timeouts trigger link-error handling instead of silently cancelling polling.
- **Pairing and scanning:** Bond broadcasts are filtered to the selected MAC. The receiver is registered before bonding begins, pairing has a timeout, scans stop before connecting, and BLE scans expire. Receiver flags account for Bluetooth broadcasts from the platform.
- **Recovery and cleanup:** A failed manual selection stops with an error instead of looping forever against a laptop/car stereo. Recovery from a dropped established link is capped at five attempts with bounded backoff. Telemetry and adapter-specific capability data reset on a new selection; log/tone resources close appropriately.
- **Small screens:** Device actions wrap instead of squeezing labels across several lines, and long paired-device names no longer crowd out the connection button.

## Map findings and corrections

The bundled JavaScript engine initialized during testing, but visual inspection found that the previous dark tile endpoint returned a successful image saying “API KEY REQUIRED.” A successful HTTP image response does not fire Leaflet's tile-error handler, so provider-fallback logic cannot detect that failure.

- **Street tiles:** The map uses standard, unmodified OpenStreetMap street tiles. Per the user's correction, no local dark filter or map-theme toggle remains. The key-gated tile dependencies and remote embed route are removed.
- **Android loading:** Bundled content is served through `WebViewAssetLoader` on a local HTTPS origin rather than a `file://` page. Arbitrary in-app navigation, file/content access, mixed content, and automatic JavaScript geolocation grants are disabled.
- **Layout and lifecycle:** Explicit map edges/size support older WebViews; resize invalidates Leaflet's viewport; Android releases destroyed WebViews. Telemetry recompositions no longer navigate/reload a remote embed.
- **GPS:** Initial coordinates are supplied before the first tile request, subsequent fixes update the marker, invalid coordinates are rejected, and unchanged positions do not continually pan. Last-known location uses the newest permitted provider, absent/rejected providers do not block the other provider, and activity location subscriptions are cleaned up.
- **Visible failures:** Loading has a deadline. With no street tiles, the UI explicitly says to check internet and retains coordinates; it does not pretend a blank grid is a street map. It retries automatically and on connectivity restoration. Open Maps remains available.
- **Attribution:** OpenStreetMap credit remains visible with a copyright link.

Street tiles still require internet. This repair does not add downloaded offline street maps, turn-by-turn navigation, or a paid tile service.

## Additional review fixes

- **VIN:** Continuation headers and ELM row prefixes are handled without injecting header characters into the VIN, including responses already flattened by the transport. Truncated VINs are rejected.
- **Readiness:** Incomplete flags are read from the proper high bits of byte B and byte D, rather than treating support bits as failures. Test fixtures were corrected to include the fourth data byte.
- **Freeze frame parser:** A final one-byte parameter is no longer discarded solely because a second data byte is absent.
- **Clock:** The displayed clock now updates rather than relying on a derived value with no observable time dependency.
- **Dependencies:** The unused Navigation Compose dependency was removed; WebKit is included for the Android asset loader.

## Verification

Run from the repository root:

```sh
node android/tools/test-map.cjs
cd android
bash ./gradlew testDebugUnitTest assembleDebug lintDebug
```

Expected suite totals: 52 JVM unit tests and 16 Android-map regression checks. Lint completes with zero errors; non-blocking dependency/resource warnings remain.

The map harness covers layout compatibility, initialization at the supplied fix, invalid coordinates, unchanged GPS updates, visible network failures, stale callbacks, network restoration, viewport resizing, attribution, absence of local dark styling, and avoidance of key-gated tile URLs. Bluetooth tests cover the twelve-device reproduction, case-insensitive address matching, flow identity across SPP/BLE replacement, stale callbacks, cancellation, retryable row presentation, and UART UUIDs.

Browser verification uses the actual bundled Android asset at a 360 × 380 viewport, not the repository's web application. The standard street map is retained without local recoloring. Earlier network-failure checks verified that blocking tile requests produced the explicit offline notice and restoring connectivity returned to street rendering.

Hardware checks remain necessary: no physical Android head unit, Bluetooth radio, or ELM327 adapter is attached here. Unit tests and browser rendering do not prove pairing, RFCOMM quirks, GATT behavior, GPS reception, or vehicle-specific PID support on the user's hardware.

## Installation and device acceptance checks

The supplied APK is a debug/test build, not a production release signed with an established release key. Its signing certificate was compared with the repository's `2026-11-05d` APK and differs, so it cannot replace that APK in place. Preserve/export any logs and settings before considering removal of the old build; uninstalling deletes its local data. A maintainer with the original signing key can instead rebuild this source with that key for an in-place update.

- **Version:** Confirm build `2026-11-05.f` after installation.
- **Twelve-device list:** Tap only the actual ELM/OBD adapter. Exactly that row should show “Connecting...”; other rows should show disabled “Connect” actions.
- **Cancellation:** Cancel mid-connect, then select another device. No old attempt should reconnect later.
- **Invalid selection:** A laptop/stereo or unavailable adapter should end in a bounded, visible failure and permit a new selection.
- **Real adapter:** Verify adapter initialization and changing RPM/speed values; repeat once with the intended classic or BLE transport.
- **Map online:** With internet available, standard street tiles should render with the position marker at the current fix. There should be no Dark Map switch or local map recoloring.
- **Map offline:** Disable internet and move the map to uncached streets. An explicit no-tiles message should appear. Restore internet and confirm automatic recovery.
- **Lifecycle:** Rotate the display, leave/return to the app, and reopen Bluetooth. Confirm map rendering and connection status remain consistent.
