package com.ounben.amaradio.players.exoplayer

import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.TransferListener
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.Call
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
    private val dataSourceListener: IcyDataSourceListener,
    private val isHls: Boolean
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
    private var byteStream: java.io.InputStream? = null
    private var responseHeaders: Map<String, List<String>> = HashMap()
    
    @Volatile
    private var opened = false
    
    private var activeCall: Call? = null
    private var shoutcastInfo: ShoutcastInfo? = null

    private var lastMetadataString: String? = null
    private var bytesUntilMetadata = Int.MAX_VALUE
    private var metadataBytesToRead = 0
    private var metadataBuffer = ByteArray(4096)
    private var metadataBufferPos = 0
    private var responseCode = 0

    private val requestProperties = HashMap<String, String>()

    override fun open(dataSpec: DataSpec): Long {
        close()
        this.dataSpec = dataSpec
        val url = dataSpec.uri.toString().toHttpUrlOrNull()
            ?: throw HttpDataSourceException("Invalid URL", dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_OPEN)
        
        val builder = Request.Builder().url(url)
        builder.header("User-Agent", "AMARadio")

        for ((key, value) in dataSpec.httpRequestHeaders) {
            builder.header(key, value)
        }
        
        if (!isHls && !dataSpec.httpRequestHeaders.containsKey("Icy-MetaData")) {
            builder.addHeader("Icy-MetaData", "1")
        }
        
        synchronized(requestProperties) {
            for ((key, value) in requestProperties) {
                builder.header(key, value)
            }
        }
        
        val request = builder.build()
        return connect(request)
    }

    private fun connect(request: Request): Long {
        val call = httpClient.newCall(request)
        activeCall = call
        
        val response = try {
            call.execute()
        } catch (e: IOException) {
            activeCall = null
            if (!opened) {
                // If we cancelled it ourselves, return a generic cancelled exception
                throw HttpDataSourceException("Connection cancelled", dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_OPEN)
            }
            throw HttpDataSourceException(e, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_OPEN)
        }

        responseCode = response.code
        if (!response.isSuccessful) {
            val code = response.code
            val headers = response.headers.toMultimap()
            response.close()
            activeCall = null
            throw HttpDataSource.InvalidResponseCodeException(code, null, null, headers, dataSpec!!, ByteArray(0))
        }

        responseBody = response.body
        byteStream = responseBody?.byteStream()
        responseHeaders = response.headers.toMultimap()
        
        opened = true
        dataSourceListener.onDataSourceConnected()
        transferListener.onTransferStart(this, dataSpec!!, true)

        if (!isHls) {
            shoutcastInfo = ShoutcastInfo.Decode(response)
            dataSourceListener.onDataSourceShoutcastInfo(shoutcastInfo)
            
            val metaInt = shoutcastInfo?.metadataOffset ?: 0
            bytesUntilMetadata = if (metaInt > 0) metaInt else Int.MAX_VALUE
        } else {
            bytesUntilMetadata = Int.MAX_VALUE
        }
        
        metadataBytesToRead = 0
        
        return responseBody?.contentLength() ?: -1L
    }

    override fun close() {
        opened = false
        
        // CRITICAL FIX: Cancel the running OkHttp call to immediately drop the socket.
        activeCall?.cancel()
        activeCall = null

        if (opened) { // Logic check: only if it was actually opened
             dataSpec?.let { transferListener.onTransferEnd(this, it, true) }
        }
        
        // Re-read dataSpec to ensure listener is notified even if we manually cleared opened
        dataSpec?.let { 
             try { transferListener.onTransferEnd(this, it, true) } catch(_: Exception) {}
        }

        byteStream?.closeQuietly()
        byteStream = null
        responseBody?.closeQuietly()
        responseBody = null
        lastMetadataString = null
        dataSpec = null
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!opened) return -1
        if (readLength == 0) return 0
        
        try {
            val stream = byteStream ?: return -1
            
            // CRITICAL FIX: Check 'opened' flag in every loop iteration to break immediately on pause
            while (opened) {
                if (metadataBytesToRead > 0) {
                    val toRead = metadataBytesToRead - metadataBufferPos
                    val read = stream.read(metadataBuffer, metadataBufferPos, toRead)
                    
                    if (!opened) return -1 // Check after blocking read
                    if (read == -1) return -1
                    
                    metadataBufferPos += read
                    if (metadataBufferPos == metadataBytesToRead) {
                        parseMetadata(metadataBuffer, metadataBytesToRead)
                        metadataBytesToRead = 0
                        bytesUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
                    }
                    if (read == 0) return 0 
                    continue
                }

                if (bytesUntilMetadata == 0) {
                    val lengthByte = stream.read()
                    
                    if (!opened) return -1 // Check after blocking read
                    if (lengthByte == -1) return -1
                    
                    val length = lengthByte * 16
                    if (length > 0) {
                        metadataBytesToRead = length
                        metadataBufferPos = 0
                        if (metadataBytesToRead > metadataBuffer.size) {
                            metadataBuffer = ByteArray(metadataBytesToRead)
                        }
                    } else {
                        bytesUntilMetadata = shoutcastInfo?.metadataOffset ?: Int.MAX_VALUE
                    }
                    continue
                }

                val toRead = Math.min(readLength, bytesUntilMetadata)
                val bytesRead = stream.read(buffer, offset, toRead)
                
                if (!opened) return -1 // Check after blocking read
                if (bytesRead == -1) return -1
                
                if (bytesUntilMetadata != Int.MAX_VALUE) {
                    bytesUntilMetadata -= bytesRead
                }
                
                if (bytesRead > 0) {
                    dataSpec?.let { transferListener.onBytesTransferred(this, it, true, bytesRead) }
                    dataSourceListener.onDataSourceBytesRead(buffer, offset, bytesRead)
                    return bytesRead
                }
                
                if (bytesRead == 0) return 0
            }
            return -1
        } catch (e: IOException) {
            if (!opened) return -1
            throw HttpDataSourceException(e, dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_READ)
        } catch (e: Exception) {
            if (!opened) return -1
            throw HttpDataSourceException(e.message ?: "Read error", dataSpec!!, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, HttpDataSourceException.TYPE_READ)
        }
    }

    private fun parseMetadata(buffer: ByteArray, length: Int) {
        try {
            val metadataString = String(buffer, 0, length, Charsets.UTF_8).trim()
            if (metadataString.isEmpty() || metadataString == lastMetadataString) return
            lastMetadataString = metadataString
            
            val metadataMap = HashMap<String, String>()
            val regex = Regex("(\\w+)='([^']*)'")
            val matches = regex.findAll(metadataString)
            for (match in matches) {
                if (match.groupValues.size == 3) {
                    metadataMap[match.groupValues[1]] = match.groupValues[2]
                }
            }
            
            if (metadataMap.isNotEmpty()) {
                dataSourceListener.onDataSourceStreamLiveInfo(StreamLiveInfo(metadataMap))
            }
        } catch (e: Exception) { 
            Log.e("IcyDataSource", "Metadata parse error: ${e.message}") 
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun setRequestProperty(name: String, value: String) {
        synchronized(requestProperties) {
            requestProperties[name] = value
        }
    }

    override fun clearRequestProperty(name: String) {
        synchronized(requestProperties) {
            requestProperties.remove(name)
        }
    }

    override fun clearAllRequestProperties() {
        synchronized(requestProperties) {
            requestProperties.clear()
        }
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return responseHeaders.filterKeys { !it.startsWith("icy-", ignoreCase = true) }
    }

    override fun getResponseCode(): Int = responseCode

    override fun addTransferListener(transferListener: TransferListener) {}
}
