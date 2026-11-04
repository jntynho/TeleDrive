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
import com.infinity.drive.presentation.common.Formatters
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadConflictHost() {
    val uploads=koinInject<UploadCoordinator>()
    val request by uploads.conflict.collectAsStateWithLifecycle()
    request?.let { conflict ->
        var all by remember(conflict){mutableStateOf(false)}
        var choice by remember(conflict){mutableStateOf<UploadCoordinator.Choice?>(null)}
        DriveBottomSheet(onDismissRequest={uploads.answer(UploadCoordinator.Answer(choice?:UploadCoordinator.Choice.SKIP,all))}) {dismiss->
            fun choose(value:UploadCoordinator.Choice){if(choice==null){choice=value;dismiss()}}
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=16.dp)) {
                Text("“${conflict.name}” is already in ${conflict.folderName}",Modifier.padding(horizontal=24.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
                ListItem(headlineContent={Text(conflict.existing.name)},supportingContent={Text("In the drive · ${Formatters.bytes(conflict.existing.sizeBytes)}")},leadingContent={FileThumbnail(conflict.existing,Modifier.size(48.dp))})
                ListItem(headlineContent={Text(conflict.name)},supportingContent={Text("New · ${Formatters.bytes(conflict.size)}")},leadingContent={Icon(DriveIcons.InsertDriveFile,null)})
                HorizontalDivider()
                SheetActionRow("Replace",DriveIcons.Refresh,detail="The previous copy goes to Trash") { choose(UploadCoordinator.Choice.REPLACE) }
                SheetActionRow("Keep both",DriveIcons.InsertDriveFile,detail="Save as ${conflict.bothName}") { choose(UploadCoordinator.Choice.KEEP_BOTH) }
                SheetActionRow("Skip",DriveIcons.Close,detail="Don't upload this file") { choose(UploadCoordinator.Choice.SKIP) }
                if(conflict.remaining>1)Row(Modifier.padding(horizontal=12.dp)) {
                    Checkbox(all,{all=it})
                    Text("Use this choice for remaining name conflicts",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
