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
import com.infinity.drive.core.files.FileNameUtils
import com.infinity.drive.domain.repository.FileRepository
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.platform.*
import com.infinity.drive.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** One persistent native sheet for additions and their child pages; system pickers open after hide. */
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
    var page by remember{mutableStateOf("actions")}
    var error by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var folderName by remember{mutableStateOf("")}
    val defaultArchiveName = stringResource(Res.string.files_zip_default_name)
    var archiveName by remember { mutableStateOf(defaultArchiveName) }
    LaunchedEffect(visible){if(visible){page="actions";busy=false;folderName="";archiveName=defaultArchiveName;error=null}}
    fun capture(result:CaptureResult){when(result){
        is CaptureResult.Ready->uploads.importAndUpload(listOf(result.reference),target,copyImmediately=true)
        is CaptureResult.Failed->error=result.message
        CaptureResult.Cancelled->Unit
    }}
    fun launchPicker(action:()->Unit){
        if(busy)return
        busy=true
        scope.launch{
            try{state.hide();onDismiss();action()}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){error="Couldn't open this picker. Please try again."}
            finally{busy=false}
        }
    }
    if(visible)DriveBottomSheet(onDismissRequest=onDismiss,sheetState=state){dismiss->
        DriveSheetPage(page){shown->
            when(shown){
                "telegram"->TelegramImportSheet(target){page="actions"}
                "takeout"->Column{
                    IconButton({page="actions"}){Icon(DriveIcons.ArrowBack,"Back to add options")}
                    TakeoutSheet(target=target){page="actions"}
                }
                "zip" -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        IconButton({ page = "actions" }) { Icon(DriveIcons.ArrowBack, stringResource(Res.string.common_back)) }
                        Text(stringResource(Res.string.files_zip_title), style = MaterialTheme.typography.titleLarge)
                    }
                    val finalName = archiveName.trim().let { if (it.endsWith(".zip", true)) it else "$it.zip" }
                    OutlinedTextField(
                        value = archiveName, onValueChange = { archiveName = it },
                        label = { Text(stringResource(Res.string.files_zip_name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { launchPicker { picker.pick { uploads.importAsZip(it, finalName, target) } } },
                        enabled = !busy && FileNameUtils.isValidName(finalName) &&
                            FileNameUtils.isValidName(finalName.dropLast(4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.files_zip_select))
                    }
                }
                "folder"->Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Row{IconButton({if(!busy)page="actions"}){Icon(DriveIcons.ArrowBack,"Back to add options")};Text("New folder",style=MaterialTheme.typography.titleLarge)}
                    OutlinedTextField(folderName,{folderName=it},label={Text("Folder name")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                    Button({if(!busy){busy=true;scope.launch{
                        try{when(files.createFolder(target,folderName.trim())){
                            is AppResult.Success->dismiss()
                            is AppResult.Failure->error="Couldn't create this folder. Try another name."
                        }}catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){error="Couldn't create this folder."}
                        finally{busy=false}
                    }}},enabled=!busy&&FileNameUtils.isValidName(folderName),modifier=Modifier.fillMaxWidth()){Text("Create")}
                }
                else->Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=20.dp)){
                    Text("Add to ${if(target==null)"My Drive"else"this folder"}",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
                    SheetActionRow("Upload files",DriveIcons.FileUpload,enabled=!busy,detail="Anything on this phone, any size"){launchPicker{picker.pick{uploads.importAndUpload(it,target)}}}
                    SheetActionRow("Upload photos and videos",DriveIcons.PhotoLibrary,enabled=!busy,detail="Originals, uncompressed"){launchPicker{device.pickMedia{uploads.importAndUpload(it,target)}}}
                    if (uploads.canCompressImports) {
                        SheetActionRow(
                            stringResource(Res.string.files_zip_title), DriveIcons.Archive, enabled = !busy,
                            detail = stringResource(Res.string.files_zip_description)
                        ) { page = "zip" }
                    }
                    SheetActionRow("Scan a document",DriveIcons.DocumentScanner,enabled=!busy,detail="Capture a document as a PDF"){launchPicker{device.scanDocument(::capture)}}
                    SheetActionRow("Take a photo",DriveIcons.PhotoCamera,enabled=!busy,detail="Goes straight here, not to your gallery"){launchPicker{device.takePhoto(::capture)}}
                    SheetActionRow("New folder",DriveIcons.CreateNewFolder,enabled=!busy){page="folder"}
                    SheetActionRow("Import from Google Photos",DriveIcons.Image,enabled=!busy,detail="Bring your library and albums from Google Takeout"){page="takeout"}
                    SheetActionRow("Save from a Telegram chat",DriveIcons.Share,enabled=!busy,detail="Copy files already in Telegram, with no upload"){page="telegram"}
                    SheetActionRow("Upload folder",DriveIcons.DriveFolderUpload,enabled=!busy,detail="Keep its folder structure"){launchPicker{folderPicker.pick{if(it is PickResult.Picked)uploads.importAndUpload(listOf(it.path),target)}}}
                }
            }
        }
    }
    error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text("Couldn't add this item")},text={Text(message)},confirmButton={TextButton({error=null}){Text("OK")}})}
}
