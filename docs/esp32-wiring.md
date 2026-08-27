# ESP32 Wiring & Power

All pins are configurable in [`firmware-esp32/src/config.h`](../firmware-esp32/src/config.h).

## Pin map (defaults)

| Function | ESP32 pin | Notes |
|---|---|---|
| WS2812B data | GPIO 13 | 330–470 Ω in series; 1000 µF across 5 V/GND at the strip |
| Month slider (pot wiper) | GPIO 34 | ADC1, input-only; pot ends to 3V3 and GND |
| 74HC165 PL (load) | GPIO 25 | active-low parallel load |
| 74HC165 CP (clock) | GPIO 26 | shared clock down the chain |
| 74HC165 Q7 (data) | GPIO 27 | serial data from the last chip |
| SD CS | GPIO 5 | SPI |
| SD SCK / MISO / MOSI | GPIO 18 / 19 / 23 | VSPI |
| DFPlayer RX (ESP TX→) | GPIO 17 | 1 kΩ in series into DFPlayer RX |
| DFPlayer TX (→ESP RX) | GPIO 16 | |
| Speaker | DFPlayer SPK1/SPK2 | 3 W 4–8 Ω, or DAC_R/GND + amp |

## 74HC165 button chain (31 buttons)

4 × 74HC165 = 32 inputs (31 used, one spare). Each button connects its 74HC165 input to GND;
enable the internal/added pull-ups (10 kΩ to 3V3). Chain `Q7 → DS` of the next chip; share
`PL` and `CP`. Firmware reads MSB-first and inverts (pressed = logical 1).

Day *N* button → 74HC165 input bit *N−1* (bit 0 = day 1).

## Power budget

93 WS2812B × ~60 mA (full white) ≈ **5.6 A worst case** at 5 V. Mitigations already in firmware:

- `LED_MAX_BRIGHTNESS = 64` (÷4 → ~1.4 A worst case).
- `FastLED.setMaxPowerInVoltsAndMilliamps(5, 2000)` hard clamp.

Use a 5 V ≥ 4 A supply, inject power at both ends of the strip for runs over ~1 m, and power
the ESP32 from the same 5 V rail via its 5V/VIN pin (not 3V3).

## Slider hardware

A smooth linear pot works with the firmware's hysteresis + 120 ms debounce. A detented pot or
12-position rotary switch feels better and is less prone to boundary flicker — wire it the same
way (wiper → GPIO 34). Tune `SLIDER_HYSTERESIS` / `SLIDER_SAMPLES` if a smooth pot still jitters.
