package net.ounben.AMARadio.players

import android.content.Context
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient

interface PlayerWrapper {
    interface PlayListener {
        fun onStateChanged(state: PlayState)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo, isHls: Boolean)
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo)
    }

    fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, isAlarm: Boolean)
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
    val bufferedMs: Long
    val audioSessionId: Int
    val totalTransferredBytes: Long
    val currentPlaybackTransferredBytes: Long
    val isLocal: Boolean
    fun setVolume(newVolume: Float)
    fun setStateListener(listener: PlayListener?)
}
