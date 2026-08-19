package com.ounben.amaradio.station.live

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class StreamLiveInfo(
    var rawMetadata: Map<String, String>? = null,
    var title: String = "",
    var artist: String = "",
    var track: String = ""
) : Parcelable {

    init {
        updateFromMetadata()
    }

    fun addMetadata(key: String, value: String?) {
        val mutableMetadata = rawMetadata?.toMutableMap() ?: HashMap()
        mutableMetadata[key] = value ?: ""
        rawMetadata = mutableMetadata
        updateFromMetadata()
    }

    private fun updateFromMetadata() {
        val fullTitle = rawMetadata?.get("StreamTitle") ?: ""
        if (fullTitle.isEmpty()) return

        // 1. Pattern: artist="Name" title="Song" (Pure Technical iHeart style)
        val techArtist = Regex("""artist="([^"]+)"""", RegexOption.IGNORE_CASE).find(fullTitle)?.groupValues?.get(1)
        val techTitle = Regex("""(?:title|text)="([^"]+)"""", RegexOption.IGNORE_CASE).find(fullTitle)?.groupValues?.get(1)

        if (techArtist != null && techTitle != null) {
            artist = techArtist.trim()
            track = techTitle.trim()
            title = "$artist - $track"
            return
        }

        // 2. Pattern: Standard "Artist - Title" but with possible embedded tags
        val parts = fullTitle.split(Regex(" [-–:] | by ", RegexOption.IGNORE_CASE), 2)
        if (parts.size == 2) {
            artist = sanitizeMetadataField(parts[0])
            track = sanitizeMetadataField(parts[1])
            title = if (artist.isNotEmpty()) "$artist - $track" else track
        } else {
            artist = ""
            track = sanitizeMetadataField(fullTitle)
            title = track
        }
    }

    private fun sanitizeMetadataField(input: String): String {
        val tagPattern = Regex("""(?:text|title|artist)="([^"]+)"""", RegexOption.IGNORE_CASE)
        val match = tagPattern.find(input)
        return if (match != null) match.groupValues[1].trim() else input.trim()
    }

    fun hasArtistAndTrack(): Boolean {
        return artist.isNotEmpty() && track.isNotEmpty()
    }
}
