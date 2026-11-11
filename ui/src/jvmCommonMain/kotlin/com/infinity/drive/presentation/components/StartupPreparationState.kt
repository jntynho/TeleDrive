package com.infinity.drive.presentation.components

import androidx.compose.runtime.mutableStateMapOf

enum class PreparationOutcome { READY, DEFERRED }

/** A bounded attempt may finish without all optional GPU work; do not label that as prepared. */
class StartupPreparationState {
    private val outcomes=mutableStateMapOf<String,PreparationOutcome>()
    fun needs(mode:String)=mode !in outcomes
    fun complete(mode:String){outcomes[mode]=PreparationOutcome.READY}
    fun deferred(mode:String){outcomes[mode]=PreparationOutcome.DEFERRED}
    fun outcome(mode:String)=outcomes[mode]
    fun reset(){outcomes.clear()}
}
