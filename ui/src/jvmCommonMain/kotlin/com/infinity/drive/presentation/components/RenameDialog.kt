package com.infinity.drive.presentation.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.infinity.drive.core.files.FileNameUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.common_cancel
import com.infinity.drive.resources.common_name

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RenameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val valid=FileNameUtils.isValidName(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError=value.isNotBlank()&&!valid,
                supportingText=if(value.isNotBlank()&&!valid)({Text("Use a name up to 180 characters without path separators or reserved characters.")})else null,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={if(valid)onConfirm(value.trim())}),
                label = { Text(stringResource(Res.string.common_name)) }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.trim()) },
                shapes = ButtonDefaults.shapes(),
                enabled = valid
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}
