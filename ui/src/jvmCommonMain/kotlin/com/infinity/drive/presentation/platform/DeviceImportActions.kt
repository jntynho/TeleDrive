package com.infinity.drive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface CaptureResult {
    data class Ready(val reference:String):CaptureResult
    data class Failed(val message:String):CaptureResult
    data object Cancelled:CaptureResult
}
interface DeviceImportActions {
    fun pickMedia(onPicked:(List<String>)->Unit)
    fun scanDocument(onResult:(CaptureResult)->Unit)
    fun takePhoto(onResult:(CaptureResult)->Unit)
}
val LocalDeviceImportActions=staticCompositionLocalOf<DeviceImportActions>{error("Device import actions are not provided")}
