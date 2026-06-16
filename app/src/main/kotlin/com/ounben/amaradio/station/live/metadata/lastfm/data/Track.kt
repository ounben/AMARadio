package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Track {
    @SerializedName("name")
    var name: String? = null
    @SerializedName("mbid")
    var mbid: String? = null
    @SerializedName("url")
    var url: String? = null
    @SerializedName("duration")
    var duration: String? = null
    @SerializedName("streamable")
    var streamable: Streamable? = null
    @SerializedName("listeners")
    var listeners: String? = null
    @SerializedName("playcount")
    var playcount: String? = null
    @SerializedName("artist")
    var artist: Artist? = null
    @SerializedName("album")
    var album: Album? = null
    @SerializedName("toptags")
    var toptags: Toptags? = null
}
