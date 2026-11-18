package com.infinity.drive.core.files

/** Stable SQLite sort keys. A run of digits compares by numeric magnitude without parsing it. */
internal object FileOrderKeys {
    fun natural(name: String): String {
        val text = name.lowercase()
        return buildString {
            var position = 0
            while (position < text.length) {
                if (text[position] !in '0'..'9') {
                    append(text[position++])
                    continue
                }
                val start = position
                while (position < text.length && text[position] in '0'..'9') position++
                var significant = start
                while (significant < position - 1 && text[significant] == '0') significant++
                append('0')
                append((position - significant).toString().padStart(10, '0'))
                append(text, significant, position)
                append(' ')
            }
        }
    }

    fun byExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && dot < name.lastIndex) name.substring(dot + 1).lowercase() else ""
        return extension + '\u0001' + natural(name)
    }
}
