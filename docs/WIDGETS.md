# Widgets

The **Boards** page is a set of horizontally swipeable pages that the driver fills with widgets.
Each widget reads live OBD/session state, and every instance has its own configuration. Layouts
and configuration persist across restarts and can be exported/imported as JSON.

Source: `android/app/src/main/java/com/sportage/obd/ui/boards/`

| File | Purpose |
|---|---|
| `BoardModels.kt` | Widget catalog, limits, layout codec, live data bundle, `BoardStore`. |
| `BoardWidgets.kt` | Widget renderers and per-widget config application. |
| `BoardScreen.kt` | Swipeable boards, edit mode, undo/reset, layout export/import. |
| `WidgetPicker.kt` | Grouped widget picker. |
| `WidgetSettingsDialog.kt` | Per-widget configuration editor. |
| `WidgetConfig.kt` | Validated config model, JSON codec, type defaults. |
| `BoardConfigStore.kt` | Versioned config persistence, portable board document. |

## Widget catalog

| Id | Title | Default unit | Notes |
|---|---|---|---|
| `speed` | Speed | `km/h` (respects unit toggle) | Warns at 140, critical at 160 |
| `rpm` | Engine speed | `rpm` | Warns at 5000, critical at 6500 |
| `coolant` | Coolant temp | `°C` | Warns at 100, critical at 110 |
| `intake` | Intake air temp | `°C` | |
| `fuel` | Fuel level | `% fuel` | Warns below 15% |
| `throttle` | Throttle | `% throttle` | |
| `load` | Engine load | `% load` | |
| `voltage` | Battery | `volts` | Warns below 11.5 V |
| `gear` | Gear estimate | `gear` | Shows `P/N` when 0 |
| `maf` | Air flow | `g/s air` | |
| `timing` | Timing advance | `°BTDC` | |
| `map` | Live map | — | Always spans the full row |
| `clock` | Clock | — | Time + date |
| `gps` | GPS position | — | Latitude/longitude + status |
| `trip_distance` | Trip distance | `km trip` | One decimal by default |
| `trip_duration` | Trip duration | `duration` | |
| `connection` | Connection | — | Adapter state icon + label |
| `speed_graph` | Speed graph | — | Rolling history |
| `rpm_graph` | RPM graph | — | Rolling history |

Adding a widget type requires an enum entry, a renderer branch, a picker entry, and (optionally)
a default config. Unknown persisted ids are dropped without losing the rest of the board.

## Configuration

`WidgetConfig` fields (all optional, normalized before use):

| Field | Range / rule |
|---|---|
| `titleOverride` | ≤ 24 chars; replaces the catalog title |
| `unitOverride` | ≤ 12 chars; replaces the unit label |
| `minValue` / `maxValue` | finite floats; normalized so min ≤ max |
| `warnThreshold` / `criticalThreshold` | clamped into `[min, max]`; warn < critical |
| `decimals` | 0–2 (defaults: voltage/MAF/timing/trip distance = 1) |
| `span` | 1 or 2 grid columns (map is forced to full width) |
| `refreshMs` | 100–5000ms; picker choices 100/250/500/1000/2000/5000 |
| `visible` | hidden widgets render only in edit mode |
| `style` | `auto`/`digital`/`bar`/`arc`/`minimal` |

Thresholds color the reading: `≥ warn` amber, `≥ critical` red, with the widget's built-in
defaults as fallback. Low-fuel and low-voltage warnings stay direction-aware.

## Limits and responsive layout

- 6 boards maximum, 12 widgets per board (`BoardLimits`). Boards cannot be emptied of pages below 1.
- Grid uses adaptive 150dp columns; widgets span 1–2 columns (clamped to the current line span).
- Board minimum sizes come from the active theme (`ThemeLayout.widgetMinWidth/Height`).
- Empty boards explain how to add widgets; a widget that has no data shows `--`, never crashes.
- Rendering is pure Compose state; the OBD polling pipeline is untouched.

## Persistence and migration

`SharedPreferences("sportage_obd_boards")`:

| Key | Format |
|---|---|
| `board_layout` | Legacy token string, e.g. `speed|rpm;;map|clock` |
| `board_widget_configs_v2` | `{"version":2,"boards":[[{...config...}]]}` |

- Legacy layouts keep working; configs are created from per-type defaults, preserving order.
- Missing/corrupt config documents log and fall back to defaults.
- The theme's `widgetPresets` seed the board the first time a fresh install has no saved layout.
- The board screen aligns config boards to widget counts after add/remove/reorder, so the two
  never drift apart.

## Editing

Edit mode exposes, per widget: configure (pencil), duplicate (copy), reorder (arrows), remove (×).
The header adds undo (last 20 changes), copy layout, paste layout, and a reset to the active
theme's default board. The picker groups types by Driving / Engine / Location / Trip / Status and
marks types already on the current board.

## Export / import

Layout export produces a portable document (also used by `docs/samples/sample-widget-layout.json`):

```json
{
  "version": 2,
  "boards": [
    {
      "widgets": [
        { "type": "speed", "config": { "title": "KMH", "span": 2, "warn": 130, "decimals": 0 } }
      ]
    }
  ]
}
```

`BoardDocument.import` enforces the version, the board/widget limits, known widget ids, and
normalizes every config. Unknown types are skipped; a document with no usable widgets is rejected.
The same document round-trips through `BoardDocument.export`.

## Graceful degradation

- No OBD link: values show `--`, connection widget shows the real adapter state.
- Invalid config values are clamped by `normalized()`; invalid documents are rejected wholesale.
- Hidden widgets remain editable (they appear in edit mode) so they can be restored.
- Widget rendering never blocks the OBD data loop and never allocates during draw beyond the
  existing history paths.

## Accessibility

- Every edit control has a content description (`Configure Speed`, `Remove widget`, …); the picker
  rows expose readable labels and test tags.
- Edit controls are 28–44dp icons inside cards; the driving surface itself uses `DriveDimens`
  minimums.
- Threshold warnings use both color and the numeric reading, so color-blind drivers keep the value.
- RTL: layouts use `Arrangement`/`Alignment` rather than absolute left/right padding.

## Tests

`android/app/src/test/java/com/sportage/obd/WidgetConfigTest.kt` covers normalization, validation,
config JSON round-trip, board document round-trip/limits/rejection, legacy migration, theme preset
resolution, and alignment of configs with widget mutations. `BoardsTest` keeps the legacy layout
codec covered.
