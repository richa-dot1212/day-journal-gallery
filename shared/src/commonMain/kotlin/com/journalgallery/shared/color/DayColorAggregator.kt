package com.journalgallery.shared.color

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.domain.WeightedColor
import kotlin.math.roundToInt

/**
 * Collapses every item's candidate colors for a day into one set of 3 representative colors.
 *
 * Each item contributes its [ColorExtractor] output; contributions are weighted by
 * `itemWeight * colorWeight` (so a day with 20 photos and a day with 2 both normalize sanely),
 * then the pooled cloud is clustered again to 3.
 */
object DayColorAggregator {

    /**
     * @param perItemColors one entry per media item, each a list of that item's weighted colors.
     * @return exactly 3 colors ordered by descending aggregate weight, or `null` if there is
     *   nothing to aggregate.
     */
    fun aggregate(perItemColors: List<List<WeightedColor>>): DayColors? {
        val pool = ArrayList<WeightedColor>()
        for (item in perItemColors) {
            if (item.isEmpty()) continue
            val itemMass = 1f / perItemColors.size
            for (wc in item) pool.add(WeightedColor(wc.color, wc.weight * itemMass))
        }
        if (pool.isEmpty()) return null

        val clustered = weightedKMeans(pool, k = 3)
        val padded = when {
            clustered.isEmpty() -> return null
            clustered.size >= 3 -> clustered.take(3)
            else -> clustered + List(3 - clustered.size) { clustered.last() }
        }
        return DayColors(padded.map { it.color })
    }

    private fun weightedKMeans(points: List<WeightedColor>, k: Int): List<WeightedColor> {
        val distinct = points.map { it.color }.distinct()
        if (distinct.size <= k) {
            return distinct.map { c ->
                WeightedColor(c, points.filter { it.color == c }.sumOf { it.weight.toDouble() }.toFloat())
            }.sortedByDescending { it.weight }
        }

        // Seed: k highest-weight, mutually distinct-ish points.
        val seeds = points.sortedByDescending { it.weight }
            .fold(mutableListOf<WeightedColor>()) { acc, p ->
                if (acc.size < k && acc.none { close(it.color, p.color, 24) }) acc.add(p)
                acc
            }
        while (seeds.size < k) seeds.add(points[seeds.size % points.size])

        val centers = seeds.map { floatArrayOf(it.color.r.toFloat(), it.color.g.toFloat(), it.color.b.toFloat()) }
            .toMutableList()

        repeat(12) {
            val sums = Array(k) { FloatArray(3) }
            val wsum = FloatArray(k)
            for (p in points) {
                val c = nearest(p.color, centers)
                sums[c][0] += p.color.r * p.weight
                sums[c][1] += p.color.g * p.weight
                sums[c][2] += p.color.b * p.weight
                wsum[c] += p.weight
            }
            for (c in 0 until k) if (wsum[c] > 0f) {
                centers[c] = floatArrayOf(sums[c][0] / wsum[c], sums[c][1] / wsum[c], sums[c][2] / wsum[c])
            }
        }

        val wsum = FloatArray(k)
        for (p in points) wsum[nearest(p.color, centers)] += p.weight

        return centers.indices
            .map { c ->
                WeightedColor(
                    Rgb(
                        centers[c][0].coerceIn(0f, 255f).roundToInt(),
                        centers[c][1].coerceIn(0f, 255f).roundToInt(),
                        centers[c][2].coerceIn(0f, 255f).roundToInt(),
                    ),
                    wsum[c],
                )
            }
            .sortedByDescending { it.weight }
    }

    private fun nearest(color: Rgb, centers: List<FloatArray>): Int {
        var best = 0; var bestD = Float.MAX_VALUE
        centers.forEachIndexed { i, c ->
            val dr = color.r - c[0]; val dg = color.g - c[1]; val db = color.b - c[2]
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun close(a: Rgb, b: Rgb, tol: Int): Boolean =
        kotlin.math.abs(a.r - b.r) <= tol && kotlin.math.abs(a.g - b.g) <= tol && kotlin.math.abs(a.b - b.b) <= tol
}
