package com.ounben.amaradio.players.mediaplayer

import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo

interface StreamProxyListener {
    fun onFoundShoutcastStream(shoutcastInfo: ShoutcastInfo, isHls: Boolean)
    fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo)
    fun onStreamCreated(proxyConnection: String)
    fun onStreamStopped()
    fun onBytesRead(buffer: ByteArray, offset: Int, length: Int)
}
