// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.platform

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import android.os.Environment
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import app.tgdrive.BuildConfig
import app.tgdrive.R
import com.infinity.drive.core.files.DocumentTreePaths
import com.infinity.drive.core.files.StandardBackupFolder
import com.infinity.drive.core.permissions.manifestPermission
import com.infinity.drive.core.permissions.openAllFilesAccess
import com.infinity.drive.core.permissions.openAppSettings
import com.infinity.drive.domain.model.AppTheme
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.applock.requireDeviceOwner
import com.infinity.drive.presentation.common.openLink
import com.infinity.drive.presentation.common.shareLocalFiles
import com.infinity.drive.presentation.components.FileSystemFolderPickerDialog
import com.infinity.drive.presentation.components.FolderListResult
import com.infinity.drive.presentation.components.LocalFolderItem
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ProvidePlatformActions(content: @Composable () -> Unit) {
    val capabilities = koinInject<PlatformCapabilities>()
    val settingsRepository = koinInject<SettingsRepository>()
    val appLock = koinInject<com.infinity.drive.core.security.AppLockManager>()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resolver=koinInject<com.infinity.drive.presentation.preview.PreviewContentResolver>()
    val sources=koinInject<com.infinity.drive.core.media.TelegramDataSourceFactory>()
    val files=koinInject<com.infinity.drive.domain.repository.FileRepository>()
    val audio=koinInject<com.infinity.drive.presentation.audio.AndroidAudioPlayback>()

    val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(UserPreferences())
    val darkTheme = when (prefs.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val backgroundAllowed by androidx.compose.runtime.rememberUpdatedState(prefs.backgroundPlayback)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner,audio,activity){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP&&!backgroundAllowed&&activity?.isChangingConfigurations!=true)audio.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}
    }
    androidx.compose.runtime.LaunchedEffect(audio,settingsRepository){
        settingsRepository.preferences.collect{audio.updateLibraryContext(it.storageChatId,it.onboardingComplete)}
    }

    val urlOpener = remember(context) { UrlOpener { url -> openLink(context, url) } }
    val telegramLinkOpener = remember(context) { AndroidTelegramLinkOpener(context) }

    var activeFolderPickerCallback by remember { mutableStateOf<((PickResult) -> Unit)?>(null) }

    val folderCallback = remember { CallbackHolder<PickResult>() }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        val path = uri?.let { DocumentTreePaths.treeToFilePath(context, it) }
        folderCallback.fire(
            when {
                uri == null -> PickResult.Canceled
                else -> PickResult.Picked(path ?: uri.toString())
            }
        )
    }
    val folderPicker = remember {
        FolderPicker { onPicked ->
            activeFolderPickerCallback = onPicked
        }
    }

    val fileCallback = remember { CallbackHolder<PickResult>() }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val path = uri?.let { DocumentTreePaths.documentToFilePath(context, it) }
        fileCallback.fire(
            when {
                uri == null -> PickResult.Canceled
                path == null -> PickResult.Unreadable
                else -> PickResult.Picked(path)
            }
        )
    }
    val filePicker = remember {
        FilePicker { onPicked ->
            fileCallback.arm(onPicked)
            fileLauncher.launch(arrayOf("*/*"))
        }
    }

    val multiCallback = remember { CallbackHolder<List<String>>() }
    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        multiCallback.fire(uris.map { it.toString() })
    }
    val deviceImports = rememberDeviceImportActions()
    val multiFilePicker = remember {
        MultiFilePicker { onPicked ->
            multiCallback.arm(onPicked)
            runCatching {
                multiLauncher.launch(arrayOf("*/*"))
            }.onFailure {
                multiCallback.fire(emptyList())
            }
        }
    }

    val fileSharer = remember(context) {
        FileSharer { paths, mimeType, chooserTitle ->
            shareLocalFiles(context, paths, mimeType, chooserTitle)
        }
    }

    val consentCallback = remember { CallbackHolder<Boolean>() }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        consentCallback.fire(result.resultCode == Activity.RESULT_OK)
    }
    val deleteConsentLauncher = remember {
        DeleteConsentLauncher { request, onResult ->
            consentCallback.arm(onResult)
            consentLauncher.launch(IntentSenderRequest.Builder(request).build())
        }
    }

    val permissionCallback = remember { CallbackHolder<Unit>() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionCallback.fire(Unit) }
    val permissionRequester = remember {
        PermissionRequester { permissions, onDone ->
            val manifest = permissions.mapNotNull { it.manifestPermission }
            if (manifest.isEmpty()) {
                onDone()
            } else {
                permissionCallback.arm { onDone() }
                permissionLauncher.launch(manifest.toTypedArray())
            }
        }
    }

    val systemScreens = remember(context) {
        object : SystemScreens {
            override fun openAppSettings() = openAppSettings(context)
            override fun openAllFilesAccess() = openAllFilesAccess(context)
        }
    }

    val deviceOwnerGate = remember(activity) {
        DeviceOwnerGate { title, subtitle, onDenied, onConfirmed ->
            requireDeviceOwner(
                activity = activity as? FragmentActivity,
                title = title,
                subtitle = subtitle,
                onDenied = onDenied,
                onConfirmed = onConfirmed
            )
        }
    }

    val hiddenAuthentications=remember(activity){mutableSetOf<Long>()}
    androidx.compose.runtime.DisposableEffect(activity,appLock){onDispose{
        hiddenAuthentications.toList().forEach{appLock.finishDeviceAuthentication(it,false)}
        hiddenAuthentications.clear()
    }}
    val hiddenDeviceGate=remember(activity,appLock){DeviceOwnerGate{title,subtitle,onDenied,onConfirmed->
        val ticket=appLock.beginDeviceAuthentication();hiddenAuthentications.add(ticket)
        fun finish(success:Boolean,action:()->Unit){
            if(hiddenAuthentications.remove(ticket)&&appLock.finishDeviceAuthentication(ticket,success))action()
        }
        try {requireDeviceOwner(activity as? FragmentActivity,title,subtitle,
            onDenied={error->finish(false){onDenied(error)}},allowUnsecured=false,
            onConfirmed={finish(true,onConfirmed)})}
        catch(_:Exception){finish(false){onDenied("Couldn't open device authentication. Please try again.")}}
    }}
    val sensitiveClipboard=remember(context){com.infinity.drive.core.security.AndroidSensitiveClipboard(context)}
    androidx.compose.runtime.DisposableEffect(sensitiveClipboard){onDispose{sensitiveClipboard.close()}}
    val hiddenBiometrics=remember(activity){(activity as? FragmentActivity)?.let{com.infinity.drive.core.security.AndroidHiddenBiometrics(it)}}
    val standardFolders = remember {
        StandardBackupFolder.entries.map { StandardFolderOption(it.labelRes, it.path) }
    }
    val aboutIcon by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,context) {
        value=withContext(Dispatchers.IO){runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256,256).asImageBitmap() }.getOrNull()}
    }
    val appIcon: @Composable (Modifier) -> Unit = { modifier ->
        if(aboutIcon!=null) Image(bitmap=aboutIcon!!,contentDescription=null,modifier=modifier)
        else androidx.compose.material3.Icon(app.tgdrive.ui.DriveIcons.CloudUpload,contentDescription=null,modifier=modifier)
    }

    val externalStorageRoot = remember {
        Environment.getExternalStorageDirectory().absolutePath
    }

    val startupLoader=org.koin.compose.koinInject<coil3.ImageLoader>()
    val startupThumbnails=org.koin.compose.koinInject<com.infinity.drive.data.local.dao.ThumbnailDao>()
    val startupPreparation=remember(context,startupLoader,startupThumbnails){com.infinity.drive.presentation.startup.AndroidStartupPreparation(context,startupLoader,startupThumbnails)}
    val liquidNavigation=if(android.os.Build.VERSION.SDK_INT>=33)com.infinity.drive.presentation.navigation.liquid.rememberLiquidNavigation()else null
    CompositionLocalProvider(
        LocalLiquidNavigation provides liquidNavigation,
        LocalStartupPreparation provides startupPreparation,
        LocalFullyDrawn provides {activity?.reportFullyDrawn()},
        LocalProfilePalette provides {path->withContext(Dispatchers.IO){runCatching{val file=File(path);if(file.length() in 1..5_000_000)com.infinity.drive.presentation.audio.artworkColors(file.readBytes())else emptyList()}.getOrDefault(emptyList())}},
        LocalAudioPlayback provides audio,
        LocalAppIcon provides appIcon,
        LocalStandardFolders provides standardFolders,
        LocalUrlOpener provides urlOpener,
        LocalTelegramLinkOpener provides telegramLinkOpener,
        LocalFolderPicker provides folderPicker,
        LocalFilePicker provides filePicker,
        LocalMultiFilePicker provides multiFilePicker,
        LocalDeviceImportActions provides deviceImports,
        LocalFileSharer provides fileSharer,
        LocalDeleteConsentLauncher provides deleteConsentLauncher,
        LocalPermissionRequester provides permissionRequester,
        LocalSystemScreens provides systemScreens,
        LocalDeviceOwnerGate provides deviceOwnerGate,
        LocalHiddenDeviceOwnerGate provides hiddenDeviceGate,
        LocalHiddenBiometrics provides hiddenBiometrics,
        LocalSensitiveClipboard provides sensitiveClipboard,
        LocalHiddenContentAccess provides org.koin.compose.koinInject<com.infinity.drive.core.security.HiddenContentAccess>(),
        LocalAppVersion provides BuildConfig.VERSION_NAME,
        LocalPlatformScreens provides AndroidPlatformScreens,
        LocalPlatformCapabilities provides capabilities
    ) {
        content()

        activeFolderPickerCallback?.let { callback ->
                DriveInfinityTheme(
                    darkTheme = darkTheme,
                    dynamicColor = prefs.dynamicColor,
                    amoled = prefs.theme == AppTheme.AMOLED
                ) {
                FileSystemFolderPickerDialog(
                    rootPath = externalStorageRoot,
                    listSubfolders = { path ->
                        withContext(Dispatchers.IO) {
                            val dir = File(path)
                            val javaFiles = runCatching { dir.listFiles() }.getOrNull()
                            if (javaFiles == null) {
                                FolderListResult.Unreadable
                            } else {
                                val items = javaFiles.filter { it.isDirectory }
                                    .sortedBy { it.name.lowercase() }
                                    .map { LocalFolderItem(it.name, it.absolutePath) }
                                FolderListResult.Success(items)
                            }
                        }
                    },
                    createSubfolder = { parentPath, name ->
                        withContext(Dispatchers.IO) {
                            runCatching { File(parentPath, name).mkdir() }.getOrDefault(false)
                        }
                    },
                    onUseSaf = {
                        val cb = callback
                        activeFolderPickerCallback = null
                        folderCallback.arm(cb)
                        folderLauncher.launch(null)
                    },
                    onConfirm = { path ->
                        activeFolderPickerCallback = null
                        callback(PickResult.Picked(path))
                    },
                    onDismiss = {
                        activeFolderPickerCallback = null
                        callback(PickResult.Canceled)
                    }
                )
            }
        }
    }
}

internal class CallbackHolder<T> {

    private var pending: ((T) -> Unit)? = null

    fun arm(callback: (T) -> Unit) {
        pending = callback
    }

    fun fire(value: T) {
        val callback = pending
        pending = null
        callback?.invoke(value)
    }
}
