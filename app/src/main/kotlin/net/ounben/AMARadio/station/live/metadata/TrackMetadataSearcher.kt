package net.ounben.AMARadio.station.live.metadata

import net.ounben.AMARadio.station.live.metadata.lastfm.LfmMetadataSearcher
import okhttp3.OkHttpClient

class TrackMetadataSearcher(httpClient: OkHttpClient) {
    private val lfmMetadataSearcher = LfmMetadataSearcher(httpClient)

    fun fetchTrackMetadata(lastFMApiKey: String?, artist: String?, track: String, trackMetadataCallback: TrackMetadataCallback) {
        lfmMetadataSearcher.fetchTrackMetadata(lastFMApiKey, artist, track, trackMetadataCallback)
    }
}
