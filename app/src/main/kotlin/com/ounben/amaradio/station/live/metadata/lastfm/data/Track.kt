package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Track {
    @SerialName("name")
    var name: String? = null
    @SerialName("mbid")
    var mbid: String? = null
    @SerialName("url")
    var url: String? = null
    @SerialName("duration")
    var duration: String? = null
    @SerialName("streamable")
    var streamable: Streamable? = null
    @SerialName("listeners")
    var listeners: String? = null
    @SerialName("playcount")
    var playcount: String? = null
    @SerialName("artist")
    var artist: Artist? = null
    @SerialName("album")
    var album: Album? = null
    @SerialName("toptags")
    var toptags: Toptags? = null
}
