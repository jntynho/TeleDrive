package com.infinity.drive.presentation.preview

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.view.WindowManager
import com.infinity.drive.core.media.ExternalMediaStreams
import com.infinity.drive.domain.model.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun openExternalMedia(activity: Activity, streams: ExternalMediaStreams, file: DriveFile, chooser: String) {
    check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
    val uri = withContext(Dispatchers.IO) { streams.authorize(activity, file.id) }
    try {
        check(!activity.isFinishing && activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
        val target = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
        activity.startActivity(Intent.createChooser(target, chooser))
    } catch (failure: Exception) {
        streams.revoke(uri)
        throw failure
    }
}
