package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Album {
    @SerializedName("artist")
    var artist: String? = null
    @SerializedName("title")
    var title: String? = null
    @SerializedName("mbid")
    var mbid: String? = null
    @SerializedName("url")
    var url: String? = null
    @SerializedName("image")
    var image: List<Image>? = null
}
