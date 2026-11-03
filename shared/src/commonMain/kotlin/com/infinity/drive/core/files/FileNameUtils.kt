// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.files

object FileNameUtils {

    private val invalidChars = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun isValidName(name: String): Boolean = name.trim().let {
        it.isNotEmpty() && it != "." && it != ".." && it.length <= 180 && !invalidChars.containsMatchIn(it)
    }

    fun sanitize(name: String): String =
        invalidChars.replace(name.trim(), "_").take(180).takeUnless { it.isEmpty() || it=="." || it==".." } ?: "unnamed"

    /** A restored caption must never escape the selected download directory. */
    fun relativeFolderPath(path:String?):String? {
        if(path.isNullOrBlank())return null
        val segments=path.replace('\\','/').split('/').filter { it.isNotEmpty() }
        require(segments.none { it==".." || it=="." }) { "Unsafe download folder path" }
        return segments.joinToString("/") { sanitize(it) }.takeIf { it.isNotEmpty() }
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "")

    fun baseNameOf(name: String): String = name.substringBeforeLast('.', name)

    /** Produces "name (1).ext", "name (2).ext", ... until [exists] returns false. */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        if (!exists(desired)) return desired
        val base = baseNameOf(desired)
        val ext = extensionOf(desired)
        var index = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            if (!exists(candidate)) return candidate
            index++
        }
    }
}
