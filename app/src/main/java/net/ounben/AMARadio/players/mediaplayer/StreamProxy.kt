package net.ounben.AMARadio.players.mediaplayer

import android.util.Log
import kotlinx.coroutines.*
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.recording.Recordable
import net.ounben.AMARadio.recording.RecordableListener
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamProxy(private val httpClient: OkHttpClient, private val uri: String, private val callback: StreamProxyListener) : Recordable {
    private val TAG = "PROXY"
    private var recordableListener: RecordableListener? = null
    private val readBuffer = ByteArray(256 * 16)
    @Volatile
    private var localAddress: String? = null
    private var isStopped = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        createProxy()
    }

    private fun createProxy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "thread started")
        scope.launch {
            try {
                connectToStream()
                if (BuildConfig.DEBUG) Log.d(TAG, "createProxy() ended")
            } catch (e: Exception) {
                Log.e(TAG, "", e)
            }
        }
    }

    private suspend fun proxyDefaultStream(info: ShoutcastInfo?, inputStream: InputStream, outStream: OutputStream) {
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
                recordableListener?.onBytesAvailable(readBuffer, 0, readBytes)
                callback.onBytesRead(readBuffer, 0, readBytes)
            } else {
                readMetaData(inputStream)
                bytesUntilMetaData = info!!.metadataOffset
            }
        }
        stopRecording()
    }

    private fun readMetaData(inputStream: InputStream): Int {
        val metadataBytes = inputStream.read() * 16
        var metadataBytesToRead = metadataBytes
        var readBytesBufferMetadata = 0
        if (BuildConfig.DEBUG) Log.d(TAG, "metadata size:$metadataBytes")
        if (metadataBytes > 0) {
            Arrays.fill(readBuffer, 0.toByte())
            while (true) {
                val readBytes = inputStream.read(readBuffer, readBytesBufferMetadata, metadataBytesToRead)
                if (readBytes <= 0) break
                metadataBytesToRead -= readBytes
                readBytesBufferMetadata += readBytes
                if (metadataBytesToRead <= 0) {
                    val s = String(readBuffer, 0, metadataBytes, charset("utf-8"))
                    if (BuildConfig.DEBUG) Log.d(TAG, "METADATA:$s")
                    val rawMetadata = decodeShoutcastMetadata(s)
                    val streamLiveInfo = StreamLiveInfo(rawMetadata)
                    if (BuildConfig.DEBUG) Log.d(TAG, "META:${streamLiveInfo.title}")
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
            if (BuildConfig.DEBUG) Log.d(TAG, "creating local proxy")
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
                    if (BuildConfig.DEBUG) Log.d(TAG, "waiting...")
                    if (isStopped) return@use
                    socketProxy?.close()
                    outputStream?.close()
                    callback.onStreamCreated(localAddress!!)
                    proxyServer.soTimeout = 2000
                    socketProxy = proxyServer.accept()
                    if (BuildConfig.DEBUG) Log.d(TAG, "sending OK to the local media player")
                    outputStream = socketProxy!!.getOutputStream()
                    outputStream!!.write(("HTTP/1.0 200 OK\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Content-Type: $contentType\r\n\r\n").toByteArray(charset("utf-8")))
                    val type = contentType.toString().lowercase(Locale.ROOT)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Content Type: $type")
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
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted ex Proxy() $e")
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
        if (BuildConfig.DEBUG) Log.d(TAG, "stopping proxy.")
        isStopped = true
        stopRecording()
        scope.cancel()
    }

    override fun canRecord(): Boolean = true

    override fun startRecording(recordableListener: RecordableListener) {
        this.recordableListener = recordableListener
    }

    override fun stopRecording() {
        recordableListener?.let {
            it.onRecordingEnded()
            recordableListener = null
        }
    }

    override fun isRecording(): Boolean = recordableListener != null

    override fun getRecordNameFormattingArgs(): Map<String, String>? = null

    override fun getExtension(): String = "mp3"

    companion object {
        private const val MAX_RETRIES = 100
    }
}
