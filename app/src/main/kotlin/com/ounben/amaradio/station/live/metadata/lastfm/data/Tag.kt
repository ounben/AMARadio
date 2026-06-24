package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Tag {
    @SerialName("name")
    var name: String? = null
    @SerialName("url")
    var url: String? = null
}
