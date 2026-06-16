package com.ounben.amaradio.station.live

import android.os.Parcelable
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import kotlinx.parcelize.Parcelize
import com.ounben.amaradio.Utils.parseIntWithDefault
import okhttp3.Response

@UnstableApi
@Parcelize
class ShoutcastInfo(
    @JvmField var metadataOffset: Int = 0,
    @JvmField var bitrate: Int = 0,
    @JvmField var audioInfo: String? = null,
    @JvmField var audioDesc: String? = null,
    @JvmField var audioGenre: String? = null,
    @JvmField var audioName: String? = null,
    @JvmField var audioHomePage: String? = null,
    @JvmField var serverName: String? = null,
    @JvmField var serverPublic: Boolean = false,
    @JvmField var channels: Int = 0,
    @JvmField var sampleRate: Int = 0
) : Parcelable {

    constructor(icyHeaders: IcyHeaders) : this(
        bitrate = icyHeaders.bitrate,
        audioGenre = icyHeaders.genre,
        serverPublic = icyHeaders.isPublic,
        audioName = icyHeaders.name,
        audioHomePage = icyHeaders.url
    )

    companion object {
        @JvmStatic
        fun Decode(response: Response): ShoutcastInfo? {
            val info = ShoutcastInfo()
            info.metadataOffset = parseIntWithDefault(response.header("icy-metaint"), 0)
            info.bitrate = parseIntWithDefault(response.header("icy-br"), 0)
            info.audioInfo = response.header("ice-audio-info")
            info.audioDesc = response.header("icy-description")
            info.audioGenre = response.header("icy-genre")
            info.audioName = response.header("icy-name")
            info.audioHomePage = response.header("icy-url")
            info.serverName = response.header("Server")
            info.serverPublic = parseIntWithDefault(response.header("icy-pub"), 0) > 0

            info.audioInfo?.let {
                val audioInfoParams = splitAudioInfo(it)
                info.channels = parseIntWithDefault(audioInfoParams["ice-channels"], 0)
                if (info.channels == 0) {
                    info.channels = parseIntWithDefault(audioInfoParams["channels"], 0)
                }

                info.sampleRate = parseIntWithDefault(audioInfoParams["ice-samplerate"], 0)
                if (info.sampleRate == 0) {
                    info.sampleRate = parseIntWithDefault(audioInfoParams["samplerate"], 0)
                }

                if (info.bitrate == 0) {
                    info.bitrate = parseIntWithDefault(audioInfoParams["ice-bitrate"], 0)
                    if (info.bitrate == 0) {
                        info.bitrate = parseIntWithDefault(audioInfoParams["bitrate"], 0)
                    }
                }
            }

            return if (info.metadataOffset == 0) null else info
        }

        private fun splitAudioInfo(audioInfo: String): Map<String, String> {
            val params = LinkedHashMap<String, String>()
            val pairs = audioInfo.split(";").toTypedArray()
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                if (idx != -1) {
                    params[pair.substring(0, idx)] = pair.substring(idx + 1)
                }
            }
            return params
        }
    }
}
