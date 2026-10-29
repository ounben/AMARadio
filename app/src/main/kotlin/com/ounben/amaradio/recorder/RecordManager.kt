package com.ounben.amaradio.recorder

import android.content.Context
import android.util.Log
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
import java.io.InputStream
import java.util.concurrent.TimeUnit

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
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val formatExtension: String = "mp3",
        val error: String? = null
    )

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var currentStation: DataRadioStation? = null
    private var mediaWriter: MediaWriter? = null
    private var startTimeMs: Long = 0L
    private var initialFormatDetected = false
    private var recordingThread: Thread? = null
    private var autoStopJob: Job? = null
    private var activeDecoupledRecorder: ThreadDecoupledRecorder? = null
    private val trackChapters = mutableListOf<Pair<String, Long>>()

    fun startRecording(station: DataRadioStation, timerDurationMs: Long = 0L) {
        if (_recordingState.value.isRecording) return

        currentStation = station
        startTimeMs = System.currentTimeMillis()
        initialFormatDetected = false
        trackChapters.clear()

        _recordingState.value = RecordingState(
            isRecording = true,
            stationName = station.Name,
            stationUuid = station.StationUuid
        )

        RecordService.startService(context, station.Name)

        // Schedule auto-stop timer if duration is specified (> 0)
        autoStopJob?.cancel()
        if (timerDurationMs > 0) {
            autoStopJob = CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(timerDurationMs)
                if (_recordingState.value.isRecording) {
                    Log.d("RecordManager", "Auto-stop recording timer expired")
                    stopRecording()
                }
            }
        }

        // Start independent background recording stream on dedicated MAX_PRIORITY thread
        recordingThread?.interrupt()
        recordingThread = Thread({
            startIndependentRecordingStream(station)
        }, "AMARadio-Recorder-Thread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun startIndependentRecordingStream(station: DataRadioStation) {
        val isolatedClient = okhttp3.OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(2, 5, TimeUnit.MINUTES))
            .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 2 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val versionName = context.getString(com.ounben.amaradio.R.string.version_name)
        val userAgent = "AMARadio/$versionName (Android; +https://github.com/ounben/AMARadio)"

        if (station.StreamUrl.lowercase().contains(".m3u8")) {
            HlsResolver.recordHlsStream(
                httpClient = isolatedClient,
                m3u8Url = station.StreamUrl,
                userAgent = userAgent,
                isRecording = { _recordingState.value.isRecording },
                onAudioData = { data, len ->
                    if (!initialFormatDetected) {
                        val detectedFormat = FormatParser.detectFormat(station.Codec, "audio/aac", data.copyOf(minOf(len, 16384)))
                        mediaWriter = MediaWriter(context, station.Name, detectedFormat)
                        if (mediaWriter?.openStream(station.StationUuid) == true) {
                            initialFormatDetected = true
                            _recordingState.value = _recordingState.value.copy(formatExtension = detectedFormat.extension)
                        }
                    }
                    mediaWriter?.write(data, len)
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    _recordingState.value = _recordingState.value.copy(
                        durationMs = elapsed,
                        fileSizeBytes = _recordingState.value.fileSizeBytes + len
                    )
                }
            )
            return
        }

        var reconnectAttempts = 0

        while (_recordingState.value.isRecording) {
            val request = okhttp3.Request.Builder()
                .url(station.StreamUrl)
                .header("User-Agent", userAgent)
                .build()

            try {
                isolatedClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("RecordManager", "Recording connection attempt returned HTTP ${response.code}")
                        reconnectAttempts++
                        if (reconnectAttempts > 5) {
                            stopRecording()
                            return
                        }
                        try { Thread.sleep(2000) } catch (_: Exception) {}
                        return@use
                    }

                    reconnectAttempts = 0
                    val contentType = response.header("Content-Type")
                    val rawStream: InputStream = response.body?.byteStream() ?: return@use

                    if (!initialFormatDetected) {
                        val peekBuffer = ByteArray(16384)
                        var bytesRead = 0
                        while (bytesRead < peekBuffer.size) {
                            val r = rawStream.read(peekBuffer, bytesRead, peekBuffer.size - bytesRead)
                            if (r == -1) break
                            bytesRead += r
                        }

                        val detectedFormat = FormatParser.detectFormat(station.Codec, contentType, peekBuffer.copyOf(bytesRead))
                        mediaWriter = MediaWriter(context, station.Name, detectedFormat)

                        if (mediaWriter?.openStream(station.StationUuid) != true) {
                            Log.e("RecordManager", "Failed to open MediaWriter stream")
                            stopRecording()
                            return
                        }

                        initialFormatDetected = true
                        _recordingState.value = _recordingState.value.copy(formatExtension = detectedFormat.extension)

                        if (bytesRead > 0) {
                            mediaWriter?.write(peekBuffer, bytesRead)
                        }
                    }

                    val writer = mediaWriter ?: return@use
                    val decoupledRecorder = ThreadDecoupledRecorder(
                        inputStream = rawStream,
                        mediaWriter = writer,
                        onBytesWritten = { addedBytes ->
                            val elapsed = System.currentTimeMillis() - startTimeMs
                            _recordingState.value = _recordingState.value.copy(
                                durationMs = elapsed,
                                fileSizeBytes = _recordingState.value.fileSizeBytes + addedBytes
                            )
                        }
                    )

                    activeDecoupledRecorder = decoupledRecorder
                    decoupledRecorder.start()
                }
            } catch (e: Exception) {
                if (!_recordingState.value.isRecording) break
                Log.w("RecordManager", "Recording stream disconnected: ${e.message}. Reconnecting in 2s...")
                reconnectAttempts++
                if (reconnectAttempts > 5) {
                    Log.e("RecordManager", "Max reconnect attempts reached")
                    stopRecording()
                    break
                }
                try { Thread.sleep(2000) } catch (_: Exception) {}
            }
        }
    }

    fun stopRecording() {
        autoStopJob?.cancel()
        autoStopJob = null
        activeDecoupledRecorder?.stop()
        activeDecoupledRecorder = null
        recordingThread?.interrupt()
        recordingThread = null

        if (!_recordingState.value.isRecording) return

        val station = currentStation
        _recordingState.value = RecordingState(isRecording = false)

        val result = if (station != null) mediaWriter?.finalizeFile(station.StationUuid) else mediaWriter?.finalizeFile("")
        mediaWriter = null

        if (result != null && station != null) {
            saveRecordingToDatabase(station, result, trackChapters.toList())
        }

        RecordService.stopService(context)
    }

    private fun saveRecordingToDatabase(
        station: DataRadioStation,
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
                        timestamp = startTimeMs,
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
                            timestamp = startTimeMs + offset
                        )
                    }
                    if (trackEntities.isNotEmpty()) {
                        recordDao.insertTracks(trackEntities)
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
