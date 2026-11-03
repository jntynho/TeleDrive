package com.infinity.drive.presentation.audio

import android.graphics.BitmapFactory
import android.graphics.Color

/** Tiny bounded decode; dominant tones instead of two arbitrary artwork pixels. */
internal fun artworkColors(bytes: ByteArray): List<Long> = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return emptyList()
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 96 && sample < 65_536) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return emptyList()
    try {
        val buckets = mutableMapOf<Int, IntArray>()
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 32)) {
            for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 32)) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                val light = (r * .2126 + g * .7152 + b * .0722) / 255
                if (Color.alpha(pixel) < 200 || light < .08 || light > .88) continue
                val bucket = buckets.getOrPut((r / 32 shl 6) or (g / 32 shl 3) or (b / 32)) { IntArray(4) }
                bucket[0]++; bucket[1] += r; bucket[2] += g; bucket[3] += b
            }
        }
        val ranked = buckets.values.sortedByDescending {
            val max = maxOf(it[1], it[2], it[3]).toFloat()
            val saturation = if (max == 0f) 0f else (max - minOf(it[1], it[2], it[3])) / max
            it[0] * (.4f + saturation)
        }.map { Color.rgb(it[1] / it[0], it[2] / it[0], it[3] / it[0]) }
        val first = ranked.firstOrNull() ?: return emptyList()
        val second = ranked.firstOrNull {
            kotlin.math.abs(Color.red(first) - Color.red(it)) + kotlin.math.abs(Color.green(first) - Color.green(it)) +
                kotlin.math.abs(Color.blue(first) - Color.blue(it)) > 80
        } ?: first
        listOf(first.toLong() and 0xffffffffL, second.toLong() and 0xffffffffL)
    } finally { bitmap.recycle() }
}.getOrDefault(emptyList())
