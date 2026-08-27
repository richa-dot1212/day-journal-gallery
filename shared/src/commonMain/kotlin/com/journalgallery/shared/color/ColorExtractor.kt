package com.journalgallery.shared.color

import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.domain.WeightedColor
import com.journalgallery.shared.media.PixelBuffer
import kotlin.math.min

/**
 * Dominant-color extraction on a small downsampled ARGB buffer.
 *
 * Strategy: median-cut to seed [k] well-spread cluster centers, then a few rounds of
 * weighted k-means (Lloyd's) in RGB space to settle them. Fully allocation-light and
 * deterministic so the same image always yields the same colors (needed for cache stability).
 */
object ColorExtractor {

    private const val KMEANS_ITERATIONS = 8
    private const val MAX_SAMPLES = 4096

    /**
     * @return up to [k] colors ordered by descending pixel weight. Near-transparent and
     *   pure-black/white pixels are dropped as non-representative unless that would leave nothing.
     */
    fun extract(buffer: PixelBuffer, k: Int = 3): List<WeightedColor> {
        val samples = collectSamples(buffer)
        if (samples.isEmpty()) return emptyList()

        val effectiveK = min(k, distinctCount(samples))
        if (effectiveK <= 1) {
            return listOf(WeightedColor(meanColor(samples, 0, samples.size / 3), 1f))
        }

        val centers = medianCutSeeds(samples, effectiveK)
        val assignment = IntArray(samples.size / 3)

        repeat(KMEANS_ITERATIONS) {
            var moved = false
            for (i in assignment.indices) {
                val nearest = nearestCenter(samples, i, centers)
                if (nearest != assignment[i]) {
                    assignment[i] = nearest
                    moved = true
                }
            }
            recomputeCenters(samples, assignment, centers)
            if (!moved) return@repeat
        }

        val counts = IntArray(effectiveK)
        for (a in assignment) counts[a]++
        val total = assignment.size.toFloat()

        return (0 until effectiveK)
            .map { c ->
                WeightedColor(
                    Rgb(
                        centers[c * 3].coerceIn(0f, 255f).toInt(),
                        centers[c * 3 + 1].coerceIn(0f, 255f).toInt(),
                        centers[c * 3 + 2].coerceIn(0f, 255f).toInt(),
                    ),
                    if (total == 0f) 0f else counts[c] / total,
                )
            }
            .filter { it.weight > 0f }
            .sortedByDescending { it.weight }
    }

    // --- internals -------------------------------------------------------------

    /** Flat [r0,g0,b0, r1,g1,b1, ...] float array of kept pixels, stride-subsampled to a cap. */
    private fun collectSamples(buffer: PixelBuffer): FloatArray {
        val pixels = buffer.argb
        val stride = maxOf(1, pixels.size / MAX_SAMPLES)
        val kept = ArrayList<Float>(min(pixels.size, MAX_SAMPLES) * 3)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a >= 128) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val isExtreme = (r < 6 && g < 6 && b < 6) || (r > 249 && g > 249 && b > 249)
                if (!isExtreme) {
                    kept.add(r.toFloat()); kept.add(g.toFloat()); kept.add(b.toFloat())
                }
            }
            i += stride
        }
        if (kept.isEmpty()) {
            // Everything was extreme/transparent: fall back to keeping opaque pixels as-is.
            i = 0
            while (i < pixels.size) {
                val p = pixels[i]
                if (((p ushr 24) and 0xFF) >= 128) {
                    kept.add(((p shr 16) and 0xFF).toFloat())
                    kept.add(((p shr 8) and 0xFF).toFloat())
                    kept.add((p and 0xFF).toFloat())
                }
                i += stride
            }
        }
        return kept.toFloatArray()
    }

    private fun distinctCount(samples: FloatArray): Int {
        val seen = HashSet<Int>()
        var i = 0
        while (i < samples.size && seen.size < 8) {
            val key = (samples[i].toInt() shl 16) or (samples[i + 1].toInt() shl 8) or samples[i + 2].toInt()
            seen.add(key)
            i += 3
        }
        return seen.size
    }

    /** Recursive median-cut over the sample cloud, returning [k] mean colors as a flat array. */
    private fun medianCutSeeds(samples: FloatArray, k: Int): FloatArray {
        data class Box(val from: Int, val to: Int) // indices into a shuffled copy, pixel units

        val n = samples.size / 3
        val idx = IntArray(n) { it }
        val boxes = ArrayDeque<Box>()
        boxes.add(Box(0, n))

        fun channelRange(box: Box, ch: Int): Float {
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (p in box.from until box.to) {
                val v = samples[idx[p] * 3 + ch]
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            return hi - lo
        }

        while (boxes.size < k) {
            val box = boxes.maxByOrNull { b ->
                maxOf(channelRange(b, 0), channelRange(b, 1), channelRange(b, 2))
            } ?: break
            if (box.to - box.from <= 1) break
            boxes.remove(box)

            val widest = (0..2).maxByOrNull { channelRange(box, it) } ?: 0
            val slice = idx.copyOfRange(box.from, box.to).toTypedArray()
            slice.sortBy { samples[it * 3 + widest] }
            for (i in slice.indices) idx[box.from + i] = slice[i]
            val mid = box.from + (box.to - box.from) / 2
            boxes.add(Box(box.from, mid))
            boxes.add(Box(mid, box.to))
        }

        val centers = FloatArray(k * 3)
        boxes.forEachIndexed { c, box ->
            if (c >= k) return@forEachIndexed
            var sr = 0f; var sg = 0f; var sb = 0f
            val cnt = (box.to - box.from).coerceAtLeast(1)
            for (p in box.from until box.to) {
                sr += samples[idx[p] * 3]; sg += samples[idx[p] * 3 + 1]; sb += samples[idx[p] * 3 + 2]
            }
            centers[c * 3] = sr / cnt; centers[c * 3 + 1] = sg / cnt; centers[c * 3 + 2] = sb / cnt
        }
        return centers
    }

    private fun nearestCenter(samples: FloatArray, pixel: Int, centers: FloatArray): Int {
        val r = samples[pixel * 3]; val g = samples[pixel * 3 + 1]; val b = samples[pixel * 3 + 2]
        var best = 0; var bestD = Float.MAX_VALUE
        var c = 0
        while (c < centers.size / 3) {
            val dr = r - centers[c * 3]
            val dg = g - centers[c * 3 + 1]
            val db = b - centers[c * 3 + 2]
            val d = dr * dr + dg * dg + db * db
            if (d < bestD) { bestD = d; best = c }
            c++
        }
        return best
    }

    private fun recomputeCenters(samples: FloatArray, assignment: IntArray, centers: FloatArray) {
        val k = centers.size / 3
        val sum = FloatArray(k * 3)
        val cnt = IntArray(k)
        for (i in assignment.indices) {
            val c = assignment[i]
            sum[c * 3] += samples[i * 3]
            sum[c * 3 + 1] += samples[i * 3 + 1]
            sum[c * 3 + 2] += samples[i * 3 + 2]
            cnt[c]++
        }
        for (c in 0 until k) {
            if (cnt[c] > 0) {
                centers[c * 3] = sum[c * 3] / cnt[c]
                centers[c * 3 + 1] = sum[c * 3 + 1] / cnt[c]
                centers[c * 3 + 2] = sum[c * 3 + 2] / cnt[c]
            }
        }
    }

    private fun meanColor(samples: FloatArray, fromPixel: Int, toPixel: Int): Rgb {
        var sr = 0f; var sg = 0f; var sb = 0f
        val cnt = (toPixel - fromPixel).coerceAtLeast(1)
        for (p in fromPixel until toPixel) {
            sr += samples[p * 3]; sg += samples[p * 3 + 1]; sb += samples[p * 3 + 2]
        }
        return Rgb((sr / cnt).toInt(), (sg / cnt).toInt(), (sb / cnt).toInt())
    }
}
