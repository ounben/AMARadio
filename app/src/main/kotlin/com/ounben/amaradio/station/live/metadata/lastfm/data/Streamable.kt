package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Streamable {
    @SerialName("#text")
    var text: String? = null
    @SerialName("fulltrack")
    var fulltrack: String? = null
}
