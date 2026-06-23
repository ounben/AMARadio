package com.ounben.amaradio.players

import android.content.Context
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
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

    fun playRemote(httpClient: okhttp3.OkHttpClient, streamUrl: String, context: Context)
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
    val player: androidx.media3.common.Player?
    val bufferedMs: Long
    val audioSessionId: Int
    val totalTransferredBytes: Long
    val currentPlaybackTransferredBytes: Long
    val isLocal: Boolean
    fun setVolume(newVolume: Float)
    fun setStateListener(listener: PlayListener?)
}
