package com.ounben.amaradio

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEventManager {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun sendEvent(event: Intent) {
        _events.tryEmit(event)
    }
}
