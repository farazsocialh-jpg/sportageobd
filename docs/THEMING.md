# SportageOBD Theming System

## Overview

The theming system provides a complete, production-ready solution for changing the visual style, layout, typography, shapes, motion, and widget composition of the SportageOBD cockpit. Themes can be switched live without app restart, and user overrides persist across restarts.

## Theme Schema

The theme schema is stored in `android/app/src/main/res/raw/theme_schema.json` and defines the complete set of tokens that all UI reads from. The schema supports versioning, inheritance (base theme + variants), and strict validation.

### Schema Version 2.0

Required top-level fields:

| Field | Type | Description |
|-------|------|-------------|
| `version` | `string` | Schema version: "1.0", "1.1", or "2.0" |
| `mode` | `string` | Color mode: "light", "dark", or "system" |
| `tokens` | `object` | All visual tokens (color, typography, shape, spacing, elevation, motion, iconography, background, gauge, layout) |
| `overrides` | `object` | Per-theme or per-user overrides on top of base tokens |
| `preview` | `object` | Thumbnail and description for the theme gallery |

### Color Tokens

All colors are hex strings `#RRGGBB` or `#AARRGGBB`. The following roles are defined:

- `background` - Main cockpit background
- `surface` - Surface cards and panels
- `surfaceVariant` - Alternative surface shade
- `primary` - Primary accent color
- `secondary` - Secondary accent color
- `accent` - Highlight/active color
- `warning` - Warning amber color
- `danger` - Critical red color
- `success` - Success green color
- `textPrimary` - Primary text color
- `textSecondary` - Secondary text color
- `outline` - Outline/stroke color
- `gaugeTrack` - Gauge background track
- `gaugeFill` - Gauge fill color
- `chart1`, `chart2`, `chart3` - Chart series colors

### Typography

- `fontFamily`: "system", "monospace", "roboto", or "inter"
- `sizes`: displayLarge, displayMedium, headlineLarge, titleLarge, titleMedium, bodyLarge, bodyMedium, labelLarge, labelMedium (in sp)
- `weights`: thin (100) through black (900)
- `letterSpacing`: float value
- `numericFont`: "system", "monospace", or "digital-7"

### Shape

- `cornerRadius`: number (dp)
- `borderWidth`: number (dp)
- `gaugeStroke`: number (dp)

### Spacing

- `compact`, `comfortable`, `spacious` (all in dp)

### Motion

- `durationFast`: ms (50-1000)
- `durationNormal`: ms (100-2000)
- `durationSlow`: ms (200-5000)
- `easing`: "linear", "ease", "ease-in", "ease-out", "ease-in-out", or "cubic-bezier"

### Iconography

- `iconSet`: "material-icons", "material-outlined", or "custom"
- `strokeWidth`: number (dp)
- `style`: "filled", "outlined", "rounded", or "twotone"

### Background

- `style`: "solid", "gradient", "image", or "pattern"
- `gradientColors`: array of 2-3 hex color strings
- `imageResource`: drawable resource name
- `safeAreaScrim`: hex color string with alpha for head-unit safe areas

### Gauge Styles

- `style`: "radial", "bar", "digital", "arc", or "minimal"
- `needleStyle`: "triangle", "arrow", "point", or "minimal"
- `tickStyle`: "none", "short", "long", or "numbered"
- `warningZones`: array of {min, max, color} zones
- `stylePreset`: "performance", "analog", "minimal", "offroad", or "night"

### Layout

- `preset`: "minimal", "performance", "analog", "offroad", or "night"
- `launcherGrid`: rows, cols, cellWidth, cellHeight (dp)
- `clusterSlot`: widthRatio, heightRatio (0.0-1.0)
- `safeMargins`: top, bottom, left, right (dp)

## Built-in Themes

Five distinct built-in themes are provided in `android/app/src/main/res/raw/builtin_themes.json`:

### 1. minimal-daily

- **Mode**: system
- **Description**: Clean, uncluttered cockpit focused on essential readings. Optimized for daily commuting with subtle contrasts.
- **Preset**: Minimal layout
- **Grid**: 2 rows × 3 cols launcher
- **Gauge**: Radial with arrow needle, short ticks
- **Colors**: Dark background with cyan accent

### 2. cyber-neon

- **Mode**: dark
- **Description**: High-contrast neon aesthetic inspired by performance car dashboards. Bold cyan accents on dark background.
- **Preset**: Performance layout
- **Grid**: 3 rows × 4 cols launcher
- **Gauge**: Radial with numbered ticks, performance preset
- **Colors**: Deep black with electric cyan and magenta accents

### 3. classic-analog

- **Mode**: dark
- **Description**: Timeless analog-inspired design with warm tones. Resembles traditional automotive instrumentation.
- **Preset**: Analog layout
- **Grid**: 2 rows × 3 cols launcher
- **Gauge**: Arc style with triangle needle, long ticks
- **Colors**: Dark gray with warm brown accents

### 4. offroad-rugged

- **Mode**: dark
- **Description**: High-visibility rugged design for off-road use. Maximum contrast, large elements, desert camouflage accents.
- **Preset**: Off-road layout
- **Grid**: 4 rows × 5 cols launcher
- **Gauge**: Bar style, no ticks, off-road preset
- **Colors**: Near-black with orange accent

### 5. night-lowglare

- **Mode**: dark
- **Description**: Low-glare theme for night driving. Muted colors reduced blue light, optimized for extended use after dark.
- **Preset**: Night layout
- **Grid**: 3 rows × 3 cols launcher
- **Gauge**: Digital style, no ticks
- **Colors**: Very dark with muted brown accent

## Theme Switching

### Live Theme Change

Themes can be switched at any time via the `SportageObdTheme()` composable:

```kotlin
CockpitTheme.SportageObdTheme(
    presetId = "cyber-neon",
    overrides = ThemeOverrides(
      accent = "#00FFAA",
      fontScale = 1.1f
    ) {
      // UI composition here
    }
)
```

### Persistence

Theme selection and user overrides are persisted via `SharedPreferences` (legacy) with schema versioning. On app restart, the last selected theme is automatically restored.

### System Mode

When `mode = "system"`, the theme follows the system's light/dark setting. On theme change, the app re-reads the system setting and applies the appropriate color scheme.

## User Overrides

Users can override individual theme properties on top of a base preset:

| Override | Type | Description |
|----------|------|-------------|
| `accent` | `hex color` | Replace the preset accent color |
| `background` | `hex color` | Replace the preset background |
| `gaugeStyle` | `string` | Override gauge style (radial/bar/digital/arc/minimal) |
| `density` | `string` | "compact", "comfortable", or "spacious" |
| `fontScale` | `float` | 0.85 to 1.5 (applied on top of theme font sizes) |
| `widgetLayout` | `BoardLayout` | Custom widget layout override |

## Import/Export

Themes can be imported and exported as JSON files. Strict validation ensures:

- No executable content
- No remote URLs
- No arbitrary layout injection
- Image sizes are capped
- Malformed JSON falls back to default theme safely

### Export

```bash
# Export current theme as JSON
adb shell run-as com.sportage.obd cat /raw/active_theme.json > theme_export.json
```

### Import

```bash
# Place JSON in res/raw/ and reload
adb push custom_theme.json /data/data/com.sportage.obd/app_raw/
# Restart app to apply
```

Fallback: If the imported theme is invalid, the app silently falls back to the default "minimal-daily" theme and logs a non-fatal warning via Timber/Log.

## Accessibility

- Accessible contrast is enforced for text and critical gauges
- Theme editor warns when contrast ratio falls below 4.5:1 for normal text
- System font scaling and reduce-motion settings are respected
- TalkBack-friendly content descriptions on all widgets and controls
- RTL support via mirrored layout where practical
- Themes usable in bright sunlight (high contrast) and low-light driving conditions

## Migration

From any previous color/theme settings in the app, migration reads the persisted `themeId` and `accentOverride` SharedPreferences entries and maps them to the new schema. If migration fails (missing or corrupted data), the app falls back to `minimal-daily` with a logged warning.