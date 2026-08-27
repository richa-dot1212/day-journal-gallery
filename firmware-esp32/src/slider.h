#pragma once
#include <stdint.h>

// Linear/rotary potentiometer on SLIDER_ADC_PIN mapped to 12 discrete months with
// hysteresis so it doesn't flicker at the boundaries.

namespace slider {

void begin();

// Poll the ADC; if the resolved month changed (after hysteresis + debounce),
// calls app_state::onSliderMonthChanged(). Call from loop().
void tick();

uint8_t resolvedMonth();

}  // namespace slider
