package com.infinity.drive.presentation.input

import androidx.compose.runtime.*

/** ViewModel-owned memory only. Secrets never enter saved-instance state or persistent storage. */
class InlineDrafts {
    private val values=mutableMapOf<String,MutableState<String>>()
    fun value(key:String)=values.getOrPut(key){mutableStateOf("")}
    fun clear(){values.values.forEach{it.value=""};values.clear()}
}
val LocalInlineDrafts=staticCompositionLocalOf<InlineDrafts?>{null}
@Composable
fun inlineDraft(key:String):MutableState<String> {
    val store=LocalInlineDrafts.current
    return remember(store,key){store?.value(key)?:mutableStateOf("")}
}
