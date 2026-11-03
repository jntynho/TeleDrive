package com.infinity.drive.presentation.audio

data class LyricWord(val timeMs: Long, val text: String)
data class LyricCue(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())
data class AudioLyrics(val cues: List<LyricCue>, val plainText: String) {
    fun activeIndex(positionMs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (cues[middle].timeMs <= positionMs) low = middle + 1 else high = middle - 1
        }
        return high
    }
}

/** Bounded local LRC/enhanced-LRC parsing; plain lyrics never receive invented timing. */
object AudioLyricsParser {
    private val lineTime = Regex("\\[((?:\\d{1,2}:)?\\d{1,3}:[0-5]\\d(?:\\.\\d{1,3})?)]")
    private val wordTime = Regex("<((?:\\d{1,2}:)?\\d{1,3}:[0-5]\\d(?:\\.\\d{1,3})?)>")
    private val offsetTag = Regex("\\[offset:([+-]?\\d{1,9})]", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("\\[(?:ar|al|ti|au|by|re|ve|length|offset):[^]]*]", RegexOption.IGNORE_CASE)
    fun parse(raw: String?): AudioLyrics {
        val source = raw.orEmpty().take(32_000).replace("\r\n", "\n").trim('\uFEFF')
        val offset = offsetTag.findAll(source).lastOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val cues = mutableListOf<LyricCue>()
        val plain = mutableListOf<String>()
        source.lineSequence().take(1_024).forEach { line ->
            val stamps = lineTime.findAll(line).toList()
            val body = metadataTag.replace(lineTime.replace(line, ""), "").trim()
            val text = wordTime.replace(body, "").trim()
            if (text.isNotEmpty()) plain += text
            val wordStamps = wordTime.findAll(body).take(256).toList()
            stamps.take(32).forEach stampLoop@ { stamp ->
                val time = timestamp(stamp.groupValues[1]) ?: return@stampLoop
                val first = stamps.firstOrNull()?.groupValues?.get(1)?.let(::timestamp) ?: time
                val words = buildList {
                    val prefix = body.substring(0, wordStamps.firstOrNull()?.range?.first ?: 0)
                    if (prefix.isNotEmpty()) add(LyricWord((time + offset).coerceAtLeast(0), prefix))
                    wordStamps.forEachIndexed { index, match ->
                        val start = timestamp(match.groupValues[1]) ?: return@forEachIndexed
                        val end = wordStamps.getOrNull(index + 1)?.range?.first ?: body.length
                        add(LyricWord((start + time - first + offset).coerceAtLeast(0), body.substring(match.range.last + 1, end)))
                    }
                }
                if (cues.size < 1_024) cues += LyricCue((time + offset).coerceAtLeast(0), text, words)
            }
        }
        val ordered = cues.sortedBy { it.timeMs }.groupBy { it.timeMs }.map { (time, simultaneous) ->
            if (simultaneous.size == 1) simultaneous.first()
            else LyricCue(time, simultaneous.map { it.text }.distinct().joinToString("\n"))
        }
        return AudioLyrics(ordered, plain.joinToString("\n"))
    }
    private fun timestamp(value: String): Long? {
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().split('.')
        val fraction = seconds.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        val minute = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
        val hour = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return ((hour * 3_600 + minute * 60 + (seconds[0].toLongOrNull() ?: return null)) * 1_000 + fraction)
            .takeIf { it in 0..86_400_000 }
    }
}
