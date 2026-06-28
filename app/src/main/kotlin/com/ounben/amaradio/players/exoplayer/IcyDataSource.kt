package com.ounben.amaradio.players.exoplayer

import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.HashMap

@UnstableApi
class IcyDataSource(
    private val httpClient: OkHttpClient,
    private val transferListener: TransferListener,
    private val dataSourceListener: IcyDataSourceListener
) : HttpDataSource {

    interface IcyDataSourceListener {
        fun onDataSourceConnected()
        fun onDataSourceConnectionLost()
        fun onDataSourceConnectionLostIrrecoverably()
        fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?)
        fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo)
        fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int)
    }

    private var dataSpec: DataSpec? = null
    private var responseBody: ResponseBody? = null
    private var responseHeaders: Map<String, List<String>> = HashMap()
    private var opened = false
    private var shoutcastInfo: ShoutcastInfo? = null

    private var bytesUntilMetadata = Int.MAX_VALUE
    private var metadataBytesToRead = 0
    private val metadataBuffer = ByteArray(4096)
    private var metadataBufferPos = 0

    override fun open(dataSpec: DataSpec): Long {
        close()
        this.dataSpec = dataSpec
        val allowGzip = (dataSpec.flags and DataSpec.FLAG_ALLOW_GZIP) != 0
        val url = dataSpec.uri.toString().toHttpUrlOrNull()
            ?: throw HttpDataSource.HttpDataSourceException("Invalid URL", dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        
        val request = Request.Builder().url(url)
            .addHeader("Icy-MetaData", "1")
            .apply { if (!allowGzip) addHeader("Accept-Encoding", "identity") }
            .build()
        
        return connect(request)
    }

    private fun connect(request: Request): Long {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException(e, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }

        if (!response.isSuccessful) {
            val code = response.code
            val headers = response.headers.toMultimap()
            response.close()
            throw HttpDataSource.InvalidResponseCodeException(code, null, null, headers, dataSpec!!, ByteArray(0))
        }

        responseBody = response.body
        responseHeaders = response.headers.toMultimap()
        
        opened = true
        dataSourceListener.onDataSourceConnected()
        transferListener.onTransferStart(this, dataSpec!!, true)

        shoutcastInfo = ShoutcastInfo.Decode(response)
        dataSourceListener.onDataSourceShoutcastInfo(shoutcastInfo)
        
        val metaInt = shoutcastInfo?.metadataOffset ?: 0
        bytesUntilMetadata = if (metaInt > 0) metaInt else Int.MAX_VALUE
        metadataBytesToRead = 0
        
        return responseBody?.contentLength() ?: -1L
    }

    override fun close() {
        if (opened) {
            opened = false
            dataSpec?.let { transferListener.onTransferEnd(this, it, true) }
        }
        responseBody?.closeQuietly()
        responseBody = null
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!opened) return -1
        return try {
            val bytesRead = readAudioOnly(buffer, offset, readLength)
            if (bytesRead > 0) {
                dataSpec?.let { transferListener.onBytesTransferred(this, it, true, bytesRead) }
                dataSourceListener.onDataSourceBytesRead(buffer, offset, bytesRead)
            }
            bytesRead
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException(e, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSource.HttpDataSourceException.TYPE_READ)
        } catch (e: Exception) {
            throw HttpDataSource.HttpDataSourceException(e.message ?: "Read error", dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSource.HttpDataSourceException.TYPE_READ)
        }
    }

    private fun readAudioOnly(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val stream = responseBody?.byteStream() ?: return -1
        var totalAudioRead = 0
        
        while (totalAudioRead < readLength) {
            if (metadataBytesToRead > 0) {
                val toRead = Math.min(metadataBytesToRead - metadataBufferPos, metadataBytesToRead)
                val read = stream.read(metadataBuffer, metadataBufferPos, toRead)
                if (read == -1) return if (totalAudioRead > 0) totalAudioRead else -1
                metadataBufferPos += read
                if (metadataBufferPos == metadataBytesToRead) {
                    parseMetadata(metadataBuffer, metadataBytesToRead)
                    metadataBytesToRead = 0
                    bytesUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
                }
            } else if (bytesUntilMetadata == 0) {
                val lengthByte = stream.read()
                if (lengthByte == -1) return if (totalAudioRead > 0) totalAudioRead else -1
                val length = lengthByte * 16
                if (length > 0) {
                    metadataBytesToRead = length
                    metadataBufferPos = 0
                } else {
                    bytesUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
                }
            } else {
                val toRead = Math.min(readLength - totalAudioRead, bytesUntilMetadata)
                val read = stream.read(buffer, offset + totalAudioRead, toRead)
                if (read == -1) return if (totalAudioRead > 0) totalAudioRead else -1
                totalAudioRead += read
                if (bytesUntilMetadata != Int.MAX_VALUE) bytesUntilMetadata -= read
            }
            if (totalAudioRead > 0 && (bytesUntilMetadata == 0 || metadataBytesToRead > 0)) break
        }
        return totalAudioRead
    }

    private fun parseMetadata(buffer: ByteArray, length: Int) {
        try {
            val metadataString = String(buffer, 0, length, Charsets.UTF_8).trim()
            if (metadataString.isEmpty()) return
            val metadataMap = HashMap<String, String>()
            metadataString.split(";").forEach { pair ->
                val keyValue = pair.split("='", limit = 2)
                if (keyValue.size == 2) {
                    metadataMap[keyValue[0]] = keyValue[1].removeSuffix("'")
                }
            }
            if (metadataMap.isNotEmpty()) dataSourceListener.onDataSourceStreamLiveInfo(StreamLiveInfo(metadataMap))
        } catch (e: Exception) { Log.e("IcyDataSource", "Metadata parse error", e) }
    }

    override fun getUri(): Uri? = dataSpec?.uri
    override fun setRequestProperty(name: String, value: String) {}
    override fun clearRequestProperty(name: String) {}
    override fun clearAllRequestProperties() {}
    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders
    override fun getResponseCode(): Int = 0
    override fun addTransferListener(transferListener: TransferListener) {}
}
