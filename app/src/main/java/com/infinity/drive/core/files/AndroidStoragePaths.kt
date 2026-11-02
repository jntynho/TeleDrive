// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.core.files

import android.content.Context
import android.os.Environment
import java.io.File

class AndroidStoragePaths(
    private val context: Context
) : AppStoragePaths {

    override val filesDir: File
        get() = File(context.filesDir,"drive-v2").apply{mkdirs()}

    override val cacheDir: File
        get() = File(context.cacheDir,"drive-v2").apply{mkdirs()}

    override val externalCacheDir: File?
        get() = context.externalCacheDir?.let{File(it,"drive-v2").apply{mkdirs()}}

    override val externalStorageRoot: File?
        get() = Environment.getExternalStorageDirectory()
}
