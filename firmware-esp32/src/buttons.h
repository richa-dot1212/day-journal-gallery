#pragma once
#include <stdint.h>

// 31 momentary buttons read through a 74HC165 shift-register chain.
// Falling edge (press) with debounce -> app_state::onButtonPressed(day).

namespace buttons {

void begin();
void tick();  // call from loop()

}  // namespace buttons
