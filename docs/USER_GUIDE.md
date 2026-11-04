# SportageOBD – User Guide & Operational Manual

## 1. Hardware Requirements & Vehicle Compatibility

SportageOBD is engineered for **Kia Sportage (2.0 MPI / 2.4 GDI / AWD)** and all OBD-II / CAN-bus compliant vehicles (ISO 15765-4 11-bit/500k standard).

### Supported Adapters
- **Bluetooth Classic ELM327** (v1.5 / v2.1 / v2.2)
- **Vgate iCar Pro** (Bluetooth 3.0 / 4.0 BLE)
- **Veepeak OBDCheck BLE**
- **vLinker MC+ / FD+**
- **Carista OBD2 Adapter**
- Aftermarket Android Head Units (TS10, UIS7862, Rockchip PX5/PX6, Allwinner)

---

## 2. Hardware Installation & Initial Setup

1. **Locate the OBD-II Port**:
   - On the Kia Sportage, the 16-pin DLC (Data Link Connector) is located directly under the dashboard to the lower-left of the steering wheel, near the fuse panel.
2. **Plug in the Adapter**:
   - Firmly insert the ELM327 adapter into the port. The adapter's power LED will illuminate red.
3. **Turn Vehicle Ignition to ON**:
   - Turn the car key to ACC/ON (or press the START button twice without pressing the brake pedal) so the ECU powers up.

---

## 3. Bluetooth Pairing & Connection

SportageOBD includes a built-in Bluetooth device scanner with automatic nearby discovery:

1. In SportageOBD, tap the **Bluetooth Status Pill** at the top right of the cockpit (or tap the Bluetooth icon on the navigation dock).
2. **Paired Devices**: If your adapter is already paired with your phone/head unit, tap **Connect** next to its name.
3. **Scan Nearby Devices**: If your adapter is not yet paired:
   - Tap **Scan Nearby**. SportageOBD will actively discover Bluetooth devices in range.
   - When your adapter appears in the **Nearby Discovered Adapters** list, tap **Pair & Connect**.
   - If prompted for a PIN, enter `1234` or `0000`.
4. Once linked, the status pill turns **Teal** and displays `ELM327 CONNECTED (~14.2Hz)` along with battery voltage.

---

## 4. Setting as Default Android HOME Launcher

To make SportageOBD the permanent car launcher:

1. Tap **Setup** in the navigation dock.
2. Tap **Set Default Home**.
3. In the Android system prompt, select **Sportage Launcher** and choose **Always**.
4. Now, turning on the head unit or pressing the physical/virtual **HOME** button will immediately return to your SportageOBD cockpit.

---

## 5. Cockpit & Features Overview

### Permanent Split-Screen (Home)
- **Left Pane (62% in landscape)**: Dominant interactive live map centered on current GPS coordinates with vehicle indicator.
  - **Speed Alert Sign**: Tap to toggle alert thresholds (`80`, `100`, `120`, `140 km/h`, or `OFF`).
  - **Waze Full Screen**: Tap the Waze button to open the full standalone Waze application for turn-by-turn routing.
  - **Recenter**: Tap the compass target icon to snap the map back to your current vehicle position.
- **Right Pane (38% in landscape)**: Real-time digital and analog instrument cluster displaying:
  - Big digital Speedometer & Estimated Transmission Gear (`P/N`, `D1`–`D6`).
  - Tachometer (RPM) with redline warning.
  - Engine Coolant Temperature with cold/overheating zones.
  - Battery Voltage, Fuel Tank Level, Throttle Position, and Engine Load.

### App Drawer (Never Get Trapped)
- Tap the **APPS** button in the dock or top status bar.
- An instant drawer displays all installed applications on the head unit (Spotify, Google Maps, FM Radio, Settings).
- Use the quick search bar to filter by name.

### Pause OBD (Ignition Off / Battery Saver)
- When parked with the vehicle engine switched off, tap **PAUSE OBD** in the top bar or Settings.
- This halts high-frequency polling queries, preventing battery drain while keeping the launcher active.

### DTC Diagnostics & Fault Codes
- Navigate to the **Diag** page in the dock.
- Tap **Scan DTCs (Mode 03)** to query the vehicle's engine control unit.
- If trouble codes are found (e.g. `P0133`, `P0420`), review the component system, severity, and probable cause.
- After servicing, tap **Clear Codes (Mode 04)** to reset the Check Engine light.

---

## 6. Virtual Bench Demo Mode

For indoor testing without a vehicle:
1. Open **Setup** or the **Bluetooth Dialog**.
2. Toggle **Bench Test / Demo Simulation** to **ON**.
3. A demo banner appears across the gauges, simulating dynamic driving speeds, RPM gear shifts, and telemetry for testing.
