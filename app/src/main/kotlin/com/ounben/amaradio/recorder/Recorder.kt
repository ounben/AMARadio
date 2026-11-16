package com.ounben.amaradio.recorder

import android.content.Context
import android.util.Log
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Main recording engine downloading live radio stream via OkHttp and piping bytes through IcyFilter to MediaWriter.
 */
class Recorder(
    private val context: Context,
    private val station: DataRadioStation
) {
    data class RecorderState(
        val isRecording: Boolean = false,
        val currentTrackTitle: String = "",
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val error: String? = null
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var icyFilter: IcyFilter? = null
    private var mediaWriter: MediaWriter? = null
    private var startTimeMs: Long = 0L

    fun startRecording(onFinished: (MediaWriter.RecordingResult?, List<Pair<String, Long>>) -> Unit) {
        if (_state.value.isRecording) return

        startTimeMs = System.currentTimeMillis()
        _state.value = RecorderState(isRecording = true)

        val trackChapters = mutableListOf<Pair<String, Long>>()

        recordJob = CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as AMARadioApp
            val httpClient = app.newHttpClientWithoutProxy()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val userAgent = com.ounben.amaradio.Utils.getUserAgent(context)

            val streamUrl = station.StreamUrl
            val request = Request.Builder()
                .url(streamUrl)
                .header("Icy-MetaData", "1")
                .header("User-Agent", userAgent)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = _state.value.copy(isRecording = false, error = "HTTP ${response.code}")
                        onFinished(null, emptyList())
                        return@use
                    }

                    val contentType = response.header("Content-Type")
                    val metaInt = response.header("icy-metaint")?.toIntOrNull() ?: 0

                    val rawStream: InputStream = response.body?.byteStream() ?: run {
                        _state.value = _state.value.copy(isRecording = false, error = "Empty stream body")
                        onFinished(null, emptyList())
                        return@use
                    }

                    val peekBuffer = ByteArray(16384)
                    var bytesRead = 0
                    while (bytesRead < peekBuffer.size) {
                        val r = rawStream.read(peekBuffer, bytesRead, peekBuffer.size - bytesRead)
                        if (r == -1) break
                        bytesRead += r
                    }

                    val detectedFormat = FormatParser.detectFormat(station.Codec, contentType, peekBuffer.copyOf(bytesRead))
                    mediaWriter = MediaWriter(context, station.Name, detectedFormat)

                    if (mediaWriter?.openStream() != true) {
                        _state.value = _state.value.copy(isRecording = false, error = "Failed to create recording file")
                        onFinished(null, emptyList())
                        return@use
                    }

                    if (bytesRead > 0) {
                        mediaWriter?.write(peekBuffer, bytesRead)
                    }

                    icyFilter = IcyFilter(
                        inputStream = rawStream,
                        metaInt = metaInt,
                        onAudioData = { data, len ->
                            mediaWriter?.write(data, len)
                            val currentDuration = System.currentTimeMillis() - startTimeMs
                            _state.value = _state.value.copy(
                                durationMs = currentDuration,
                                fileSizeBytes = (_state.value.fileSizeBytes + len)
                            )
                        },
                        onMetadata = { trackTitle ->
                            val offsetMs = System.currentTimeMillis() - startTimeMs
                            trackChapters.add(trackTitle to offsetMs)
                            _state.value = _state.value.copy(currentTrackTitle = trackTitle)
                        }
                    )

                    icyFilter?.parseStream()

                    val result = mediaWriter?.finalizeFile(station.StationUuid)
                    _state.value = _state.value.copy(isRecording = false)
                    onFinished(result, trackChapters)
                }
            } catch (e: Exception) {
                Log.e("Recorder", "Recording error", e)
                val result = mediaWriter?.finalizeFile(station.StationUuid)
                _state.value = _state.value.copy(isRecording = false, error = e.localizedMessage)
                onFinished(result, trackChapters)
            }
        }
    }

    fun stopRecording() {
        icyFilter?.stop()
        recordJob?.cancel()
    }
}
