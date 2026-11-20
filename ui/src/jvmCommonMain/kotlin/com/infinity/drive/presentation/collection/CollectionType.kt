// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.collection

import app.tgdrive.ui.DriveIcons
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.collection_archived_empty
import com.infinity.drive.resources.collection_archived_subtitle
import com.infinity.drive.resources.collection_archived_title
import com.infinity.drive.resources.collection_favorites_empty
import com.infinity.drive.resources.collection_favorites_subtitle
import com.infinity.drive.resources.collection_favorites_title
import com.infinity.drive.resources.collection_hidden_empty
import com.infinity.drive.resources.collection_hidden_subtitle
import com.infinity.drive.resources.collection_hidden_title

enum class CollectionType(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    private val iconVector: @Composable () -> ImageVector,
    val emptyMessageRes: StringResource
) {
    FAVORITES(
        titleRes = Res.string.collection_favorites_title,
        subtitleRes = Res.string.collection_favorites_subtitle,
        iconVector = { DriveIcons.Star },
        emptyMessageRes = Res.string.collection_favorites_empty
    ),
    ARCHIVED(
        titleRes = Res.string.collection_archived_title,
        subtitleRes = Res.string.collection_archived_subtitle,
        iconVector = { app.tgdrive.ui.LibraryIcons.Archive },
        emptyMessageRes = Res.string.collection_archived_empty
    ),
    HIDDEN(
        titleRes = Res.string.collection_hidden_title,
        subtitleRes = Res.string.collection_hidden_subtitle,
        iconVector = { app.tgdrive.ui.LibraryIcons.Hidden },
        emptyMessageRes = Res.string.collection_hidden_empty
    );

    val icon: ImageVector @Composable get() = iconVector()
}
