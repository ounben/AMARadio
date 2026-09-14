package com.ounben.amaradio.utils

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse

/**
 * Manager for Play Integrity API, replacing the deprecated SafetyNet Attestation API.
 */
object IntegrityManager {
    private const val TAG = "Integrity"

    /**
     * Requests an integrity token from Google Play Services.
     * This token should be sent to your server to verify app and device integrity.
     */
    fun checkIntegrity(context: Context, nonce: String, onComplete: (String?) -> Unit) {
        val integrityManager = IntegrityManagerFactory.create(context)

        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()

        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response: IntegrityTokenResponse ->
                val token = response.token()
                Log.d(TAG, "Integrity token received")
                onComplete(token)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Integrity check failed", e)
                onComplete(null)
            }
    }
}
