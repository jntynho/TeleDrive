package com.infinity.drive.presentation.platform

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.*
import java.io.File

/** System camera and on-demand Play Services scanner: no camera/OCR engine bundled in the APK. */
@Composable
internal fun rememberDeviceImportActions():DeviceImportActions {
    val context=LocalContext.current
    val activity=LocalActivity.current
    val callbacks=androidx.lifecycle.viewmodel.compose.viewModel<CaptureCallbacks>(key="device-import-callbacks"){CaptureCallbacks()}
    var photoPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    fun deliverPhoto(result:CaptureResult){val callback=callbacks.photo;callbacks.photo=null;callback?.invoke(result)}
    fun deliverScan(result:CaptureResult){val callback=callbacks.scan;callbacks.scan=null;callback?.invoke(result)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success ->
        val photo=photoPath?.let(::File);photoPath=null
        if(success&&photo!=null&&photo.length()>0)deliverPhoto(CaptureResult.Ready(photo.absolutePath))
        else {photo?.delete();deliverPhoto(CaptureResult.Cancelled)}
    }
    val scanner=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result ->
        if(result.resultCode!=Activity.RESULT_OK)deliverScan(CaptureResult.Cancelled)
        else {
            val pdf=GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            if(pdf==null)deliverScan(CaptureResult.Failed("The scanner didn't return a PDF. Try again."))
            else deliverScan(CaptureResult.Ready(pdf.toString()))
        }
    }
    val picked=remember { CallbackHolder<List<String>>() }
    val media=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()){uris ->
        uris.forEach{uri->runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
        picked.fire(uris.map{it.toString()})
    }
    return remember(context,activity,camera,scanner,media) { object:DeviceImportActions {
        override fun pickMedia(onPicked:(List<String>)->Unit){
            picked.arm(onPicked)
            runCatching{media.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}
                .onFailure{picked.fire(emptyList())}
        }
        override fun takePhoto(onResult:(CaptureResult)->Unit){
            if(callbacks.photo!=null){onResult(CaptureResult.Failed("The camera is already open."));return}
            callbacks.photo=onResult
            try {
                val directory=File(context.filesDir,"captures").apply{mkdirs()}
                val file=File(directory,"Photo_${System.currentTimeMillis()}.jpg")
                photoPath=file.absolutePath
                camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.files",file))
            }catch(_:Exception){photoPath?.let(::File)?.delete();photoPath=null;deliverPhoto(CaptureResult.Failed("No camera app is available on this phone."))}
        }
        override fun scanDocument(onResult:(CaptureResult)->Unit){
            if(callbacks.scan!=null){onResult(CaptureResult.Failed("The scanner is already open."));return}
            if(activity==null){onResult(CaptureResult.Failed("Open TeleDrive to start scanning."));return}
            callbacks.scan=onResult
            val options=GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(30)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener{sender->runCatching{scanner.launch(IntentSenderRequest.Builder(sender).build())}.onFailure{deliverScan(CaptureResult.Failed("Couldn't open the document scanner."))}}
                .addOnFailureListener{deliverScan(CaptureResult.Failed("Document scanning requires supported Google Play services. Connect to the internet for its first setup, then try again."))}
        }
    }}
}

internal class CaptureCallbacks:androidx.lifecycle.ViewModel() {
    var photo:((CaptureResult)->Unit)?=null
    var scan:((CaptureResult)->Unit)?=null
    override fun onCleared(){photo=null;scan=null}
}
