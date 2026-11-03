package com.infinity.drive.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.DialogProperties
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.session_broken_action
import com.infinity.drive.resources.session_broken_message
import com.infinity.drive.resources.session_broken_title

/**
 * Shown when the stored session cannot be read, which leaves the app unable to
 * reach Telegram at all. There is nothing to go back to, so the dialog has no
 * way out other than signing in again.
 */
@Composable
fun SessionBrokenDialog(onSignInAgain: () -> Unit) {
    var confirmReset by rememberSaveable{mutableStateOf(false)}
    if(confirmReset){
        ConfirmDialog(title="Reset local session?",
            message="This removes the local session, index and app-managed copies on this device. Files already uploaded to Telegram remain there. Keep another copy of files that have not been uploaded before continuing.",
            confirmLabel="Reset session",destructive=true,
            onConfirm={confirmReset=false;onSignInAgain()},onDismiss={confirmReset=false})
        return
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(Res.string.session_broken_title)) },
        text = { Text(stringResource(Res.string.session_broken_message)) },
        confirmButton = {
            Button(onClick = {confirmReset=true}, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.session_broken_action))
            }
        }
    )
}
