# PVINFO / SOPR - Solar Mini Power Station Switch & Infotainment System

**SOPR (SOlární PŘepínač)** is an intelligent solar switch system designed for 24V off-grid mini solar power stations (e.g., 24V battery bank with solar panels and a backup 230V $\rightarrow$ 24V mains power supply). Created by **Martin Pihrt** ([pihrt.com](https://pihrt.com)).

This repository contains the complete software ecosystem for the SOPR project:
- **Android Mobile App ([`app/`](app))**: Real-time telemetry dashboard, historical voltage charts, and auto-connection over Bluetooth.
- **Python Bluetooth Emulator ([`sopr_bluetooth_emulator.py`](sopr_bluetooth_emulator.py))**: Desktop GUI tool (Tkinter) emulating SOPR hardware over RFCOMM for testing and development.

---

## ⚡ Overview & Features

When using solar power for off-grid equipment (IP cameras, automated gates, etc.), winter months or low-sun conditions can lead to battery depletion. SOPR automatically manages charging inputs between solar power and backup mains, while protecting the battery against under-voltage and over-voltage.

- **Automatic Charge Source Switching**: Prioritizes Solar PV charging and automatically switches to the backup 24V mains power supply when battery level drops below 25%.
- **Battery Over-Voltage & Under-Voltage Protection**:
  - Automatically disconnects the load (AUX output) when battery voltage drops below 21.2 V.
  - Automatically disconnects the charge controller during battery over-voltage (> 30.0 V).
- **Visual Status Display**: 5 status LEDs for real-time state feedback (Solar, 24V Mains Source, Battery, AUX Load, Error).
- **Bluetooth Telemetry Streaming**: Transmits real-time voltage measurements, temperature, uptime, relay states, and 10-sample historical data over RFCOMM in JSON format.

---

## 🛠️ Hardware Architecture & Specifications

| Feature / Component | Description |
| :--- | :--- |
| **Microcontroller** | ATmega328P (5V operating voltage via onboard LM2576 step-down regulator) |
| **Measurement Voltage Range** | 0.0 V – 45.0 V DC (Solar PV, 24V Source, Battery via resistor dividers) |
| **Temperature Range** | -127 °C to +127 °C |
| **Relays (3x)** | • `re_s` (Relay 1): Charge input selector (Solar PV vs 24V Mains Source)<br>• `re_r` (Relay 2): Charger disconnect (opens during over-voltage)<br>• `re_a` (Relay 3): AUX load disconnect (opens during under-voltage) |
| **LED Indicators (5x)** | • `led_p` (Solar PV status)<br>• `led_s` (24V Mains Source status)<br>• `led_b` (Battery status, solid when > 25%)<br>• `led_a` (AUX load output active)<br>• `led_e` (Error indicator: solid on under-voltage, blinking on over-voltage) |
| **Bluetooth Module** | HC-05 / HC-06 (RFCOMM, 9600 baud, default PIN: `1234`) |

### Voltage Thresholds (Default for Sealed 24V Battery Banks)
- **Minimal Solar Input**: `20.0 V` (Switches charging input to Solar PV when exceeded)
- **Minimal Supply Input**: `20.0 V` (Available mains backup voltage)
- **Battery 25% Threshold**: `25.2 V` (Below this, mains charging is engaged if PV is unavailable)
- **Under-Voltage Cutoff**: `21.2 V` (Disconnects AUX output load)
- **Over-Voltage Cutoff**: `30.0 V` (Disconnects charge regulator input)

---

## 📡 Bluetooth Communication Protocol & JSON Payload

The SOPR hardware communicates with external clients (Android App, Python emulator) using simple ASCII commands over an RFCOMM serial stream.

### Handshake & Data Commands

1. **Verification Request**:
   - Send: `?`
   - Response: `SOPR\r\nR`

2. **JSON Telemetry Request**:
   - Send: `J` or `j`
   - Response: JSON payload (terminated with `\r\n`):

```json
{
  "fw": "1.01",
  "run": "0.1.25.10",
  "pv": "18.50",
  "src": "28.55",
  "bat": "24.15",
  "temp": "24.63",
  "re_s": 1,
  "re_r": 1,
  "re_a": 1,
  "led_p": 1,
  "led_s": 0,
  "led_b": 1,
  "led_a": 1,
  "led_e": 0,
  "pH": ["18.50", "18.20", "17.90", "16.50", "15.00", "0.00", "0.00", "0.00", "0.00", "0.00"],
  "sH": ["28.55", "28.55", "28.55", "28.55", "28.55", "28.55", "28.55", "28.55", "28.55", "28.55"],
  "bH": ["24.15", "24.10", "24.05", "24.00", "23.95", "23.90", "23.85", "23.80", "23.75", "23.70"]
}
```

### JSON Field Reference
- `fw`: Firmware version running on the CPU.
- `run`: System uptime since boot formatted as `days.hours.minutes.seconds`.
- `pv`: Solar panel voltage reading (V).
- `src`: 24V mains power supply voltage reading (V).
- `bat`: Battery bank voltage reading (V).
- `temp`: Battery temperature reading (°C).
- `re_s`, `re_r`, `re_a`: Relay status (`1` = closed/active, `0` = open/inactive).
- `led_p`, `led_s`, `led_b`, `led_a`, `led_e`: LED status flags (`1` = on, `0` = off).
- `pH`, `sH`, `bH`: 10-sample historical voltage arrays (rotated every 30 minutes) for Solar PV (`pH`), Mains Source (`sH`), and Battery (`bH`).

---

## 📱 Android Mobile Application (`app/`)

The Android application is written in **Kotlin** using **Jetpack Compose** and **Material 3**.

### Features
- **Live Monitoring Dashboard**: Displays current solar, source, and battery voltages, temperature, system uptime, firmware version, and status indicators.
- **Historical Charts**: Visual representation of past 10 sample trends (`pH`, `sH`, `bH`).
- **Bluetooth Connection Management**:
  - Native pairing via Android `CompanionDeviceManager`.
  - RFCOMM channel fallback support (bypasses SDP when standard socket connection fails).
  - Configurable polling interval and auto-reconnect to saved SOPR device MAC address.
- **Settings Screen**: Allows adjusting refresh rate (seconds) and target RFCOMM channel.

### Building & Running the App
1. Open the repository in **Android Studio**.
2. Sync Project with Gradle Files.
3. Run on a physical Android device (Bluetooth RFCOMM requires a physical device or emulator with pass-through support).
4. Minimum SDK: Android 8.0 (API 26) / Target SDK: Android 34+.

---

## 🐍 Python Bluetooth Device Emulator (`sopr_bluetooth_emulator.py`)

A desktop GUI tool built with Python 3 and Tkinter that simulates a hardware SOPR device over Bluetooth RFCOMM. This enables testing the Android application without requiring physical hardware.

### Key Features
- **Adapter & RFCOMM Channel Scanner**: Automatically detects local Bluetooth adapters (via `ipconfig /all` on Windows) and tests RFCOMM channels 1–30 to find free channels.
- **Live Parameter Controls**: Inputs to adjust simulated voltage values (`pv`, `src`, `bat`, `temp`), firmware string, and uptime key.
- **State Toggles**: Checkboxes to toggle relay states (`re_s`, `re_r`, `re_a`) and LED states (`led_p`, `led_s`, `led_b`, `led_a`, `led_e`).
- **History Generator**: Push live measurement snapshots or generate random data series into 10-sample history buffers.
- **Activity Log**: Real-time logging of connection events, incoming commands, and outgoing responses.

### Running the Emulator
```bash
python sopr_bluetooth_emulator.py
```
*Note: Requires Python 3.x with `tkinter` (included standard on Windows Python installations).*

---

## 📁 Repository Structure

```
PVINFO/
├── app/                                    # Android Mobile Application (Kotlin / Jetpack Compose)
│   ├── src/main/java/cz/pihrtm/sopr/        # App source code (MainActivity, MainViewModel, UI & datatypes)
│   ├── src/main/res/                       # Resources (layouts, strings, icons, themes)
│   └── build.gradle                        # App Gradle build configuration
├── sopr_bluetooth_emulator.py              # Python Tkinter Bluetooth RFCOMM emulator GUI
├── build.gradle                            # Top-level Gradle build file
├── settings.gradle                         # Gradle settings
└── README.md                               # Project documentation
```

---

## 🔗 References & Credits

- **Author**: Martin Pihrt
- **Blog / Documentation**: [pihrt.com](https://pihrt.com)
- **Google Play Store**: [SOPR App on Google Play](https://play.google.com/store/apps/details?id=cz.pihrtm.sopr)
- **AVR Dude / USBasp**: Flashing tool for ATmega328P firmware.
