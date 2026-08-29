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
import androidx.media3.extractor.mp3.Mp3Extractor
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.StationIconProvider
import java.io.ByteArrayOutputStream

@UnstableApi
object Media3Utils {
    
    /**
     * Centralized metadata builder for Radio Stations.
     */
    fun buildMetadata(station: DataRadioStation, liveTitle: String? = null, bitmap: Bitmap? = null): MediaMetadata {
        val stationName = station.Name
        val stationDetails = "${station.Country} ${station.TagsAll}".trim().ifEmpty { "Radio" }
        
        val line2 = if (!liveTitle.isNullOrEmpty() && liveTitle != stationName) liveTitle else stationDetails
        
        val builder = MediaMetadata.Builder()
            .setTitle(stationName)
            .setDisplayTitle(stationName)
            .setArtist(line2)
            .setSubtitle(line2)
            .setAlbumTitle(stationName)
            .setAlbumArtist(stationName)
            .setStation(stationName)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            
        val artworkUri = StationIconProvider.getIconUri(station.StationUuid, station.Name, station.IconUrl)
        
        if (bitmap == null) {
            builder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putString("com.ounben.amaradio.STATION_ID", station.StationUuid)
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, stationName)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, line2)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, stationName)
            putInt("androidx.media.utils.extras.CONTENT_TYPE", 1)
            putInt("androidx.media.utils.extras.MEDIA_TYPE", 1)
            
            if (bitmap == null) {
                putString("android.media.metadata.DISPLAY_ICON_URI", artworkUri.toString())
            }
        }
        
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
     * Builds metadata specifically for browsable folders in Android Auto.
     */
    fun buildFolderMetadata(title: String, iconUri: Uri?): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(iconUri)
            .setExtras(Bundle().apply {
                putInt("androidx.media.utils.extras.CONTENT_TYPE", 1)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                if (iconUri != null) {
                    putString("android.media.metadata.DISPLAY_ICON_URI", iconUri.toString())
                }
            })
            .build()
    }
    
    /**
     * Factory for extractors optimized for live radio streams.
     * We DISABLE seeking and indexing flags as they cause long delays 
     * while the player tries to "calculate" the duration of an infinite stream.
     */
    fun getRadioExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
            .setAdtsExtractorFlags(0)
    }

    /**
     * Builds a MediaItem optimized for infinite live radio streams.
     */
    fun buildLiveMediaItem(uri: Uri, metadata: MediaMetadata?, mediaId: String? = null): MediaItem {
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setMaxPlaybackSpeed(1.0f)
            .setMinPlaybackSpeed(1.0f)
            .setTargetOffsetMs(C.TIME_UNSET)
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
