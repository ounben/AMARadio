package com.ounben.amaradio.players

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import okhttp3.OkHttpClient

/**
 * Dummy CastPlayerWrapper for FOSS flavor.
 */
class CastPlayerWrapper(player: Any) : PlayerWrapper {
    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, metadata: MediaMetadata?) {}
    override fun pause() {}
    override fun stop() {}
    override fun release() {}
    override fun isPlaying(): Boolean = false
    override val player: Player? = null
    override val bufferedMs: Long = 0
    override val audioSessionId: Int = 0
    override val currentPlaybackTransferredBytes: Long = 0
    override val isLocal: Boolean = false
    override fun setVolume(newVolume: Float) {}
    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {}
}
