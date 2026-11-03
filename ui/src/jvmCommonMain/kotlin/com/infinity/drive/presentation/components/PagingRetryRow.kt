package com.infinity.drive.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PagingRetryRow(onRetry:()->Unit){
    TextButton(onRetry,Modifier.fillMaxWidth().padding(vertical=8.dp)){
        Text("Couldn't load more files · Retry")
    }
}
