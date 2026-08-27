package com.journalgallery.shared.domain

import kotlinx.serialization.Serializable

/**
 * A 24-bit RGB color. Stored packed so it round-trips cleanly to the ESP32's
 * 3-bytes-per-color NVS layout.
 */
@Serializable
data class Rgb(val r: Int, val g: Int, val b: Int) {
    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "channel out of range: $r,$g,$b" }
    }

    /** `#RRGGBB` — the wire form used in the JSON protocol. */
    fun hex(): String = "#" + h(r) + h(g) + h(b)

    companion object {
        private fun h(v: Int): String {
            val s = v.toString(16)
            return if (s.length == 1) "0$s" else s
        }

        fun fromHex(hex: String): Rgb {
            val clean = hex.removePrefix("#").trim()
            require(clean.length == 6) { "expected #RRGGBB, was '$hex'" }
            return Rgb(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16),
            )
        }

        /** From a packed ARGB int (as produced by platform bitmap pixel buffers). */
        fun fromArgb(argb: Int): Rgb =
            Rgb((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }
}

/** A dominant color plus the fraction of sampled pixels it represents (0f..1f). */
data class WeightedColor(val color: Rgb, val weight: Float)

/**
 * The three representative colors for one day, ordered by descending weight,
 * ready to drive both the UI gradient and the day's 3 physical LEDs.
 */
@Serializable
data class DayColors(val colors: List<Rgb>) {
    init {
        require(colors.size == 3) { "DayColors needs exactly 3 colors, had ${colors.size}" }
    }
}

/** A 3-stop linear gradient spec the UI layer turns into a platform brush. */
data class GradientSpec(val stops: List<Rgb>) {
    companion object {
        fun from(day: DayColors) = GradientSpec(day.colors)
    }
}
