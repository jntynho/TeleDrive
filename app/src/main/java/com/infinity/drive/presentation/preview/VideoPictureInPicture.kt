package com.infinity.drive.presentation.preview

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

internal class VideoPictureInPicture(private val activity: ComponentActivity?) {
    var active by mutableStateOf(activity?.isInPictureInPictureMode == true)
        private set

    fun supported(): Boolean {
        val host = activity ?: return false
        return host.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            host.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0
    }

    fun enter(width: Int, height: Int): Boolean {
        val host = activity ?: return false
        if (!supported() || host.isFinishing || active) return false
        return try {
            host.enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(pictureInPictureRatio(width, height)).build()).also { entered ->
                if (entered) active = true
            }
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun update(inPip: Boolean) {
        active = inPip
    }
}

@Composable
internal fun rememberVideoPictureInPicture(): VideoPictureInPicture {
    val activity = LocalActivity.current as? ComponentActivity
    val state = remember(activity) { VideoPictureInPicture(activity) }
    DisposableEffect(activity, state) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { state.update(it.isInPictureInPictureMode) }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return state
}

internal fun pictureInPictureRatio(width: Int, height: Int): Rational {
    if (width <= 0 || height <= 0) return Rational(16, 9)
    val ratio = width.toDouble() / height
    return if (ratio in (1.0 / 2.39)..2.39) Rational(width, height) else Rational(16, 9)
}
