package com.ounben.amaradio.players.mediaplayer

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.players.PlayerWrapper
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
class MediaPlayerWrapper(private val playerThreadHandler: Handler) : PlayerWrapper, StreamProxyListener {
    private val tag = "MediaPlayerWrapper"
    private var mediaPlayer: MediaPlayer? = null
    private var proxy: StreamProxy? = null
    private var stateListener: PlayerWrapper.PlayListener? = null
    private var streamUrl: String? = null
    private var context: Context? = null
    private var isHls = false
    override var currentPlaybackTransferredBytes: Long = 0
        private set
    private val playerIsInLegalState = AtomicBoolean(false)

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context) {
        if (streamUrl != this.streamUrl) {
            currentPlaybackTransferredBytes = 0
        }
        this.streamUrl = streamUrl
        this.context = context
        Log.v(tag, "Stream url:$streamUrl")
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        if (!isHls) {
            if (proxy != null) {
                if (Utils.isDebug) Log.d(tag, "stopping old proxy.")
                stopProxy()
            }
            proxy = StreamProxy(httpClient, streamUrl, this)
        } else {
            stopProxy()
            onStreamCreated(streamUrl)
        }
    }

    private fun playProxyStream(proxyUrl: String) {
        playerIsInLegalState.set(false)
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
        }
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
        mediaPlayer!!.reset()
        try {
            @Suppress("DEPRECATION")
            mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer!!.setDataSource(proxyUrl)
            mediaPlayer!!.prepareAsync()
            mediaPlayer!!.setOnPreparedListener {
                playerIsInLegalState.set(true)
                stateListener?.onStateChanged(PlayState.PrePlaying)
                mediaPlayer!!.start()
                stateListener?.onStateChanged(PlayState.Playing)
            }
            mediaPlayer!!.setOnErrorListener { _, _, _ ->
                stateListener?.onPlayerError(R.string.error_play_stream)
                true
            }
        } catch (e: Exception) {
            Log.e(tag, e.toString())
            stateListener?.onPlayerError(R.string.error_play_stream)
        }
    }

    override fun pause() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
                mediaPlayer!!.reset()
                stateListener?.onStateChanged(PlayState.Paused)
            } else {
                stop()
            }
        }
        stopProxy()
    }

    override fun stop() {
        if (mediaPlayer != null) {
            playerIsInLegalState.set(false)
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }
            mediaPlayer!!.release()
            mediaPlayer = null
            playerIsInLegalState.set(true)
        }
        stateListener?.onStateChanged(PlayState.Idle)
        stopProxy()
    }

    override fun isPlaying(): Boolean {
        if (mediaPlayer == null) return false
        return !playerIsInLegalState.get() || mediaPlayer!!.isPlaying
    }

    override val player: androidx.media3.common.Player? = null

    override val bufferedMs: Long = -1

    override val audioSessionId: Int
        get() = mediaPlayer?.audioSessionId ?: 0

    override val isLocal: Boolean = true

    override fun setVolume(newVolume: Float) {
        val vol = newVolume / 100f
        mediaPlayer?.setVolume(vol, vol)
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {
        stateListener = listener
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onFoundShoutcastStream(shoutcastInfo: ShoutcastInfo, isHls: Boolean) {
        stateListener?.onDataSourceShoutcastInfo(shoutcastInfo, isHls)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo) {
        stateListener?.onDataSourceStreamLiveInfo(liveInfo)
    }

    override fun onStreamCreated(proxyConnection: String) {
        playerThreadHandler.post { playProxyStream(proxyConnection) }
    }

    override fun onStreamStopped() {
        stop()
    }

    override fun onBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        currentPlaybackTransferredBytes += length.toLong()
    }

    private fun stopProxy() {
        if (proxy != null) {
            try {
                proxy!!.stop()
            } catch (e: Exception) {
                Log.e(tag, "proxy stop exception: ", e)
            }
            proxy = null
        }
    }
}
