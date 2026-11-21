package com.infinity.drive.presentation.components

import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.infinity.drive.core.update.AppRelease
import com.infinity.drive.presentation.common.MarkdownText
import com.infinity.drive.presentation.platform.AppUpdateInstallResult
import com.infinity.drive.presentation.platform.LocalAppUpdateInstaller
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.about_update_downloading
import com.infinity.drive.resources.about_update_download_failed
import com.infinity.drive.resources.about_update_from
import com.infinity.drive.resources.about_update_install
import com.infinity.drive.resources.about_update_installer_opened
import com.infinity.drive.resources.about_update_installer_unavailable
import com.infinity.drive.resources.about_update_later
import com.infinity.drive.resources.about_update_permission
import com.infinity.drive.resources.about_update_release_page
import com.infinity.drive.resources.about_update_title
import com.infinity.drive.resources.about_update_verification_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    release: AppRelease,
    currentVersion: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val installer = LocalAppUpdateInstaller.current?.takeIf { it.supports(release) }
    DriveBottomSheet(onDismissRequest = onDismiss, contractOnDismiss = true) { dismiss ->
        val closeThen = LocalSheetCloseThen.current
        val scope = rememberCoroutineScope()
        var downloading by remember(release.version) { mutableStateOf(false) }
        var progress by remember(release.version) { mutableFloatStateOf(0f) }
        var status by remember(release.version) { mutableStateOf<AppUpdateInstallResult?>(null) }
        val openRelease = { closeThen { onOpenUrl(release.pageUrl) } }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("update-sheet")) {
            val notesHeight = minOf(220.dp, maxHeight * .36f)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                            Icon(DriveIcons.Autorenew, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.about_update_title, release.version), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(Res.string.about_update_from, currentVersion, release.version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (release.notes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .04f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.heightIn(max = notesHeight).verticalScroll(rememberScrollState()).padding(15.dp)) {
                            MarkdownText(text = release.notes, onOpenUrl = onOpenUrl)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (downloading) {
                        Text(stringResource(Res.string.about_update_downloading), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        val message = when (status) {
                            AppUpdateInstallResult.PermissionRequired -> Res.string.about_update_permission
                            AppUpdateInstallResult.InstallerOpened -> Res.string.about_update_installer_opened
                            AppUpdateInstallResult.DownloadFailed -> Res.string.about_update_download_failed
                            AppUpdateInstallResult.VerificationFailed -> Res.string.about_update_verification_failed
                            AppUpdateInstallResult.InstallerUnavailable -> Res.string.about_update_installer_unavailable
                            null -> null
                        }
                        if (message != null) {
                            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (installer == null || status == AppUpdateInstallResult.InstallerUnavailable) {
                                openRelease()
                            } else if (!downloading) {
                                downloading = true
                                progress = 0f
                                scope.launch {
                                    try {
                                        status = installer.install(release) { progress = it }
                                    } finally {
                                        downloading = false
                                    }
                                }
                            }
                        },
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(
                            if (installer == null || status == AppUpdateInstallResult.InstallerUnavailable) Res.string.about_update_release_page
                            else Res.string.about_update_install
                        ))
                    }
                    TextButton(onClick = dismiss) { Text(stringResource(Res.string.about_update_later)) }
                }
            }
        }
    }
}
