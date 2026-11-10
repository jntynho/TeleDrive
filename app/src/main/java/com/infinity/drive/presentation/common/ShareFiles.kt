package com.infinity.drive.presentation.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands local copies to another app. Files still only in Telegram cannot be
 * shared, so callers filter first and tell the user what is missing.
 */
fun shareLocalFiles(
    context: Context,
    paths: List<String>,
    mimeType: String,
    chooserTitle: String
) {
    if(paths.isEmpty())return
    context.startActivity(Intent.createChooser(localFilesShareIntent(context,paths,mimeType),chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun localFilesShareIntent(context:Context,paths:List<String>,mimeType:String):Intent {
    require(paths.isNotEmpty())
    val uris=paths.map { path ->
        if(path.startsWith("content://"))android.net.Uri.parse(path)
        else {val file=File(path);check(file.isFile){"This local copy is missing. Download it again."};FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)}
    }
    return Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type=mimeType
        if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris.first())else putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
        clipData=android.content.ClipData.newUri(context.contentResolver,"Shared file",uris.first()).apply{uris.drop(1).forEach{addItem(android.content.ClipData.Item(it))}}
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
