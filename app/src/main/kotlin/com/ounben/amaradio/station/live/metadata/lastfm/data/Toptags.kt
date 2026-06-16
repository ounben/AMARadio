package com.ounben.amaradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.SerializedName

class Toptags {
    @SerializedName("tag")
    var tag: List<Tag>? = null
}
