package net.ounben.AMARadio.players.mediaplayer

import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo

interface StreamProxyListener {
    fun onFoundShoutcastStream(bitrate: ShoutcastInfo, isHls: Boolean)
    fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo)
    fun onStreamCreated(proxyConnection: String)
    fun onStreamStopped()
    fun onBytesRead(buffer: ByteArray, offset: Int, length: Int)
}
