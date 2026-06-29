package com.ounben.amaradio.station.live

import android.os.Parcelable
import android.text.TextUtils
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
        rawMetadata?.let {
            if (it.containsKey("StreamTitle")) {
                val fullTitle = it["StreamTitle"] ?: ""
                title = fullTitle
                if (fullTitle.isNotEmpty()) {
                    // Split by common separators: " - ", " – ", " : ", " by "
                    val parts = fullTitle.split(Regex(" [-–:] | by ", RegexOption.IGNORE_CASE), 2)
                    if (parts.size == 2) {
                        artist = parts[0].trim()
                        track = parts[1].trim()
                    } else {
                        artist = "" // Clear fallback to avoid duplication
                        track = fullTitle.trim()
                    }
                }
            }
        }
    }

    fun hasArtistAndTrack(): Boolean {
        return artist.isNotEmpty() && track.isNotEmpty()
    }
}
