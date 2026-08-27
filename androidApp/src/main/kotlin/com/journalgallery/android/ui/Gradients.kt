package com.journalgallery.android.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.GradientSpec
import com.journalgallery.shared.domain.Rgb

fun Rgb.toComposeColor(): Color = Color(r, g, b)

fun GradientSpec.toBrush(): Brush = Brush.linearGradient(stops.map { it.toComposeColor() })

fun DayColors.toBrush(): Brush = GradientSpec.from(this).toBrush()

/** Readable text color for a swatch/gradient background. */
fun Rgb.contrastingText(): Color {
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (luminance > 0.55) Color.Black else Color.White
}
