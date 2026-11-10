package com.infinity.drive.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local readiness, never persisted: maintenance can yield to initial local navigation. */
object StartupReadiness {
    private val mutable=MutableStateFlow(false)
    val ready=mutable.asStateFlow()
    fun markReady(){mutable.value=true}
}
