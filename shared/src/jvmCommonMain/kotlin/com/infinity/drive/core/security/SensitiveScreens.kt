package com.infinity.drive.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class SensitiveScreens {
    private val count=MutableStateFlow(0)
    val active=count.asStateFlow()
    fun enter():Closeable {
        count.update{it+1};val closed=AtomicBoolean()
        return Closeable{if(closed.compareAndSet(false,true))count.update{(it-1).coerceAtLeast(0)}}
    }
}
