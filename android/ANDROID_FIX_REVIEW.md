# Android Bluetooth and map repair

Build: `2026-11-05.g`, version `1.0.3`, version code `4`. The change set is scoped to `android/`; the React/Vite web application, its package files, and root APKs are unchanged.

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

The user's physical-device screenshot still showed only the old position-marker fallback after the browser-map changes. Browser-only rendering tests were therefore insufficient evidence that the embedded map would work on the target Android device. This build removes that architecture instead of claiming another browser setting will fix it.

- **Native renderer:** Mapsforge renders the embedded map directly through Android graphics. The HTML asset, JavaScript engine, WebKit dependency, embedded browser, and fake grid/marker fallback are deleted. No local dark filter or theme toggle is used.
- **Bundled Qatar streets:** The APK includes a 25,476,984-byte vector street map of Qatar, dated 22 October 2026. It is copied to private app storage on the IO dispatcher, then rendered locally. Viewing Qatar streets does not require internet, browser support, a tile-server response, an account, or an API key.
- **World coverage:** Outside the bundled map's bounding box, the native renderer switches to standard OpenStreetMap tiles over HTTPS. This worldwide mode requires internet. Its cache is separate from the offline vector cache and uses an identifying user agent.
- **GPS:** The marker follows valid native location fixes without reloading the map. Follow GPS recenters within the app rather than launching an external navigation app. Last-known location uses the newest permitted provider; absent/rejected providers do not block the other provider.
- **Lifecycle:** A native MapView occupies an explicit match-parent container. Render/cache/download resources are released when disposed. The asset is copied atomically, and a real initialization error is shown instead of a fake map.
- **Navigation cleanup:** The map's external-launch buttons are replaced by Follow GPS. Old deep-link/package routing and navigation-specific manifest queries are removed. Optional place searches use the OpenStreetMap website, not an external navigation app.
- **Attribution:** Visible OpenStreetMap credit links to the copyright/license information. The bundled dataset and renderer notices are in the APK assets.

Offline street coverage is Qatar, not the entire world. This is a location tracker, not turn-by-turn navigation or a paid map service.

The dataset comes from the [Qatar Mapsforge download](https://alternativaslibres.org/en/download_other.html?file=Mapsforge_Qatar.zip), listed on [Alternativas Libres](https://alternativaslibres.org/en/downloads-mf.php). OpenStreetMap data can be redistributed with attribution under the Open Database License, as explained in [OpenStreetMap copyright and license](https://www.openstreetmap.org/copyright). Native rendering uses [Mapsforge](https://github.com/mapsforge/mapsforge), version 0.25.0.

## Additional review fixes

- **VIN:** Continuation headers and ELM row prefixes are handled without injecting header characters into the VIN, including responses already flattened by the transport. Truncated VINs are rejected.
- **Readiness:** Incomplete flags are read from the proper high bits of byte B and byte D, rather than treating support bits as failures. Test fixtures were corrected to include the fourth data byte.
- **Freeze frame parser:** A final one-byte parameter is no longer discarded solely because a second data byte is absent.
- **Clock:** The displayed clock now updates rather than relying on a derived value with no observable time dependency.
- **Dependencies:** The unused Navigation Compose and WebKit dependencies are removed. Native Mapsforge renderer/reader/theme dependencies replace the browser-map stack.

## Verification

Run from the repository root:

```sh
cd android
bash ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The JVM suite contains 56 tests, including four native-map/data checks. The obsolete JavaScript map harness was removed along with the browser map. Lint completes with zero errors; non-blocking dependency/resource warnings remain.

The native-map tests validate the bundled file's length, binary header and SHA-256; parse it with the real map reader; confirm coverage at the user's screenshot coordinates and central Doha; confirm a London fix is outside the offline area; and read nonempty street geometry at the user's position. Invalid location fixes are rejected. Bluetooth regressions remain unchanged.

Physical head-unit, Bluetooth radio, and vehicle checks remain necessary. File-reader tests do not prove rendering or GPS reception on the user's physical device.

## Installation and device acceptance checks

The supplied APK is a debug/test build using the same local debug signing key as builds e/f, so it can update those builds without uninstalling or deleting app data. It has a different certificate from the old repository d APK; preserve data before considering removal of that old installation. A maintainer with the original key can instead rebuild for an in-place update of d.

- **Version:** Confirm build `2026-11-05.g` after installation.
- **Twelve-device list:** Tap only the actual ELM/OBD adapter. Exactly that row should show “Connecting...”; other rows should show disabled “Connect” actions.
- **Cancellation:** Cancel mid-connect, then select another device. No old attempt should reconnect later.
- **Invalid selection:** A laptop/stereo or unavailable adapter should end in a bounded, visible failure and permit a new selection.
- **Real adapter:** Verify adapter initialization and changing RPM/speed values; repeat once with the intended classic or BLE transport.
- **Map offline:** With internet disabled, Qatar streets should still render from the bundled dataset, with a native GPS marker and an OFFLINE label. No map-theme switch or local recoloring should exist.
- **Map tracking:** Move the device or inject a new GPS fix. The native marker and map center should follow; Follow GPS should recenter without leaving the app.
- **Outside Qatar:** With internet enabled, a position outside the offline map bounds should use worldwide online streets. The label must indicate internet is required.
- **Lifecycle:** Rotate the display, leave/return to the app, and reopen Bluetooth. Confirm map rendering and connection status remain consistent.
