# Sportage Android cockpit

This is an Operate surface: a working vehicle dashboard, not a promotional concept. Refine the incumbent cockpit identity rather than replacing native Android interaction patterns or changing the Bluetooth, GPS, diagnostics, or navigation flows.

## Visual direction

- **Hierarchy:** Map and vehicle speed are the primary content. Secondary readings use smaller, consistent type. Bluetooth status has one visible entry point rather than repeated badges.
- **Typography:** Use the native sans-serif Material type scale. Reserve monospace for actual diagnostic commands and bytes. Measurement numerals should remain stable as values change.
- **Color:** Preserve the existing dark cockpit with warmer charcoal-neutral surfaces, a restrained pale-cyan action accent, muted green connection state, and semantic amber/red warnings. Keep the actual map light; do not introduce a dark filter or theme selector.
- **Structure:** Use 12–20 dp internal spacing, 16 dp main surface corners, and flatter data groups instead of nested miniature cards. Controls live in dedicated layout slots rather than covering the map.
- **Touch:** Primary and navigation controls have at least 48 dp touch targets. Keep accessible labels on icon-only actions.

## Responsive behavior

- **Phone portrait:** Full-width header, map and vehicle surfaces stacked, and five visible bottom navigation destinations. More opens Diagnostics, Apps, and Setup through a native popup.
- **Head-unit landscape:** A labeled Material navigation rail and side-by-side map/vehicle surfaces. Navigation can scroll on short displays.
- **Compact telemetry:** Pair the speed readout with RPM, coolant, and battery, with load/throttle below. Keep the telemetry surface scrollable for short heights and increased system font sizes.
- **Safe areas:** Preserve the activity's existing safe-drawing window insets. No changes to system-navigation behavior.

## Truth and functional constraints

Retain all six page IDs and the app-drawer callback. Do not fabricate connection status, GPS availability, engine readings, a fixed polling rate, legal road limits, or routing data. Demo mode remains clearly identified. The user-set speed alert is labeled as an alert, not presented as an authoritative road sign.

Keep one icon-only external navigation action in the map header, preserving Android's installed-app selection and Just once/Always defaults. Do not force one navigation vendor or wrap the intent in a chooser that prevents persistent defaults.

## Verification

Use real Compose layout interaction tests and inspect simulated Android native-graphics output at portrait, head-unit, short-landscape, and enlarged-text sizes. These checks are not physical-device or emulator screenshots, and do not verify real Bluetooth hardware, navigation applications, refresh rates, or GPU drivers. The Impeccable source detector is an additional static check, not a native accessibility or rendering certification.
