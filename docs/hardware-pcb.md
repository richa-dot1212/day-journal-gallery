# Day-Calendar Hardware — Circuit Blueprint

Everything needed to move the day-orb calendar from jumper wires to a perfboard or a
fabricated PCB. The firmware is `firmware-esp32-arduino/DayCalendar/` (4 orbs today).

The tricky parts are electrical, not layout: **power budget, the LED data level-shift, and
button pull-ups.** Get those right and the board just works.

---

## A. 4-orb board (now)

### Parts (BOM)

| Ref | Part | Notes |
|---|---|---|
| U1 | ESP32 DevKitC / NodeMCU-32S | Mounted on 2× female headers so it stays removable |
| U2 | 74AHCT125 (or 74HCT245) | 3.3 V → 5 V level shifter for LED data. *Recommended*; short strips often work without it but it's the #1 source of flaky pixels |
| D1–D4 | WS2812B 5050 (or a 4-pixel cut of strip) | One per orb |
| SW1–SW4 | 6 mm tactile switch | One per orb |
| R1 | 330–470 Ω, ¼ W | In series with LED data, at the first pixel |
| C1 | 1000 µF, 10 V electrolytic | Across 5 V / GND, right at the LED end |
| C2 | 100 nF ceramic | Decoupling, next to U1's 3V3 pin |
| C3–C6 | 100 nF ceramic | Optional, one across each switch for debounce |
| J1 | 2-pin screw terminal or USB-C breakout | 5 V power in |
| J2 | 3-pin JST / header | LED strip out: 5 V, GND, DATA |

### Connections (netlist)

**Power**
```
J1.+5V ──┬── U1.5V (VIN)          ← powers the ESP from the same 5 V
         ├── U2.VCC
         ├── C1(+), C2, C3..C6 one side
         └── J2.5V (to LED strip)
J1.GND ──┴── U1.GND, U2.GND, C1(−), all switch GND legs, J2.GND   ← one common ground
```
4 LEDs at brightness ≤ 80 draw < ~150 mA — fine on a dev board's 5 V pin / USB.
Add the external 5 V supply (J1) once you have more than ~8 LEDs.

**LED data**
```
U1.GPIO4 ──► U2.A (input)
U2.Y (output, now 5 V) ──► R1 ──► J2.DATA ──► D1.DIN
D1.DOUT ──► D2.DIN ──► D3.DIN ──► D4.DIN         (each pixel's OUT to the next pixel's IN)
```
If you skip U2: `GPIO4 ──► R1 ──► D1.DIN` directly (works for short runs, 3.3 V logic).

**Buttons** (firmware uses internal pull-ups — no external resistor needed)
```
SW1: U1.GPIO27 ── SW1 ── J1.GND
SW2: U1.GPIO26 ── SW2 ── J1.GND
SW3: U1.GPIO25 ── SW3 ── J1.GND
SW4: U1.GPIO33 ── SW4 ── J1.GND
```
Optional per switch: 100 nF (C3–C6) across the switch, and/or a 10 kΩ from the GPIO to 3V3
if you want a hardware pull-up too.

### Pin summary (matches the firmware `#define`s)

| ESP32 pin | Use |
|---|---|
| GPIO 4 | WS2812B data out (to level shifter / first pixel) |
| GPIO 27 | Button 1 → Sept 1 |
| GPIO 26 | Button 2 → Sept 2 |
| GPIO 25 | Button 3 → Sept 3 |
| GPIO 33 | Button 4 → Sept 4 |
| 5V / VIN | 5 V in |
| GND | common ground |

Avoid for buttons if you add more: GPIO 0, 2, 12, 15 (strapping), 6–11 (flash),
34–39 (input-only, no pull-up).

---

## B. 31-orb board (later)

Same power + LED chain (31 pixels — now you **need** the external 5 V supply: 31 × 60 mA ≈
1.9 A worst case, size for 3 A, inject 5 V at both ends of the chain, keep C1 = 1000 µF).

**31 buttons** don't fit on GPIOs — use a **74HC165 shift-register chain**:

| Part | |
|---|---|
| 4× 74HC165 | 4 × 8 = 32 parallel inputs (31 used) |
| 31× tactile switch | each input to GND |
| 31× 10 kΩ | pull-up on each input to 3V3 (the '165 has no internal pull-ups) |
| decoupling 100 nF per '165 | |

```
ESP32.GPIO_LOAD  ──► all '165  SH/LD  (pin 1)
ESP32.GPIO_CLK   ──► all '165  CLK    (pin 2)
ESP32.GPIO_DATA  ◄── '165 #4   Q7     (pin 9)
'165 #n Q7  ──► '165 #(n+1) SER (pin 10)      (daisy-chain)
```
Pick any 3 free GPIOs for LOAD / CLK / DATA (e.g. 32, 14, 13). The firmware change is a
shift-register read loop instead of `digitalRead` per pin — the button→day→BLE logic is
unchanged. `ORB_DAYS[]` grows to 1..31; `OrbController.calendarDayNumbers` grows to match.

---

## C. Getting it made

1. **KiCad** (free, kicad.org) or **EasyEDA** (easyeda.com, browser). Draw the schematic from
   section A/B, assign footprints, do the board layout.
2. **File → Fabrication Outputs → Gerbers** (KiCad) or one-click in EasyEDA.
3. Upload the Gerber ZIP to **jlcpcb.com** or **pcbway.com**. Defaults are fine
   (1.6 mm, HASL, 2-layer). ~$2 + shipping, ~5–10 days.
4. Hand-solder the through-hole parts. The 74AHCT125 / 74HC165 also come in DIP packages —
   use those + DIP sockets so nothing is surface-mount.

### Perfboard alternative (no fab)
Solder the section-A connections onto a piece of stripboard. Use female headers for the
ESP32 so it stays removable. This is the right call for the 4-orb prototype.
