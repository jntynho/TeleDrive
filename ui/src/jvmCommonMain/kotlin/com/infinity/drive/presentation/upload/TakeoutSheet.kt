package com.infinity.drive.presentation.upload

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.platform.*
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoutSheet(target:String?=null,onDismiss:()->Unit) {
    val importer=koinInject<TakeoutImporter>()
    val state by importer.state.collectAsStateWithLifecycle()
    val picker=LocalMultiFilePicker.current
    val url=LocalUrlOpener.current
    DriveBottomSheet(onDismissRequest=onDismiss){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("Import from Google Photos",style=MaterialTheme.typography.titleLarge)
            Text("Bring your originals into TeleDrive using Google Takeout. Exported folders become albums; photo dates and favorites are read from the export metadata.")
            Text("1. Export Google Photos as ZIP files.\n2. Download all export parts to this phone.\n3. Choose the ZIP parts below. Keep this screen or the app open while preparing them.",style=MaterialTheme.typography.bodyMedium)
            OutlinedButton({url.open("https://takeout.google.com/settings/takeout/custom/photos")},Modifier.fillMaxWidth()){Text("Open Google Takeout")}
            if(state.running){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparing ${state.current}",style=MaterialTheme.typography.bodyMedium)
                Text("${state.imported} queued · ${state.skipped} existing · ${state.failed} couldn't import",style=MaterialTheme.typography.bodySmall)
                Text("Uploads already queued continue in Activity. Closing this sheet keeps preparation running; Android can stop preparation if the app is closed.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(importer::cancel,Modifier.fillMaxWidth()){Text("Stop preparing")}
            }else{
                if(state.processed>0)Text("${state.imported} queued · ${state.skipped} existing · ${state.failed} couldn't import")
                Button({picker.pick{importer.start(it,target)}},Modifier.fillMaxWidth()){Text(if(state.processed>0)"Choose more ZIP parts"else"Choose downloaded ZIP parts")}
                Text("If preparation was interrupted, select the same parts again. Already indexed originals are checked before importing.",style=MaterialTheme.typography.bodySmall)
            }
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
