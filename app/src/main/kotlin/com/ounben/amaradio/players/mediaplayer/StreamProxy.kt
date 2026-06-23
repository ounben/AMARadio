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
import kotlinx.coroutines.withContext
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
import kotlin.time.Duration.Companion.seconds

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamProxy(private val httpClient: OkHttpClient, private val uri: String, private val callback: StreamProxyListener) {
    private val tag = "PROXY"
    private val readBuffer = ByteArray(256 * 16)
    @Volatile
    private var localAddress: String? = null
    private var isStopped = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        createProxy()
    }

    private fun createProxy() {
        if (Utils.isDebug) Log.d(tag, "thread started")
        scope.launch {
            try {
                connectToStream()
                if (Utils.isDebug) Log.d(tag, "createProxy() ended")
            } catch (e: Exception) {
                Log.e(tag, "", e)
            }
        }
    }

    private fun proxyDefaultStream(info: ShoutcastInfo?, inputStream: InputStream, outStream: OutputStream) {
        var bytesUntilMetaData = 0
        var streamHasMetaData = false
        if (info != null) {
            callback.onFoundShoutcastStream(info, isHls = false)
            bytesUntilMetaData = info.metadataOffset
            streamHasMetaData = true
        }
        while (!isStopped) {
            if (!streamHasMetaData || (bytesUntilMetaData > 0)) {
                var bytesToRead = readBuffer.size
                if (streamHasMetaData) {
                    bytesToRead = kotlin.math.min(bytesUntilMetaData, bytesToRead)
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
        if (Utils.isDebug) Log.d(tag, "metadata size:$metadataBytes")
        if (metadataBytes > 0) {
            Arrays.fill(readBuffer, 0.toByte())
            while (true) {
                val readBytes = inputStream.read(readBuffer, readBytesBufferMetadata, metadataBytesToRead)
                if (readBytes <= 0) break
                metadataBytesToRead -= readBytes
                readBytesBufferMetadata += readBytes
                if (metadataBytesToRead <= 0) {
                    val s = String(readBuffer, 0, metadataBytes, charset("utf-8"))
                    if (Utils.isDebug) Log.d(tag, "METADATA:$s")
                    val rawMetadata = decodeShoutcastMetadata(s)
                    val streamLiveInfo = StreamLiveInfo(rawMetadata)
                    if (Utils.isDebug) Log.d(tag, "META:${streamLiveInfo.title}")
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
            if (Utils.isDebug) Log.d(tag, "creating local proxy")
            try {
                proxyServer = withContext(Dispatchers.IO) {
                    ServerSocket(0, 1, InetAddress.getLocalHost())
                }
            } catch (_: IOException) {
                return
            }
            val port = proxyServer.localPort
            localAddress = String.format(Locale.US, "http://localhost:%d", port)
            val request = Request.Builder().url(uri)
                .addHeader("Icy-MetaData", "1")
                .build()
            while (!isStopped && (retry > 0)) {
                val currentResponse = withContext(Dispatchers.IO) {
                    try {
                        httpClient.newCall(request).execute()
                    } catch (_: IOException) {
                        null
                    }
                }

                currentResponse?.use { response ->
                    val responseBody = response.body
                    val contentType = responseBody.contentType()
                    if (Utils.isDebug) Log.d(tag, "waiting...")
                    
                    if (isStopped) return@use
                    
                    socketProxy?.close()
                    outputStream?.close()
                    callback.onStreamCreated(localAddress!!)
                    proxyServer.soTimeout = 2000
                    socketProxy = withContext(Dispatchers.IO) {
                        proxyServer.accept()
                    }
                    if (Utils.isDebug) Log.d(tag, "sending OK to the local media player")
                    val out = socketProxy!!.getOutputStream()
                    outputStream = out
                    out.write(
                        ("HTTP/1.0 200 OK\r\n" +
                                "Pragma: no-cache\r\n" +
                                "Content-Type: $contentType\r\n\r\n").toByteArray(charset("utf-8")),
                    )
                    val type = contentType.toString().lowercase(Locale.ROOT)
                    if (Utils.isDebug) Log.d(tag, "Content Type: $type")
                    if (type == "application/vnd.apple.mpegurl" || type == "application/x-mpegurl") {
                        Log.e(tag, "Cannot play HLS streams through proxy!")
                    } else {
                        val info = ShoutcastInfo.Decode(response)
                        proxyDefaultStream(info, responseBody.byteStream(), out)
                    }
                    retry = MAX_RETRIES
                } ?: run {
                    retry--
                }
                
                if (isStopped) break
                delay(1.seconds)
            }
        } catch (e: Exception) {
            Log.e(tag, "exception occurred inside the connection loop, retry. $e")
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    proxyServer?.close()
                    socketProxy?.close()
                    outputStream?.close()
                } catch (e: IOException) {
                    Log.e(tag, "exception occurred while closing resources. $e")
                }
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

    fun stop() {
        if (Utils.isDebug) Log.d(tag, "stopping proxy.")
        isStopped = true
        scope.cancel()
    }

    companion object {
        private const val MAX_RETRIES = 100
    }
}
