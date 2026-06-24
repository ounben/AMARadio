package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Image {
    @SerialName("#text")
    var text: String? = null
    @SerialName("size")
    var size: String? = null
}
