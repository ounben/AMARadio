package com.ounben.amaradio.players.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor

@UnstableApi
object Media3Utils {
    
    /**
     * Factory for extractors optimized for unstable radio streams.
     * Enables seeking/sniffing even if headers are missing or bitrate is variable.
     */
    fun getRadioExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            // Allow seeking and better sniffing for MP3/AAC streams with variable bitrate
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or 
                Mp3Extractor.FLAG_DISABLE_ID3_METADATA // Prevents ID3 parser from getting stuck in ICY data
            )
            .setAdtsExtractorFlags(
                AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
    }

    /**
     * Builds a MediaItem optimized for infinite live radio streams.
     * Use C.TIME_UNSET for offsets to let the player decide based on the stream's MIME type.
     */
    fun buildLiveMediaItem(uri: Uri, metadata: MediaMetadata?, mediaId: String? = null): MediaItem {
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            // We use wider bounds to allow the native decoder to stabilize various MIME types
            .setMaxPlaybackSpeed(1.02f)
            .setMinPlaybackSpeed(0.98f)
            .setTargetOffsetMs(C.TIME_UNSET) // Automatic based on buffer/MIME
            .build()

        return MediaItem.Builder()
            .setUri(uri)
            .apply { 
                if (mediaId != null) setMediaId(mediaId)
                if (metadata != null) setMediaMetadata(metadata)
            }
            .setLiveConfiguration(liveConfig)
            .build()
    }
}
