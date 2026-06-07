package net.ounben.AMARadio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Tag {
    @SerializedName("name")
    var name: String? = null
    @SerializedName("url")
    var url: String? = null
}
