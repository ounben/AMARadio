package com.ounben.amaradio.recorder

import android.content.Context
import android.util.Log
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.RecordEntity
import com.ounben.amaradio.database.user.RecordTrackEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton RecordManager. Runs an independent background recording stream via OkHttp + RecordIcyStream + MediaWriter.
 * Completely decoupled from player playback (e.g. user can play SRF 1 while recording 1.FM).
 */
class RecordManager private constructor(private val context: Context) {

    data class RecordingState(
        val isRecording: Boolean = false,
        val stationName: String = "",
        val stationUuid: String = "",
        val currentTrackTitle: String = "",
        val startTimeMs: Long = 0L,
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val formatExtension: String = "mp3",
        val error: String? = null
    )

    private class RecordingSession(
        val id: Long,
        val station: DataRadioStation,
        val startTimeMs: Long
    ) {
        @Volatile var isActive: Boolean = true
        @Volatile var initialFormatDetected: Boolean = false
        @Volatile var mediaWriter: MediaWriter? = null
        @Volatile var activeDecoupledRecorder: ThreadDecoupledRecorder? = null
        @Volatile var httpClient: okhttp3.OkHttpClient? = null
        @Volatile var recordingThread: Thread? = null
        @Volatile var autoStopJob: Job? = null
        val trackChapters: MutableList<Pair<String, Long>> = Collections.synchronizedList(mutableListOf())
    }

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val sessionLock = Any()
    private val sessionIdGenerator = AtomicLong(0L)
    @Volatile
    private var activeSession: RecordingSession? = null

    fun startRecording(station: DataRadioStation, timerDurationMs: Long = 0L) {
        synchronized(sessionLock) {
            if (activeSession != null) return
            startSessionLocked(station, timerDurationMs)
        }
    }

    /**
     * Stops and saves any currently active recording (if one is running) and immediately starts
     * recording [station] without tearing down the foreground service in between.
     */
    fun replaceRecording(station: DataRadioStation, timerDurationMs: Long = 0L) {
        val previousSession = synchronized(sessionLock) {
            val detached = detachActiveSessionLocked(stopForegroundService = false)
            startSessionLocked(station, timerDurationMs)
            detached
        }
        if (previousSession != null) {
            finalizeDetachedSessionAsync(previousSession)
        }
    }

    private fun startSessionLocked(station: DataRadioStation, timerDurationMs: Long) {
        val session = RecordingSession(
            id = sessionIdGenerator.incrementAndGet(),
            station = station,
            startTimeMs = System.currentTimeMillis()
        )
        activeSession = session

        _recordingState.value = RecordingState(
            isRecording = true,
            stationName = station.Name,
            stationUuid = station.StationUuid,
            startTimeMs = session.startTimeMs
        )

        RecordService.startService(context, station.Name)

        // Schedule auto-stop timer if duration is specified (> 0)
        if (timerDurationMs > 0) {
            session.autoStopJob = CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(timerDurationMs)
                if (session.isActive) {
                    Log.d("RecordManager", "Auto-stop recording timer expired for ${station.Name}")
                    stopSession(session)
                }
            }
        }

        // Start independent background recording stream on dedicated MAX_PRIORITY thread
        session.recordingThread = Thread({
            startIndependentRecordingStream(session)
        }, "AMARadio-Recorder-Thread-${session.id}").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private inline fun updateStateIfActive(
        session: RecordingSession,
        transform: (RecordingState) -> RecordingState
    ) {
        synchronized(sessionLock) {
            if (activeSession === session && session.isActive) {
                _recordingState.value = transform(_recordingState.value)
            }
        }
    }

    private fun startIndependentRecordingStream(session: RecordingSession) {
        val station = session.station
        val isolatedClient = okhttp3.OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(2, 5, TimeUnit.MINUTES))
            .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 2 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        session.httpClient = isolatedClient

        val userAgent = com.ounben.amaradio.Utils.getUserAgent(context)

        if (station.StreamUrl.lowercase().contains(".m3u8") || station.Hls == 1) {
            recordViaHls(session, isolatedClient, station.StreamUrl, userAgent)
            return
        }

        var currentStreamUrl = station.StreamUrl
        var reconnectAttempts = 0

        while (session.isActive) {
            val request = okhttp3.Request.Builder()
                .url(currentStreamUrl)
                .header("User-Agent", userAgent)
                .build()

            var detectedHlsUrl: String? = null

            try {
                isolatedClient.newCall(request).execute().use { response ->
                    if (!session.isActive) return
                    if (!response.isSuccessful) {
                        Log.w("RecordManager", "Recording connection attempt returned HTTP ${response.code}")
                        reconnectAttempts++
                        if (reconnectAttempts > 5) {
                            stopSession(session)
                            return
                        }
                        try { Thread.sleep(2000) } catch (_: Exception) {}
                        return@use
                    }

                    reconnectAttempts = 0
                    val finalUrl = response.request.url.toString()
                    val contentType = response.header("Content-Type")
                    Log.i("RecordManager", "Connected to ${station.Name}. HTTP ${response.code}, Content-Type: $contentType, URL: $finalUrl")
                    val rawStream: InputStream = response.body.byteStream()

                    if (!session.initialFormatDetected) {
                        val peekBuffer = ByteArray(16384)
                        var bytesRead = 0
                        while (bytesRead < peekBuffer.size && session.isActive) {
                            val r = rawStream.read(peekBuffer, bytesRead, peekBuffer.size - bytesRead)
                            if (r == -1) break
                            bytesRead += r
                        }
                        if (!session.isActive) return@use
                        val sampleBytes = peekBuffer.copyOf(bytesRead)

                        // Dynamic HLS detection: many TV / AAC streams serve an HLS playlist without
                        // `Hls == 1` in the DB or `.m3u8` in the initial URL (e.g. after HTTP redirects).
                        if (HlsResolver.isHlsPlaylistResponse(finalUrl, contentType, sampleBytes)) {
                            Log.i("RecordManager", "Dynamically detected HLS playlist for ${station.Name} at $finalUrl")
                            detectedHlsUrl = finalUrl
                            return@use
                        }

                        // Check if response is a PLS/M3U playlist instead of raw audio
                        val resolvedUrl = resolvePlaylistStreamUrl(isolatedClient, userAgent, finalUrl, contentType, sampleBytes)
                        if (resolvedUrl != null && resolvedUrl != currentStreamUrl) {
                            Log.i("RecordManager", "Resolved playlist for ${station.Name}: $currentStreamUrl -> $resolvedUrl")
                            if (resolvedUrl.lowercase().contains(".m3u8")) {
                                detectedHlsUrl = resolvedUrl
                                return@use
                            }
                            currentStreamUrl = resolvedUrl
                            return@use
                        }

                        val detectedFormat = FormatParser.detectFormat(station.Codec, contentType, sampleBytes)
                        Log.i("RecordManager", "Detected format for ${station.Name}: ${detectedFormat.extension} (${detectedFormat.mimeType})")
                        val writer = MediaWriter(context, station.Name, detectedFormat)

                        if (!writer.openStream(station.StationUuid)) {
                            Log.e("RecordManager", "Failed to open MediaWriter stream for ${station.Name}")
                            stopSession(session)
                            return
                        }

                        session.mediaWriter = writer
                        session.initialFormatDetected = true
                        val uiExt = when (detectedFormat) {
                            FormatParser.AudioFormat.AAC,
                            FormatParser.AudioFormat.AAC_PLUS,
                            FormatParser.AudioFormat.MPEG_TS -> FormatParser.AudioFormat.M4A.extension
                            else -> detectedFormat.extension
                        }
                        updateStateIfActive(session) { it.copy(formatExtension = uiExt) }

                        if (bytesRead > 0) {
                            writer.write(peekBuffer, bytesRead)
                        }
                    }

                    if (!session.isActive) return@use
                    val writer = session.mediaWriter ?: return@use
                    val decoupledRecorder = ThreadDecoupledRecorder(
                        inputStream = rawStream,
                        mediaWriter = writer,
                        onBytesWritten = { addedBytes ->
                            val elapsed = System.currentTimeMillis() - session.startTimeMs
                            updateStateIfActive(session) {
                                it.copy(
                                    durationMs = elapsed,
                                    fileSizeBytes = it.fileSizeBytes + addedBytes
                                )
                            }
                        },
                        onDiskError = { e ->
                            Log.e("RecordManager", "Storage full or disk write failed", e)
                            updateStateIfActive(session) {
                                it.copy(
                                    error = context.getString(com.ounben.amaradio.R.string.database_error_no_space)
                                )
                            }
                            stopSession(session)
                        }
                    )

                    session.activeDecoupledRecorder = decoupledRecorder
                    if (session.isActive) {
                        try {
                            decoupledRecorder.start()
                        } finally {
                            session.activeDecoupledRecorder = null
                        }
                    }
                }

                if (!session.isActive) break
                detectedHlsUrl?.let { hlsUrl ->
                    recordViaHls(session, isolatedClient, hlsUrl, userAgent)
                    return
                }
            } catch (e: Exception) {
                if (!session.isActive) break
                Log.w("RecordManager", "Recording stream disconnected: ${e.message}. Reconnecting in 2s...")
                reconnectAttempts++
                if (reconnectAttempts > 5) {
                    Log.e("RecordManager", "Max reconnect attempts reached")
                    stopSession(session)
                    break
                }
                try { Thread.sleep(2000) } catch (_: Exception) {}
            }
        }
    }

    private fun recordViaHls(
        session: RecordingSession,
        httpClient: okhttp3.OkHttpClient,
        hlsUrl: String,
        userAgent: String
    ) {
        val station = session.station
        HlsResolver.recordHlsStream(
            httpClient = httpClient,
            m3u8Url = hlsUrl,
            userAgent = userAgent,
            isRecording = { session.isActive },
            onAudioData = { data, len ->
                if (!session.isActive) return@recordHlsStream
                if (!session.initialFormatDetected) {
                    val detectedFormat = FormatParser.detectFormat(station.Codec, "audio/aac", data.copyOf(minOf(len, 16384)))
                    val writer = MediaWriter(context, station.Name, detectedFormat, isHls = true)
                    if (writer.openStream(station.StationUuid)) {
                        session.mediaWriter = writer
                        session.initialFormatDetected = true
                        updateStateIfActive(session) {
                            it.copy(formatExtension = FormatParser.AudioFormat.M4A.extension)
                        }
                    }
                }
                session.mediaWriter?.write(data, len)
                val elapsed = System.currentTimeMillis() - session.startTimeMs
                updateStateIfActive(session) {
                    it.copy(
                        durationMs = elapsed,
                        fileSizeBytes = it.fileSizeBytes + len
                    )
                }
            }
        )
    }

    private fun resolvePlaylistStreamUrl(
        client: okhttp3.OkHttpClient,
        userAgent: String,
        url: String,
        contentType: String?,
        sampleBytes: ByteArray
    ): String? {
        val isPlsOrM3u = url.lowercase().endsWith(".pls") ||
            url.lowercase().endsWith(".m3u") ||
            (contentType != null && (contentType.contains("scpls") || contentType.contains("mpegurl") || contentType.contains("playlist")))

        val text = String(sampleBytes, Charsets.UTF_8)
        // Do not treat HLS playlists as simple M3U redirect lists
        if (text.contains("#EXT-X-", ignoreCase = true)) return null

        if (text.contains("[playlist]") || text.contains("File1=", ignoreCase = true)) {
            val fileMatch = Regex("""File1\s*=\s*(https?://[^\s\r\n]+)""", RegexOption.IGNORE_CASE).find(text)
            if (fileMatch != null) return fileMatch.groupValues[1].trim()
        } else if (text.contains("#EXTM3U") || text.lines().any { it.trim().startsWith("http") }) {
            val line = text.lines().firstOrNull { it.trim().startsWith("http") }
            if (line != null) return line.trim()
        }

        if (isPlsOrM3u) {
            try {
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", userAgent).build()
                client.newCall(req).execute().use { res ->
                    val bodyText = res.body?.string() ?: ""
                    val fileMatch = Regex("""File1\s*=\s*(https?://[^\s\r\n]+)""", RegexOption.IGNORE_CASE).find(bodyText)
                    if (fileMatch != null) return fileMatch.groupValues[1].trim()
                    val line = bodyText.lines().firstOrNull { it.trim().startsWith("http") }
                    if (line != null) return line.trim()
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun stopRecording() {
        val sessionToStop = synchronized(sessionLock) {
            detachActiveSessionLocked(stopForegroundService = true)
        } ?: return
        finalizeDetachedSessionAsync(sessionToStop)
    }

    private fun stopSession(session: RecordingSession) {
        val sessionToStop = synchronized(sessionLock) {
            if (activeSession !== session) return
            detachActiveSessionLocked(stopForegroundService = true)
        } ?: return
        finalizeDetachedSessionAsync(sessionToStop)
    }

    private fun detachActiveSessionLocked(stopForegroundService: Boolean): RecordingSession? {
        val session = activeSession ?: return null
        activeSession = null
        session.isActive = false
        session.autoStopJob?.cancel()
        session.autoStopJob = null
        session.activeDecoupledRecorder?.stop()
        session.httpClient?.dispatcher?.cancelAll()
        session.recordingThread?.interrupt()

        if (stopForegroundService) {
            _recordingState.value = RecordingState(isRecording = false)
            RecordService.stopService(context)
        }
        return session
    }

    private fun finalizeDetachedSessionAsync(session: RecordingSession) {
        CoroutineScope(Dispatchers.IO).launch {
            val thread = session.recordingThread
            if (thread != null && thread !== Thread.currentThread()) {
                try {
                    thread.join(3000L)
                } catch (_: InterruptedException) {}
            }
            val writer = session.mediaWriter
            val chapters = synchronized(session.trackChapters) { session.trackChapters.toList() }
            session.mediaWriter = null
            if (writer != null) {
                val result = writer.finalizeFile(session.station.StationUuid)
                if (result != null) {
                    saveRecordingToDatabase(session.station, session.startTimeMs, result, chapters)
                }
            }
        }
    }

    private fun saveRecordingToDatabase(
        station: DataRadioStation,
        recordingStartTimeMs: Long,
        result: MediaWriter.RecordingResult,
        chapters: List<Pair<String, Long>>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDb = AMARadioUserDatabase.getDatabase(context)
                val recordDao = userDb.recordDao()

                val recordId = recordDao.insertRecord(
                    RecordEntity(
                        StationUuid = station.StationUuid,
                        Name = station.Name,
                        Url = station.StreamUrl,
                        Homepage = station.HomePageUrl,
                        Favicon = station.IconUrl,
                        Country = station.Country,
                        CountryCode = station.CountryCode,
                        Subcountry = station.State,
                        Tags = station.TagsAll,
                        Language = station.Language,
                        Codec = station.Codec,
                        Bitrate = station.Bitrate,
                        Votes = station.Votes,
                        clickcount = station.ClickCount,
                        ClickTrend = station.ClickTrend,
                        Hls = station.Hls,
                        LastChangeTime = station.LastChangeTime,
                        Creation = station.Creation,
                        ChangeUuid = station.ChangeUuid,
                        LastCheckOkTime = station.LastCheckOkTime,
                        fileUri = result.uri.toString(),
                        timestamp = recordingStartTimeMs,
                        durationMs = result.durationMs,
                        fileSizeBytes = result.fileSizeBytes,
                        format = result.format.extension
                    )
                )

                if (recordId > 0) {
                    val trackEntities = chapters.map { (title, offset) ->
                        RecordTrackEntity(
                            recordId = recordId,
                            trackTitle = title,
                            offsetMs = offset,
                            timestamp = recordingStartTimeMs + offset
                        )
                    }
                    if (trackEntities.isNotEmpty()) {
                        recordDao.insertTracks(trackEntities)
                    }
                    withContext(Dispatchers.Main) {
                        Utils.showToast(context, com.ounben.amaradio.R.string.notify_recording_saved)
                        Unit
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordManager", "Error saving recording to DB", e)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: RecordManager? = null

        fun getInstance(context: Context): RecordManager {
            return instance ?: synchronized(this) {
                instance ?: RecordManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
