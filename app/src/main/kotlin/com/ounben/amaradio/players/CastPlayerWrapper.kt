package com.ounben.amaradio.players

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import com.ounben.amaradio.players.exoplayer.Media3Utils
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import androidx.core.net.toUri
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CastPlayerWrapper(private val castPlayer: CastPlayer) : PlayerWrapper {

    private var listener: PlayerWrapper.PlayListener? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_READY -> if (castPlayer.playWhenReady) PlayState.Playing else PlayState.Paused
                Player.STATE_BUFFERING -> PlayState.PrePlaying
                Player.STATE_ENDED -> PlayState.Idle
                Player.STATE_IDLE -> PlayState.Idle
                else -> PlayState.Idle
            }
            listener?.onStateChanged(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val state = if (isPlaying) PlayState.Playing else {
                if (castPlayer.playbackState == Player.STATE_BUFFERING) PlayState.PrePlaying else PlayState.Paused
            }
            listener?.onStateChanged(state)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            listener?.onPlayerError(com.ounben.amaradio.R.string.error_station_load)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val liveInfo = StreamLiveInfo(null)
                    liveInfo.addMetadata("StreamTitle", entry.title)
                    listener?.onDataSourceStreamLiveInfo(liveInfo)
                } else if (entry is IcyHeaders) {
                    val shoutcastInfo = ShoutcastInfo()
                    shoutcastInfo.audioName = entry.name
                    shoutcastInfo.bitrate = entry.bitrate / 1000
                    listener?.onDataSourceShoutcastInfo(shoutcastInfo, false)
                }
            }
        }
    }

    init {
        castPlayer.addListener(playerListener)
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, metadata: MediaMetadata?) {
        val mediaItem = Media3Utils.buildLiveMediaItem(streamUrl.toUri(), metadata)
        castPlayer.setMediaItem(mediaItem)
        castPlayer.prepare()
        castPlayer.play()
    }

    override fun pause() {
        castPlayer.pause()
    }

    override fun stop() {
        castPlayer.stop()
    }

    override fun release() {
        castPlayer.removeListener(playerListener)
    }

    override fun isPlaying(): Boolean = castPlayer.isPlaying

    override val player: Player get() = castPlayer

    override val bufferedMs: Long get() = castPlayer.bufferedPosition

    override val audioSessionId: Int get() = 0 

    override val currentPlaybackTransferredBytes: Long get() = 0 

    override val isLocal: Boolean get() = false

    override fun setVolume(newVolume: Float) {
        // Handled via Cast session usually
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {
        this.listener = listener
    }
}
