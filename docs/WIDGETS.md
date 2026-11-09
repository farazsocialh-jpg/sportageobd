# SportageOBD Widget System

## Overview

The widget system provides a complete framework for customizable boards with live OBD data, configuration, persistence, and theme-aware defaults. Widgets can be added, removed, resized, reordered, and duplicated from a picker, and layouts persist across restarts via DataStore.

## Widget Types

The following widget types are available, each with its own renderer and config schema:

### Driving Widgets

| Type | ID | Title | Data | Config |
|------|-----|-------|------|--------|
| Speed | `speed` | Speed | `speedKmh`, `speedUnit` | units, warning threshold |
| RPM | `rpm` | Engine speed | `rpm` | redline warning |
| Coolant | `coolant` | Coolant temp | `coolantTempC` | overheating threshold |
| Intake Air | `intake` | Intake air temp | `intakeTempC` | |
| Boost | (custom) | Boost/vacuum | `boostKpa` | units, warning |
| Throttle | `throttle` | Throttle % | `throttlePct` | |
| Load | `load` | Engine load | `engineLoadPct` | |
| Battery | `voltage` | Battery voltage | `batteryVoltage` | low-voltage warning |
| Gear | `gear` | Gear estimate | `estimatedGear` | P/R/N display |

### Trip Computation Widgets

| Type | ID | Title | Data | Config |
|------|-----|-------|------|--------|
| Trip Distance | `trip_distance` | Trip distance | `tripDistanceKm` | reset button |
| Trip Duration | `trip_duration` | Trip duration | `tripDurationSeconds` | |
| Average Speed | `avg_speed` | Average speed | `avgSpeedKmh` | |
| Fuel Economy | (custom) | Fuel economy | `fuelEconomyL100` | |

### Status Widgets

| Type | ID | Title | Data | Config |
|------|-----|-------|------|--------|
| Connection | `connection` | Connection status | `connectionLabel`, `connectionOk` | |
| DTC Status | (custom) | DTC/status | `dtcCount`, `hasCritical` | |
| Protocol | (custom) | Protocol/adapter status | `protocol`, `adapterName` | |
| GPS Position | `gps` | GPS position | `currentLat`, `currentLon`, `gpsStatus` | |

### Media & Clock

| Type | ID | Title | Data | Config |
|------|-----|-------|------|--------|
| Clock | `clock` | Clock | system time | format (12/24h) |
| Media | `media` | Now-playing placeholder | `appName`, `trackName` | |
| Date | (custom) | Date | system date | format |

### Text & Action

| Type | ID | Title | Data | Config |
|------|-----|-------|------|--------|
| Text/Note | `text_note` | Text note | `body` | multiline, color role |
| Shortcut/Action | `shortcut` | Shortcut/action | pending intent | label, icon |

### Default Board Layout

The default layout (used when no layout is persisted) is:

```json
{
  "boards": [
    ["speed", "rpm", "coolant", "voltage", "map", "clock"]
  ]
}
```

## Widget Configuration Schema

Each widget type supports a configuration schema stored per-widget. Common fields:

```json
{
  "title": "Speed",
  "units": "km/h",
  "min": 0,
  "max": 300,
  "warningThreshold": 140,
  "criticalThreshold": 180,
  "displayStyle": "value-bar-digital", // value, bar, digital
  "size": "large", // small, medium, large
  "refreshRate": "1s", // how often to poll OBD
  "dataSource": "RPM", // PID or widget type
  "visibility": "always", // always, when-connected, when-error
  "labelFormat": "%.0f", // decimal format
  "decimals": 0,
  "colorRole": "primary" // overrides text/accent color
}
```

### Per-Widget Config Fields by Type

- **Speed**: `units` ("km/h" or "mph"), `warningThreshold` (speed in km/h)
- **RPM**: `redline` (RPM threshold), `displayStyle` ("value" or "bar")
- **Coolant**: `overheatingThreshold` (°C, default 105)
- **Voltage**: `lowVoltage` (V, default 11.5)
- **Connection**: none (read-only status)
- **Clock**: `format` ("HH:mm" or "HH:mm:ss", 12/24h)
- **Text/Note**: `multiline` (boolean), `initialText`, `textColorRole`
- **Trip Distance**: `showUnit` ("km" or "mi")

## Board Layout Persistence

Layouts are persisted as compact token strings via `BoardLayout`:

```kotlin
// Save layout
BoardLayout.save(prefs, layout)

// Load layout
val layout = BoardLayout.load(prefs)
```

The format is semi-colon separated boards, pipe separated widgets:

```
speed|rpm;;coolant|voltage|map|clock
```

This avoids JSON dependency for the board codec and stays testable on the JVM.

## Theme-Defined Default Presets

Each built-in theme defines its own default widget layout:

| Theme | Default Layout |
|-------|---------------|
| minimal-daily | speed, rpm, coolant, voltage, map, clock |
| cyber-neon | rpm, speed, boost, coolant, oil_temp, intake |
| classic-analog | coolant, oil_temp, intake, voltage, gear, connection |
| offroad-rugged | speed, rpm, coolant, oil_temp, boost, fuel |
| night-lowglare | speed, coolant, voltage, connection, trip_distance, clock |

## Widget Picker

The widget picker (`WidgetPicker`) displays categorized entries with:

- Category filters (Driving, Engine, Location, Trip, Status)
- Preview of widget type with icon
- Checked state for widgets already on the board
- Disable state for widgets that would exceed `MAX_WIDGETS_PER_BOARD` (12)

## Resizing and Reordering

### Constraints

- Boards: maximum 6 boards per layout (`BoardLimits.MAX_BOARDS`)
- Widgets per board: maximum 12 widgets (`BoardLimits.MAX_WIDGETS_PER_BOARD`)
- Minimum widget size: 96dp height, variable width
- Grid adaptation: `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 150.dp)`

### Actions

| Action | Description |
|--------|-------------|
| Add widget | From picker; placed at end of current board |
| Remove widget | Delete from board; layout updates |
| Resize | Drag widget edges within grid constraints |
| Reorder | Drag-and-drop within same board |
| Duplicate | Copy widget to same or different board |
| Remove board | If more than 1 board exists |

### Move Widget

```kotlin
fun moveWidget(boardIndex: Int, widgetIndex: Int, delta: Int) {
    val board = layout.boards.getOrNull(boardIndex) ?: return
    val target = widgetIndex + delta
    if (target < 0 || target >= board.size) return
    val swapped = board.toMutableList().apply {
        val w = removeAt(widgetIndex); add(target, w)
    }
    persist(BoardLayout(layout.boards.mapIndexed { i, b ->
        if (i == boardIndex) swapped else b
    }))
}
```

## Graceful Degradation

When OBD data is unavailable:

- Show placeholder text (e.g., "--")
- Show last-known value with stale indicator
- Show error state (red warning) for critical readings
- Never crash; rendering is isolated from OBD I/O

All widgets use `StateFlow`/`LiveData` from the view model, and rendering is triggered by state changes, not direct OBD calls.

## Accessibility

- All widgets have content descriptions for TalkBack
- Minimum touch target: 76dp
- High-contrast mode respect in off-road and night themes
- Focus order is logical within and between boards
- RTL mirroring where practical