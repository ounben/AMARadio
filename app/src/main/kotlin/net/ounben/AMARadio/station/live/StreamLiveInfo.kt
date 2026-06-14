package net.ounben.AMARadio.station.live

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
                title = it["StreamTitle"] ?: ""
                if (!TextUtils.isEmpty(title)) {
                    val artistAndTrack = title.split(" - ".toRegex(), 2).toTypedArray()
                    artist = artistAndTrack[0]
                    track = if (artistAndTrack.size == 2) artistAndTrack[1] else ""
                }
            }
        }
    }

    fun hasArtistAndTrack(): Boolean {
        return artist.isNotEmpty() && track.isNotEmpty()
    }
}
