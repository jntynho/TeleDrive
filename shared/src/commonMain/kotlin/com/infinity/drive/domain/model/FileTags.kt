package com.infinity.drive.domain.model

object FileTags {
    fun parse(input: String): List<String>? = normalize(input.split(',', '،'))

    fun normalize(values: List<String>): List<String>? {
        val tags = values.map { it.trim().removePrefix("#").trim().lowercase() }
            .filter { it.isNotBlank() }.distinct()
        if (tags.size > 8 || tags.any { tag ->
            tag.length > 24 || tag.any { ch -> ch == '|' || ch.isISOControl() || ch == ',' || ch == '،' }
        } || tags.sumOf { it.encodeToByteArray().size } > 96) return null
        return tags
    }

    fun encode(tags: List<String>): String = if (tags.isEmpty()) "" else tags.joinToString("|", "|", "|")

    fun decode(encoded: String): List<String> = normalize(encoded.split('|')).orEmpty()

    fun searchTag(query: String): String? = query.trim().takeIf { it.startsWith('#') }
        ?.let { parse(it)?.singleOrNull() }
}
