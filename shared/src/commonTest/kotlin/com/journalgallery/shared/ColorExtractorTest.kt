package com.journalgallery.shared

import com.journalgallery.shared.color.ColorExtractor
import com.journalgallery.shared.color.DayColorAggregator
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.domain.WeightedColor
import com.journalgallery.shared.media.PixelBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorExtractorTest {

    private fun solid(argb: Int, n: Int = 64) = PixelBuffer(n, n, IntArray(n * n) { argb })

    private fun halfAndHalf(a: Int, b: Int, n: Int = 64): PixelBuffer {
        val px = IntArray(n * n) { if (it % n < n / 2) a else b }
        return PixelBuffer(n, n, px)
    }

    @Test
    fun solidImage_yieldsThatColor() {
        val red = 0xFFCC2020.toInt()
        val out = ColorExtractor.extract(solid(red), k = 3)
        assertTrue(out.isNotEmpty())
        val top = out.first().color
        assertTrue(kotlin.math.abs(top.r - 0xCC) < 12, "got $top")
        assertTrue(top.g < 48 && top.b < 48, "got $top")
    }

    @Test
    fun twoColorImage_recoversBoth() {
        val blue = 0xFF2040D0.toInt()
        val green = 0xFF20C040.toInt()
        val out = ColorExtractor.extract(halfAndHalf(blue, green), k = 3)
        val hasBlue = out.any { it.color.b > 150 && it.color.r < 100 }
        val hasGreen = out.any { it.color.g > 150 && it.color.r < 100 }
        assertTrue(hasBlue && hasGreen, "expected blue+green, got ${out.map { it.color }}")
    }

    @Test
    fun deterministic() {
        val img = halfAndHalf(0xFF803010.toInt(), 0xFF101060.toInt())
        assertEquals(ColorExtractor.extract(img), ColorExtractor.extract(img))
    }

    @Test
    fun dayAggregation_alwaysReturnsThree() {
        val itemA = listOf(WeightedColor(Rgb(200, 30, 30), 0.7f), WeightedColor(Rgb(30, 30, 30), 0.3f))
        val itemB = listOf(WeightedColor(Rgb(30, 30, 200), 1.0f))
        val day = DayColorAggregator.aggregate(listOf(itemA, itemB))
        assertEquals(3, day!!.colors.size)
    }

    @Test
    fun dayAggregation_emptyIsNull() {
        assertEquals(null, DayColorAggregator.aggregate(listOf(emptyList(), emptyList())))
    }
}
