package com.ounben.amaradio.players.mediaplayer

import android.util.Log
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Arrays
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamProxy(private val httpClient: OkHttpClient, private val uri: String, private val callback: StreamProxyListener) {
    private val TAG = "PROXY"
    private val readBuffer = ByteArray(256 * 16)
    @Volatile
    private var localAddress: String? = null
    private var isStopped = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        createProxy()
    }

    private fun createProxy() {
        if (Utils.isDebug) Log.d(TAG, "thread started")
        scope.launch {
            try {
                connectToStream()
                if (Utils.isDebug) Log.d(TAG, "createProxy() ended")
            } catch (e: Exception) {
                Log.e(TAG, "", e)
            }
        }
    }

    private fun proxyDefaultStream(info: ShoutcastInfo?, inputStream: InputStream, outStream: OutputStream) {
        var bytesUntilMetaData = 0
        var streamHasMetaData = false
        if (info != null) {
            callback.onFoundShoutcastStream(info, false)
            bytesUntilMetaData = info.metadataOffset
            streamHasMetaData = true
        }
        while (!isStopped) {
            if (!streamHasMetaData || bytesUntilMetaData > 0) {
                var bytesToRead = readBuffer.size
                if (streamHasMetaData) {
                    bytesToRead = Math.min(bytesUntilMetaData, bytesToRead)
                }
                val readBytes = inputStream.read(readBuffer, 0, bytesToRead)
                if (readBytes <= 0) break
                if (streamHasMetaData) {
                    bytesUntilMetaData -= readBytes
                }
                outStream.write(readBuffer, 0, readBytes)
                callback.onBytesRead(readBuffer, 0, readBytes)
            } else {
                readMetaData(inputStream)
                bytesUntilMetaData = info!!.metadataOffset
            }
        }
    }

    private fun readMetaData(inputStream: InputStream): Int {
        val metadataBytes = inputStream.read() * 16
        var metadataBytesToRead = metadataBytes
        var readBytesBufferMetadata = 0
        if (Utils.isDebug) Log.d(TAG, "metadata size:$metadataBytes")
        if (metadataBytes > 0) {
            Arrays.fill(readBuffer, 0.toByte())
            while (true) {
                val readBytes = inputStream.read(readBuffer, readBytesBufferMetadata, metadataBytesToRead)
                if (readBytes <= 0) break
                metadataBytesToRead -= readBytes
                readBytesBufferMetadata += readBytes
                if (metadataBytesToRead <= 0) {
                    val s = String(readBuffer, 0, metadataBytes, charset("utf-8"))
                    if (Utils.isDebug) Log.d(TAG, "METADATA:$s")
                    val rawMetadata = decodeShoutcastMetadata(s)
                    val streamLiveInfo = StreamLiveInfo(rawMetadata)
                    if (Utils.isDebug) Log.d(TAG, "META:${streamLiveInfo.title}")
                    callback.onFoundLiveStreamInfo(streamLiveInfo)
                    break
                }
            }
        }
        return readBytesBufferMetadata + 1
    }

    private suspend fun connectToStream() {
        isStopped = false
        var retry = MAX_RETRIES
        var socketProxy: Socket? = null
        var outputStream: OutputStream? = null
        var proxyServer: ServerSocket? = null
        try {
            if (Utils.isDebug) Log.d(TAG, "creating local proxy")
            try {
                proxyServer = ServerSocket(0, 1, InetAddress.getLocalHost())
            } catch (e: IOException) {
                e.printStackTrace()
                return
            }
            val port = proxyServer.localPort
            localAddress = String.format(Locale.US, "http://localhost:%d", port)
            val request = Request.Builder().url(uri)
                .addHeader("Icy-MetaData", "1")
                .build()
            while (!isStopped && retry > 0) {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body
                    if (responseBody == null) {
                        retry--
                        return@use
                    }
                    val contentType = responseBody.contentType()
                    if (Utils.isDebug) Log.d(TAG, "waiting...")
                    if (isStopped) return@use
                    socketProxy?.close()
                    outputStream?.close()
                    callback.onStreamCreated(localAddress!!)
                    proxyServer.soTimeout = 2000
                    socketProxy = proxyServer.accept()
                    if (Utils.isDebug) Log.d(TAG, "sending OK to the local media player")
                    outputStream = socketProxy!!.getOutputStream()
                    outputStream!!.write(("HTTP/1.0 200 OK\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Content-Type: $contentType\r\n\r\n").toByteArray(charset("utf-8")))
                    val type = contentType.toString().lowercase(Locale.ROOT)
                    if (Utils.isDebug) Log.d(TAG, "Content Type: $type")
                    if (type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl") {
                        Log.e(TAG, "Cannot play HLS streams through proxy!")
                    } else {
                        val info = ShoutcastInfo.Decode(response)
                        proxyDefaultStream(info, responseBody.byteStream(), outputStream!!)
                    }
                    retry = MAX_RETRIES
                }
                if (isStopped) break
                retry--
                delay(1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "exception occurred inside the connection loop, retry. $e")
        } finally {
            try {
                proxyServer?.close()
                socketProxy?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "exception occurred while closing resources. $e")
            }
        }
        if (!isStopped) {
            callback.onStreamStopped()
        }
        stop()
    }

    private fun decodeShoutcastMetadata(metadataStr: String): Map<String, String> {
        val metadata = HashMap<String, String>()
        val kvs = metadataStr.split(";").toTypedArray()
        for (kv in kvs) {
            val n = kv.indexOf('=')
            if (n < 1) continue
            val isString = n + 1 < kv.length && kv[kv.length - 1] == '\'' && kv[n + 1] == '\''
            val key = kv.substring(0, n)
            val `val` = if (isString) kv.substring(n + 2, kv.length - 1) else if (n + 1 < kv.length) kv.substring(n + 1) else ""
            metadata[key] = `val`
        }
        return metadata
    }

    fun getLocalAddress(): String? = localAddress

    fun stop() {
        if (Utils.isDebug) Log.d(TAG, "stopping proxy.")
        isStopped = true
        scope.cancel()
    }

    companion object {
        private const val MAX_RETRIES = 100
    }
}
