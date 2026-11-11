package com.infinity.drive.presentation.components

import androidx.compose.runtime.mutableStateListOf

/** ViewModel-owned: configuration recreation does not repeat the blocking startup gate. */
class StartupPreparationState {
    private val prepared=mutableStateListOf<String>()
    fun needs(mode:String)=mode !in prepared
    fun complete(mode:String){if(mode !in prepared)prepared+=mode}
    fun reset(){prepared.clear()}
}
