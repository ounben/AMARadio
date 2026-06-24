package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Toptags {
    @SerialName("tag")
    var tag: List<Tag>? = null
}
