package com.ounben.amaradio.cast

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dummy CastManager for FOSS flavor.
 * Google Cast is not available in the FOSS version.
 */
class CastManager private constructor(context: Context) {
    val castPlayer: Any? = null
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    companion object {
        fun init(context: Context): CastManager = CastManager(context)
        fun getInstance(): CastManager? = null
    }
}
