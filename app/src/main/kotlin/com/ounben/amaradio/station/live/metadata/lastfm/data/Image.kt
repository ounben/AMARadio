package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Image {
    @SerializedName("#text")
    var text: String? = null
    @SerializedName("size")
    var size: String? = null
}
