package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Artist {
    @SerialName("name")
    var name: String? = null
    @SerialName("mbid")
    var mbid: String? = null
    @SerialName("url")
    var url: String? = null
}
