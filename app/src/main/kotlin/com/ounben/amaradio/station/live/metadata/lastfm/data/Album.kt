package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Album {
    @SerialName("artist")
    var artist: String? = null
    @SerialName("title")
    var title: String? = null
    @SerialName("mbid")
    var mbid: String? = null
    @SerialName("url")
    var url: String? = null
    @SerialName("image")
    var image: List<Image>? = null
}
