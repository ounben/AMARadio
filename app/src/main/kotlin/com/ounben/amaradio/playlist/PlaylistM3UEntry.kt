package com.ounben.amaradio.playlist

import android.util.Log
import com.ounben.amaradio.BuildConfig

class PlaylistM3UEntry {
    var header: String? = null
    var content: String? = null
    var length: Int = -1
    var title: String? = null
    var bitrate: Int = -1
    var programId: Int = -1
    var isStreamInformation: Boolean = false

    constructor(header: String?, content: String?) {
        this.header = header
        this.content = content
        decode()
    }

    constructor(content: String?) {
        this.header = null
        this.content = content
    }

    private fun decode() {
        val h = header ?: return
        if (h.startsWith(EXTINF)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "found EXTINF:$h")
            val attributes = h.substring(EXTINF.length)
            val sep = attributes.indexOf(",")
            if (sep != -1) {
                val timeStr = attributes.substring(0, sep).trim()
                length = timeStr.toIntOrNull() ?: -1
                title = attributes.substring(sep + 1)
            }
        } else if (h.startsWith(STREAMINF)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "found STREAMINFO:$h")
            isStreamInformation = true
            val attributes = h.substring(STREAMINF.length)
            val attributesList = attributes.split(",")
            for (attr in attributesList) {
                val trimmedAttr = attr.trim()
                if (trimmedAttr.startsWith(STREAMINF_BANDWIDTH)) {
                    val paramStr = trimmedAttr.substring(STREAMINF_BANDWIDTH.length)
                    bitrate = paramStr.toIntOrNull() ?: -1
                }
                if (trimmedAttr.startsWith(STREAMINF_PROGRAM)) {
                    val paramStr = trimmedAttr.substring(STREAMINF_PROGRAM.length)
                    programId = paramStr.toIntOrNull() ?: -1
                }
            }
        }
    }

    companion object {
        const val EXTINF = "#EXTINF:"
        const val STREAMINF = "#EXT-X-STREAM-INF:"
        const val STREAMINF_PROGRAM = "PROGRAM-ID="
        const val STREAMINF_BANDWIDTH = "BANDWIDTH="
        const val STREAMINF_CODECS = "CODECS="
        private const val TAG = "M3U"
    }
}
