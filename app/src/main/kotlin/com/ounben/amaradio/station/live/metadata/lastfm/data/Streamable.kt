package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Streamable {
    @SerializedName("#text")
    var text: String? = null
    @SerializedName("fulltrack")
    var fulltrack: String? = null
}
