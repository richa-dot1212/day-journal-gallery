package com.journalgallery.shared.util

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.domain.WeightedColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** JSON codecs for the blobs stored in TEXT columns. */
object Json {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Wc(val r: Int, val g: Int, val b: Int, val w: Float)

    fun encodeWeightedColors(list: List<WeightedColor>): String =
        json.encodeToString(ListSerializer(Wc.serializer()), list.map { Wc(it.color.r, it.color.g, it.color.b, it.weight) })

    fun decodeWeightedColors(text: String): List<WeightedColor> =
        json.decodeFromString(ListSerializer(Wc.serializer()), text)
            .map { WeightedColor(Rgb(it.r, it.g, it.b), it.w) }

    fun encodeDayColors(dc: DayColors): String =
        json.encodeToString(DayColors.serializer(), dc)

    fun decodeDayColors(text: String): DayColors =
        json.decodeFromString(DayColors.serializer(), text)
}
