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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun rememberAndroidDirectoryTreeDocument(): DirectoryTreeDocument {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val pending = remember { CallbackHolder<Uri?>() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        pending.fire(it)
    }
    return remember(resolver, scope, launcher) {
        DirectoryTreeDocument { write, onResult ->
            pending.arm { uri ->
                if (uri == null) {
                    onResult(null)
                } else scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        var completed = false
                        try {
                            val treeResult = resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { output ->
                                write { line ->
                                    output.write(line)
                                    output.newLine()
                                }
                            } ?: AppResult.Failure(AppError.PermissionDenied())
                            completed = treeResult is AppResult.Success
                            treeResult
                        } catch (_: IOException) {
                            AppResult.Failure(AppError.Unknown())
                        } catch (_: SecurityException) {
                            AppResult.Failure(AppError.PermissionDenied())
                        } finally {
                            if (!completed) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        }
                    }
                    onResult(result)
                }
            }
            try {
                launcher.launch("TeleDrive-tree.txt")
            } catch (_: ActivityNotFoundException) {
                pending.arm {}
                onResult(AppResult.Failure(AppError.UnsupportedOperation()))
            }
        }
    }
}
