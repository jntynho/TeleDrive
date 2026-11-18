package com.infinity.drive.data.repository

internal fun directoryTreeLine(name: String, depth: Int, folder: Boolean): String = buildString {
    repeat(depth) { append("  ") }
    append(if (folder) "[Folder] " else "[File] ")
    for (character in name) {
        when {
            character == '\\' -> append("\\\\")
            character.isISOControl() || character in '\u2028'..'\u202E' ||
                character in '\u2066'..'\u2069' -> {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
            else -> append(character)
        }
    }
}
