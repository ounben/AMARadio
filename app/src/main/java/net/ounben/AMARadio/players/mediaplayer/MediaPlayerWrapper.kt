package net.ounben.AMARadio.players.mediaplayer

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.R
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.players.PlayState
import net.ounben.AMARadio.players.PlayerWrapper
import net.ounben.AMARadio.recording.RecordableListener
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class MediaPlayerWrapper(private val playerThreadHandler: Handler) : PlayerWrapper, StreamProxyListener {
    private val TAG = "MediaPlayerWrapper"
    private var mediaPlayer: MediaPlayer? = null
    private var proxy: StreamProxy? = null
    private var stateListener: PlayerWrapper.PlayListener? = null
    private var streamUrl: String? = null
    private var context: Context? = null
    private var isAlarm = false
    private var isHls = false
    override var totalTransferredBytes: Long = 0
        private set
    override var currentPlaybackTransferredBytes: Long = 0
        private set
    private val playerIsInLegalState = AtomicBoolean(false)

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, isAlarm: Boolean) {
        if (streamUrl != this.streamUrl) {
            currentPlaybackTransferredBytes = 0
        }
        this.streamUrl = streamUrl
        this.context = context
        this.isAlarm = isAlarm
        Log.v(TAG, "Stream url:$streamUrl")
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        if (!isHls) {
            if (proxy != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "stopping old proxy.")
                stopProxy()
            }
            proxy = StreamProxy(httpClient, streamUrl, this)
        } else {
            stopProxy()
            onStreamCreated(streamUrl)
        }
    }

    private fun playProxyStream(proxyUrl: String, context: Context?, isAlarm: Boolean) {
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
            mediaPlayer!!.setAudioStreamType(if (isAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC)
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
            Log.e(TAG, "$e")
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

    override fun canRecord(): Boolean = mediaPlayer != null && !isHls

    override fun startRecording(recordableListener: RecordableListener) {
        proxy?.startRecording(recordableListener)
    }

    override fun stopRecording() {
        proxy?.stopRecording()
    }

    override fun isRecording(): Boolean = proxy != null && proxy!!.isRecording()

    override fun getRecordNameFormattingArgs(): Map<String, String>? = null

    override fun getExtension(): String = proxy?.getExtension() ?: "mp3"

    override fun onFoundShoutcastStream(shoutcastInfo: ShoutcastInfo, isHls: Boolean) {
        stateListener?.onDataSourceShoutcastInfo(shoutcastInfo, isHls)
    }

    override fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo) {
        stateListener?.onDataSourceStreamLiveInfo(liveInfo)
    }

    override fun onStreamCreated(proxyConnection: String) {
        playerThreadHandler.post { playProxyStream(proxyConnection, context, isAlarm) }
    }

    override fun onStreamStopped() {
        stop()
    }

    override fun onBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        totalTransferredBytes += length.toLong()
        currentPlaybackTransferredBytes += length.toLong()
    }

    private fun stopProxy() {
        if (proxy != null) {
            try {
                proxy!!.stop()
            } catch (e: Exception) {
                Log.e(TAG, "proxy stop exception: ", e)
            }
            proxy = null
        }
    }
}
