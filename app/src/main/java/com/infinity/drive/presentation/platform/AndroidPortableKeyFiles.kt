package com.infinity.drive.presentation.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.infinity.drive.core.crypto.KeyBackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun rememberAndroidPortableKeyFiles(): PortableKeyFiles {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val saveCallback = remember { CallbackHolder<Uri?>() }
    val openCallback = remember { CallbackHolder<Uri?>() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        saveCallback.fire(it)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        openCallback.fire(it)
    }
    return remember(resolver, scope, save, open) {
        object : PortableKeyFiles {
            override fun save(bytes: ByteArray, onResult: (PortableKeyFileResult<Unit>) -> Unit) {
                saveCallback.arm { uri ->
                    if (uri == null) onResult(PortableKeyFileResult.Cancelled)
                    else scope.launch {
                        onResult(withContext(Dispatchers.IO) {
                            try {
                                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                                    ?: throw IOException("No output")
                                PortableKeyFileResult.Success(Unit)
                            } catch (_: IOException) {
                                PortableKeyFileResult.Failed
                            } catch (_: SecurityException) {
                                PortableKeyFileResult.Failed
                            }
                        })
                    }
                }
                try {
                    save.launch("TeleDrive-content-key.tdkey")
                } catch (_: android.content.ActivityNotFoundException) {
                    onResult(PortableKeyFileResult.Failed)
                    saveCallback.arm {}
                }
            }

            override fun open(onResult: (PortableKeyFileResult<ByteArray>) -> Unit) {
                openCallback.arm { uri ->
                    if (uri == null) onResult(PortableKeyFileResult.Cancelled)
                    else scope.launch {
                        onResult(withContext(Dispatchers.IO) {
                            try {
                                val bytes = resolver.openInputStream(uri)?.use { input ->
                                    val buffer = ByteArray(KeyBackupCodec.MAX_BACKUP_BYTES + 1)
                                    var size = 0
                                    while (size < buffer.size) {
                                        val read = input.read(buffer, size, buffer.size - size)
                                        if (read <= 0) break
                                        size += read
                                    }
                                    if (size !in 1..KeyBackupCodec.MAX_BACKUP_BYTES) null else buffer.copyOf(size)
                                }
                                if (bytes == null) PortableKeyFileResult.Failed
                                else PortableKeyFileResult.Success(bytes)
                            } catch (_: IOException) {
                                PortableKeyFileResult.Failed
                            } catch (_: SecurityException) {
                                PortableKeyFileResult.Failed
                            }
                        })
                    }
                }
                try {
                    open.launch(arrayOf("*/*"))
                } catch (_: android.content.ActivityNotFoundException) {
                    onResult(PortableKeyFileResult.Failed)
                    openCallback.arm {}
                }
            }
        }
    }
}
