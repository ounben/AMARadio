package com.ounben.amaradio.players.exoplayer

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo

@UnstableApi
object MetadataHandler {

    /**
     * Centralized metadata parsing for Media3 Player Listeners.
     * Processes Ogg/Vorbis and Icecast Headers.
     * Note: IcyInfo is ignored here because IcyDataSource handles it with better timing.
     */
    fun handleMetadata(
        metadata: Metadata,
        onStreamLiveInfo: (StreamLiveInfo) -> Unit,
        onShoutcastInfo: (ShoutcastInfo) -> Unit
    ) {
        var vorbisTitle: String? = null
        var vorbisArtist: String? = null

        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            when (entry) {
                is IcyInfo -> {
                    // IGNORED: We trust IcyDataSource for MP3/AAC metadata 
                    // to avoid flickering and sync issues.
                }
                is IcyHeaders -> {
                    val shoutcastInfo = ShoutcastInfo()
                    shoutcastInfo.audioName = entry.name
                    shoutcastInfo.bitrate = entry.bitrate / 1000
                    onShoutcastInfo(shoutcastInfo)
                }
                is VorbisComment -> {
                    when (entry.key) {
                        "TITLE" -> vorbisTitle = entry.value
                        "ARTIST" -> vorbisArtist = entry.value
                    }
                }
            }
        }

        if (!vorbisTitle.isNullOrEmpty()) {
            val formatted = if (!vorbisArtist.isNullOrEmpty()) "$vorbisArtist - $vorbisTitle" else vorbisTitle
            val liveInfo = StreamLiveInfo(null)
            liveInfo.addMetadata("StreamTitle", formatted)
            onStreamLiveInfo(liveInfo)
        }
    }

    /**
     * Fallback for HLS or other streams where metadata is only available via MediaMetadata.
     */
    fun handleMediaMetadata(
        mediaMetadata: MediaMetadata,
        stationName: String?,
        onStreamLiveInfo: (StreamLiveInfo) -> Unit
    ) {
        // Guard: Ignore static station metadata to prevent flickering back to station name.
        // We check for our custom extra and also compare against the known station name.
        if (mediaMetadata.extras?.containsKey("com.ounben.amaradio.STATION_ID") == true) {
            return
        }

        val title = mediaMetadata.title?.toString()
        if (!title.isNullOrEmpty()) {
            // Further guard: if the title is exactly the station name, it's likely not live info
            if (stationName != null && title.equals(stationName, ignoreCase = true)) {
                return
            }

            val artist = mediaMetadata.artist?.toString()
            val formatted = if (!artist.isNullOrEmpty() && !title.contains(artist)) {
                "$artist - $title"
            } else {
                title
            }

            val liveInfo = StreamLiveInfo(null)
            liveInfo.addMetadata("StreamTitle", formatted)
            onStreamLiveInfo(liveInfo)
        }
    }
}
