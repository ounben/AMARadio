package com.ounben.amaradio.utils

import android.content.Context

/**
 * Dummy IntegrityManager for FOSS flavor.
 */
object IntegrityManager {
    fun checkIntegrity(context: Context, nonce: String, onComplete: (String?) -> Unit) {
        // FOSS: App is always considered "integral" or we skip the check
        onComplete(null)
    }
}
