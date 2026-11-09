# Theming

The SportageOBD cockpit is rendered from **resolved theme tokens**, not hardcoded colors or sizes.
A theme changes typography, shapes, spacing/density, motion, elevation/glow, iconography,
background, gauge presentation and the default widget layout. Switching is live: the whole
Compose tree restyles on the next frame, and the selection persists across restarts.

Everything lives in `android/app/src/main/java/com/sportage/obd/ui/theme/`:

| File | Purpose |
|---|---|
| `Theme.kt` | Public API used by screens: `CockpitThemes`, `LocalCockpitColors`, color accessors, `SportageObdTheme`. |
| `ThemeSpec.kt` | Versioned schema, strict JSON parser/serializer, contrast checks, Compose mapping. |
| `BuiltInThemes.kt` | Five complete built-in themes and cached resolution with user overrides. |
| `ImportedThemes.kt` | Validated registry and `SharedPreferences` persistence for imported themes. |
| `DriveTokens.kt` | Fixed driving-safety dimensions (touch targets, minimum gaps). Not themeable. |

## Schema (v2)

A theme is one JSON object (or a bundle of them under `"themes"`). All fields except `tokens`
are optional; `tokens` may omit any group, in which case it is inherited from `extends` or the
neutral defaults.

```json
{
  "version": "2.0",
  "id": "my-theme",
  "name": "My Theme",
  "description": "One-line description shown in the gallery.",
  "mode": "dark",
  "extends": "cyber-neon",
  "tokens": {
    "color": {
      "background": "#FF05070C", "surface": "#FF0C1220", "surfaceVariant": "#FF16233A",
      "primary": "#FFE8FBFF", "secondary": "#FFFF4FD8", "accent": "#FF22D3EE",
      "warning": "#FFFFC14D", "danger": "#FFFF4D6D", "success": "#FF4DFFB8",
      "textPrimary": "#FFE8FBFF", "textSecondary": "#FF8FA9C4", "outline": "#FF2A3F5C",
      "gaugeTrack": "#FF12203A", "gaugeFill": "#FF22D3EE",
      "chart": ["#FF22D3EE", "#FFFF4FD8", "#FF4DFFB8"]
    },
    "typography": {
      "fontFamily": "sans", "numericFont": "monospace",
      "displayLarge": 44, "displayMedium": 34, "headlineLarge": 25,
      "titleLarge": 22, "titleMedium": 18, "bodyLarge": 16, "bodyMedium": 14,
      "labelLarge": 14, "labelMedium": 12,
      "weightRegular": 400, "weightMedium": 600, "weightBold": 800,
      "letterSpacing": 0.04
    },
    "shape": { "cornerRadius": 4, "cardCornerRadius": 8, "borderWidth": 1, "gaugeStroke": 10 },
    "spacing": { "density": "compact", "itemGap": 8, "cardPadding": 10, "safeMargin": 12 },
    "motion": { "fast": 80, "normal": 140, "slow": 220, "easing": "linear" },
    "elevation": { "cardElevation": 0, "glowAlpha": 0.55 },
    "iconography": { "style": "outlined", "strokeWidth": 1 },
    "background": { "style": "gradient", "base": "#FF05070C", "accent": "#FF0E2A3A", "gradientAngle": 135, "pattern": "grid" },
    "gauge": { "style": "arc", "needle": "line", "tickCount": 32, "warnFraction": 0.72, "criticalFraction": 0.88 },
    "layout": { "launcherRows": 3, "launcherCols": 4, "widgetMinWidth": 132, "widgetMinHeight": 88 }
  },
  "widgetPresets": [
    ["rpm", "speed", "rpm_graph", "speed_graph", "coolant", "voltage", "gear"]
  ]
}
```

### Token groups

| Group | Fields (validated ranges) |
|---|---|
| `color` | `#RRGGBB` or `#AARRGGBB`; `chart` holds 1–8 colors |
| `typography` | families `sans`/`serif`/`monospace`/`cursive`; sizes 8–96sp; weights 100–900; letter spacing −0.1–0.2em |
| `shape` | radii 0–48dp; border 0–8dp; gauge stroke 0.5–16dp |
| `spacing` | density `compact`/`comfortable`/`spacious`; gaps 0–96dp |
| `motion` | durations 0–2000ms; easing `linear`/`ease`/`ease-in`/`ease-out`/`ease-in-out`/`spring` |
| `elevation` | card elevation 0–24dp; glow alpha 0–1 |
| `iconography` | `filled`/`outlined`; stroke 1–8dp |
| `background` | `solid`/`gradient`/`pattern`; bundled pattern name only; angle 0–360 |
| `gauge` | `radial`/`bar`/`digital`/`arc`/`minimal`; needle `tapered`/`line`/`dot`; 0–60 ticks; `warnFraction < criticalFraction` |
| `layout` | launcher grid 1–8 × 1–8; widget minimums 96–180 × 64–320dp; up to 6 preset boards × 12 widgets |

## Built-in themes

| Id | Name | Mode | Character |
|---|---|---|---|
| `cockpit-dark` (default) | Cockpit Dark | dark | Original cockpit palette; neutral token defaults |
| `minimal-daily` | Minimal Daily | light | Light surfaces, soft corners, spacious, digital gauges, minimal motion |
| `cyber-neon` | Cyber Neon | dark | Neon accents, monospace numerals, compact grid, arc gauges, glow, fast motion |
| `classic-analog` | Classic Analog | dark | Warm serif labels, radial dials, 60 ticks, large radii, slow motion |
| `offroad-rugged` | Off-Road Rugged | dark | Maximum contrast, thick borders, bold numerals, bar gauges, topographic pattern |
| `night-lowglare` | Night Low-Glare | dark | Dim amber palette, minimal gauges, slow motion, compact density |

The registry also keeps the historical color-only presets (`midnight`, `emerald`, `sunset`,
`rose`, `violet`, `arctic`, `slate`). They are lifted to full specs with neutral typography,
shape, motion and layout via `BuiltInThemes.fromLegacyColors`, so every preset is complete.

`res/raw/builtin_themes.json` mirrors the five token themes for documentation and as import
examples; `ThemeSpecTest` verifies the file validates and matches the Kotlin definitions.

## Resolution and inheritance

1. `CockpitThemes.byId(id)` selects a preset; unknown or null ids fall back to the default.
2. `imported themes` (validated) override built-ins by id.
3. `extends` merges token groups: anything the child omits comes from the parent, then from
   `ThemeDefaults`.
4. User overrides are applied last: accent, density, gauge style.
5. Results are cached (`BuiltInThemes.resolved`) keyed by id + overrides, so OBD frames never
   re-allocate tokens. Importing a theme invalidates the cache.

## Runtime application

`SportageObdTheme(themeId, accentOverride, densityOverride, gaugeStyleOverride) { ... }`:

- builds Material 3 `colorScheme`, `typography` and `shapes` from the resolved tokens;
- provides `LocalCockpitColors` (12 legacy accessors plus success/outline/gauge roles) and
  `LocalCockpitTokens` (`spec` + `motion`);
- honours reduce-motion: `ThemedMotion.duration()` returns 0 when the system animator scale is 0;
- keeps screen code unchanged — screens call `ElectricCyan()`, `PrimaryText()`, etc.

## User overrides and persistence

Stored in `SharedPreferences("sportage_obd_ui")` (DataStore is not a project dependency):

| Key | Meaning |
|---|---|
| `themeId` | Selected preset/imported theme id |
| `accentOverride` | `AARRGGBB` accent layered on the theme |
| `densityOverride` | `compact`/`comfortable`/`spacious` |
| `gaugeStyleOverride` | `radial`/`bar`/`digital`/`arc`/`minimal` |
| `imported_themes_v2` | Validated imported theme documents (max 20) |
| `fontScale` | Existing in-app font scale (0.85–1.5) |

Widget layouts and per-widget configuration live under `sportage_obd_boards` — see
[WIDGETS.md](WIDGETS.md). Missing or corrupt values fall back to defaults and are logged
(`Log.w`), never applied partially.

## Import / export and security

The Settings → Theme card can copy the active theme JSON to the clipboard and import it back
(same mechanism works for hand-written files). Every document is validated strictly:

- maximum 64 KB; malformed JSON rejected;
- `http://`, `https://`, `data:`, `file://`, `javascript:` rejected anywhere in the document;
- unknown top-level or token-group fields rejected (no arbitrary layout injection);
- ids limited to `a-z0-9-_`; names ≤ 60 chars; descriptions truncated to 240;
- colors must be hex; numbers must be in range; widget ids must be known catalog ids;
- primary text contrast below 3:1 against the background is rejected;
- imported themes are capped at 20 and stored only after validation.

## Accessibility

- `ThemeSpec.accessibilityWarnings()` checks primary/secondary text (4.5:1), gauge fill and
  critical color (3:1) against their surfaces; the theme card lists violations.
- System font scaling is respected; the existing in-app font scale composes on top.
- Reduce-motion zeroes theme animation durations.
- `DriveDimens` keeps 76dp touch targets and 24dp separation regardless of density.
- Light themes (`minimal-daily`, `arctic`, `slate`) support bright-sunlight use; `night-lowglare`
  minimises glare for night driving.

## Migration and fallback

- Existing `themeId`/`accentOverride` preferences are read unchanged; color-only ids remain valid.
- Unknown ids resolve to `cockpit-dark`; invalid imported documents are skipped individually.
- A failing theme resolution never crashes the app: the default spec is used and a warning logged.
- Widget layouts encoded with the legacy token string (`speed|rpm;;map|clock`) migrate to default
  per-widget configs on first load, preserving order.

## Tests

`android/app/src/test/java/com/sportage/obd/ThemeSpecTest.kt` covers schema validation, built-in
JSON parity, inheritance, override application, round-trip export/import, accessibilty warnings,
fallback and imported-theme persistence.
