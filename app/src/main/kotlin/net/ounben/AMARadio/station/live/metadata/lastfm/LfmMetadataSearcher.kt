package net.ounben.AMARadio.station.live.metadata.lastfm

import android.text.TextUtils
import com.google.gson.Gson
import net.ounben.AMARadio.station.live.metadata.TrackMetadata
import net.ounben.AMARadio.station.live.metadata.TrackMetadataCallback
import net.ounben.AMARadio.station.live.metadata.lastfm.data.LfmTrackMetadata
import net.ounben.AMARadio.utils.RateLimiter
import okhttp3.*
import java.io.IOException
import java.util.*

class LfmMetadataSearcher(private val httpClient: OkHttpClient) {
    private val gson = Gson()
    private val rateLimiter = RateLimiter(4, 60 * 1000)

    private fun tryNormalizeTrack(track: String): String? {
        val normalizedTrack = track
            .replace("\\(.*\\)".toRegex(), "")
            .replace("\\[.*\\]".toRegex(), "")
            .replace("\\*.*\\*".toRegex(), "")
            .trim()
        return if (normalizedTrack == track) null else normalizedTrack
    }

    fun fetchTrackMetadata(lastFMApiKey: String?, artist: String?, track: String, trackMetadataCallback: TrackMetadataCallback) {
        if (lastFMApiKey.isNullOrEmpty() || TextUtils.isEmpty(track)) {
            trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
            return
        }

        val trimmedArtist = artist?.trim() ?: ""
        val trimmedTrack = track.trim()

        if (rateLimiter.allowed()) {
            httpClient.newCall(buildRequest(lastFMApiKey, trimmedArtist, trimmedTrack))
                .enqueue(MetadataCallback(trackMetadataCallback, lastFMApiKey, trimmedArtist, trimmedTrack))
        } else {
            trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.RECOVERABLE)
        }
    }

    private fun buildRequest(lastFMApiKey: String, artist: String, track: String): Request {
        val url = String.format(API_GET_TRACK_METADATA, lastFMApiKey, artist, track)
        return Request.Builder().url(url).get().build()
    }

    private inner class MetadataCallback(
        private val trackMetadataCallback: TrackMetadataCallback,
        private val lastFMApiKey: String,
        private val artist: String,
        private val track: String
    ) : Callback {

        override fun onFailure(call: Call, e: IOException) {
            trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.RECOVERABLE)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val lfmTrackMetadata = gson.fromJson(response.body?.charStream(), LfmTrackMetadata::class.java)
                val trackData = lfmTrackMetadata?.track

                if (trackData == null) {
                    val normalizedTrack = tryNormalizeTrack(track)
                    if (normalizedTrack != null && normalizedTrack.length > 3) {
                        httpClient.newCall(buildRequest(lastFMApiKey, artist, normalizedTrack))
                            .enqueue(MetadataCallback(trackMetadataCallback, lastFMApiKey, artist, normalizedTrack))
                    } else {
                        trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
                    }
                    return
                }

                val trackMetadata = TrackMetadata()
                trackMetadata.artist = trackData.artist?.name
                trackMetadata.track = trackData.name

                val albumArts = mutableListOf<TrackMetadata.AlbumArt>()
                trackMetadata.albumArts = albumArts

                trackData.album?.let { album ->
                    trackMetadata.album = album.title
                    album.image?.forEach { img ->
                        val artSize = when (img.size) {
                            "small" -> TrackMetadata.AlbumArtSize.SMALL
                            "medium" -> TrackMetadata.AlbumArtSize.MEDIUM
                            "large" -> TrackMetadata.AlbumArtSize.LARGE
                            "extralarge" -> TrackMetadata.AlbumArtSize.EXTRA_LARGE
                            else -> TrackMetadata.AlbumArtSize.SMALL
                        }
                        albumArts.add(TrackMetadata.AlbumArt(artSize, img.text ?: ""))
                    }
                    albumArts.sortByDescending { it.size }
                }

                trackMetadataCallback.onSuccess(trackMetadata)
            } catch (ex: Exception) {
                trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
            } finally {
                response.close()
            }
        }
    }

    companion object {
        private const val API_GET_TRACK_METADATA = "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=%s&artist=%s&track=%s&format=json"
    }
}
