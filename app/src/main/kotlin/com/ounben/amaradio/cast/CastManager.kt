package com.ounben.amaradio.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CastManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var _castPlayer: CastPlayer? = null
    val castPlayer: CastPlayer? get() = _castPlayer

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: $sessionId")
            _isCasting.value = true
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _isCasting.value = false
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended")
            _isCasting.value = false
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Cast session resumed")
            _isCasting.value = true
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _isCasting.value = false
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }
    }

    init {
        // CastContext must be initialized on the main thread
        scope.launch {
            try {
                val castContext = CastContext.getSharedInstance(appContext)
                _castPlayer = CastPlayer(castContext, DefaultMediaItemConverter())
                castContext.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                _isCasting.value = castContext.sessionManager.currentCastSession?.isConnected == true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CastContext", e)
            }
        }
    }

    companion object {
        private const val TAG = "CastManager"
        @Volatile
        private var instance: CastManager? = null

        fun init(context: Context): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager(context).also { instance = it }
            }
        }

        fun getInstance(): CastManager? = instance
    }
}
