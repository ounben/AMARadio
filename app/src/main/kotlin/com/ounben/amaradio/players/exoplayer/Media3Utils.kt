package com.ounben.amaradio.players.exoplayer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.StationIconProvider
import java.io.ByteArrayOutputStream

@UnstableApi
object Media3Utils {
    
    /**
     * Centralized metadata builder for Radio Stations.
     * Ensures high compatibility with Android Auto (Gearhead) by:
     * 1. Setting both Title and Subtitle/Artist (required by AA monitor)
     * 2. Using MEDIA_TYPE_MUSIC (1) instead of RADIO_STATION (21) for maximum version support.
     * 3. Using content:// URIs for artwork to avoid Binder transaction limits.
     */
    fun buildMetadata(station: DataRadioStation, liveTitle: String? = null, bitmap: Bitmap? = null): MediaMetadata {
        val stationName = station.Name
        val stationDetails = "${station.Country ?: ""} ${station.TagsAll}".trim().ifEmpty { "Radio" }
        
        // Line 2 content: Priority to Song Title, then Station Details (Country/Tags)
        val line2 = if (!liveTitle.isNullOrEmpty() && liveTitle != stationName) liveTitle else stationDetails
        
        val builder = MediaMetadata.Builder()
            .setTitle(stationName)           // Line 1 (Player & Lists)
            .setDisplayTitle(stationName)    // Line 1 hint
            .setArtist(line2)                // Line 2 (Player)
            .setSubtitle(line2)              // Line 2 (Lists & Notifications)
            .setAlbumTitle(stationName)
            .setAlbumArtist(stationName)
            .setStation(stationName)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            
        // Use the IconProvider URI for the main artwork (Binder-safe)
        val artworkUri = StationIconProvider.getIconUri(station.StationUuid, station.Name, station.IconUrl)
        builder.setArtworkUri(artworkUri)

        // Add extras for legacy Bluetooth and AA hints
        val extras = Bundle().apply {
            putString("com.ounben.amaradio.STATION_ID", station.StationUuid)
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, stationName)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, line2)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, stationName)
            putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
            putInt("androidx.media.utils.extras.MEDIA_TYPE", 1)   // Music
            putString("android.media.metadata.DISPLAY_ICON_URI", artworkUri.toString())
        }
        
        // Only attach raw data if it's small or we really have no URI choice
        if (bitmap != null) {
            try {
                val byteStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteStream)
                builder.setArtworkData(byteStream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                extras.putParcelable("android.media.metadata.DISPLAY_ICON", bitmap)
            } catch (e: Exception) { /* ignore */ }
        }
        
        builder.setExtras(extras)
        return builder.build()
    }
    
    /**
     * Factory for extractors optimized for unstable radio streams.
     * Enables seeking/sniffing even if headers are missing or bitrate is variable.
     */
    fun getRadioExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            // Allow seeking and better sniffing for MP3/AAC streams with variable bitrate
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or 
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_DISABLE_ID3_METADATA // Prevents ID3 parser from getting stuck in ICY data
            )
            .setAdtsExtractorFlags(
                androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
            // Fix for Android Auto PesReader: Radio Italia HLS is AAC-in-TS.
            // We must NOT ignore AAC streams here globally. 
            // The PesReader warnings are better than a non-working stream.
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
