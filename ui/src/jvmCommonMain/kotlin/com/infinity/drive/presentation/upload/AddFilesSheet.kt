package com.infinity.drive.presentation.upload

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.platform.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The same native addition sheet in Home and every drive folder. Child sheets replace it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilesSheet(visible:Boolean,target:String?=null,onDismiss:()->Unit) {
    val uploads=koinInject<UploadCoordinator>()
    val files=koinInject<FileRepository>()
    val picker=LocalMultiFilePicker.current
    val folderPicker=LocalFolderPicker.current
    val device=LocalDeviceImportActions.current
    val scope=rememberCoroutineScope()
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var child by remember{mutableStateOf<String?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var creating by remember{mutableStateOf(false)}
    fun capture(result:CaptureResult){when(result){
        is CaptureResult.Ready->uploads.importAndUpload(listOf(result.reference),target,copyImmediately=true)
        is CaptureResult.Failed->error=result.message
        CaptureResult.Cancelled->Unit
    }}
    fun run(action:()->Unit){scope.launch{state.hide();onDismiss();action()}}
    if(visible)DriveBottomSheet(onDismissRequest=onDismiss,sheetState=state){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=20.dp)){
            Text("Add to ${if(target==null)"My Drive"else"this folder"}",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
            SheetActionRow("Upload files",DriveIcons.FileUpload,detail="Anything on this phone, any size"){run{picker.pick{uploads.importAndUpload(it,target)}}}
            SheetActionRow("Upload photos and videos",DriveIcons.PhotoLibrary,detail="Originals, uncompressed"){run{device.pickMedia{uploads.importAndUpload(it,target)}}}
            SheetActionRow("Scan a document",DriveIcons.DocumentScanner,detail="Straightened and cropped into a PDF"){run{device.scanDocument(::capture)}}
            SheetActionRow("Take a photo",DriveIcons.PhotoCamera,detail="Goes straight here, not to your gallery"){run{device.takePhoto(::capture)}}
            SheetActionRow("New folder",DriveIcons.CreateNewFolder){run{child="folder"}}
            SheetActionRow("Import from Google Photos",DriveIcons.Image,detail="Bring your library and albums from Google Takeout"){run{child="takeout"}}
            SheetActionRow("Save from a Telegram chat",DriveIcons.Share,detail="Copy files already in Telegram, with no upload"){run{child="telegram"}}
            SheetActionRow("Upload folder",DriveIcons.DriveFolderUpload,detail="Keep its folder structure"){run{folderPicker.pick{if(it is PickResult.Picked)uploads.importAndUpload(listOf(it.path),target)}}}
        }
    }
    when(child){
        "telegram"->TelegramImportSheet(target){child=null}
        "takeout"->TakeoutSheet{child=null}
        "folder"->RenameDialog("New folder","","Create",onConfirm={name->
            if(!creating){creating=true;scope.launch{
                try{if(files.createFolder(target,name) is AppResult.Failure)error="Couldn't create this folder. Try another name.";child=null}
                catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(_:Exception){error="Couldn't create this folder."}
                finally{creating=false}
            }}
        },onDismiss={if(!creating)child=null})
    }
    error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text("Couldn't add this item")},text={Text(message)},confirmButton={TextButton({error=null}){Text("OK")}})}
}
