package com.infinity.drive.presentation.platform

import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.data.repository.FolderArchiveExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.IOException

@Composable
internal fun rememberAndroidFolderArchiveDocument(): FolderArchiveDocument {
    val resolver = LocalContext.current.contentResolver
    val exporter = koinInject<FolderArchiveExporter>()
    val scope = rememberCoroutineScope()
    val pending = remember { CallbackHolder<Uri?>() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        pending.fire(it)
    }
    return remember(resolver, exporter, scope, launcher) {
        FolderArchiveDocument { folder, progress, onResult ->
            pending.arm { uri ->
                if (uri == null) onResult(null)
                else scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        var complete = false
                        try {
                            val archive = resolver.openOutputStream(uri, "wt")?.use { output ->
                                exporter.export(folder, output.buffered(), progress)
                            } ?: AppResult.Failure(AppError.PermissionDenied())
                            complete = archive is AppResult.Success
                            archive
                        } catch (_: IOException) {
                            AppResult.Failure(AppError.TransferFailed())
                        } catch (_: SecurityException) {
                            AppResult.Failure(AppError.PermissionDenied())
                        } finally {
                            if (!complete) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        }
                    }
                    onResult(result)
                }
            }
            try {
                launcher.launch("TeleDrive-backup.zip")
            } catch (_: ActivityNotFoundException) {
                pending.arm {}
                onResult(AppResult.Failure(AppError.UnsupportedOperation()))
            }
        }
    }
}
