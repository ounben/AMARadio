package com.ounben.amaradio.station.live.metadata

class TrackMetadata {
    enum class AlbumArtSize {
        SMALL,
        MEDIUM,
        LARGE,
        EXTRA_LARGE
    }

    class AlbumArt(var size: AlbumArtSize, var url: String)

    var artist: String? = null
    var album: String? = null
    var track: String? = null
    var tags: ArrayList<String>? = null
    var albumArts: List<AlbumArt>? = null
}
