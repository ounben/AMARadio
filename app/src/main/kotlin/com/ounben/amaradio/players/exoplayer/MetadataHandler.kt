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
     * Supports ICY (MP3/AAC) and Vorbis/Opus (Ogg) metadata.
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
                    val liveInfo = StreamLiveInfo(null)
                    liveInfo.addMetadata("StreamTitle", entry.title)
                    onStreamLiveInfo(liveInfo)
                }
                is IcyHeaders -> {
                    val shoutcastInfo = ShoutcastInfo()
                    shoutcastInfo.audioName = entry.name
                    shoutcastInfo.bitrate = entry.bitrate / 1000
                    onShoutcastInfo(shoutcastInfo)
                }
                is VorbisComment -> {
                    // VorbisComment keys are already converted to upper case by Media3's constructor
                    when (entry.key) {
                        "TITLE" -> vorbisTitle = entry.value
                        "ARTIST" -> vorbisArtist = entry.value
                    }
                }
            }
        }

        // If we found Vorbis TITLE, we format it as "Artist - Title" to maintain
        // compatibility with the existing StreamLiveInfo parsing/cleansing logic.
        if (!vorbisTitle.isNullOrEmpty()) {
            val formatted = if (!vorbisArtist.isNullOrEmpty()) "$vorbisArtist - $vorbisTitle" else vorbisTitle
            val liveInfo = StreamLiveInfo(null)
            liveInfo.addMetadata("StreamTitle", formatted)
            onStreamLiveInfo(liveInfo)
        }
    }

    /**
     * Fallback for streams where Vorbis comments are only available via MediaMetadata.
     */
    fun handleMediaMetadata(
        mediaMetadata: MediaMetadata,
        onStreamLiveInfo: (StreamLiveInfo) -> Unit
    ) {
        // Guard: Ignore metadata updates that were pushed by the app itself
        if (mediaMetadata.extras?.containsKey("com.ounben.amaradio.STATION_ID") == true) {
            return
        }

        val title = mediaMetadata.title?.toString()
        if (!title.isNullOrEmpty()) {
            val artist = mediaMetadata.artist?.toString()
            // Format as "Artist - Title" for the existing StreamLiveInfo parser
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
