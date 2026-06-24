package com.ounben.amaradio.station.live.metadata.lastfm.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LfmTrackMetadata {
    @SerialName("track")
    var track: Track? = null
}
