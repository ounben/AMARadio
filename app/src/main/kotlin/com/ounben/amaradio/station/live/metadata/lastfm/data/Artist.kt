package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Artist {
    @SerializedName("name")
    var name: String? = null
    @SerializedName("mbid")
    var mbid: String? = null
    @SerializedName("url")
    var url: String? = null
}
