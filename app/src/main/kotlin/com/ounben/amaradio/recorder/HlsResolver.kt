package com.ounben.amaradio.recorder

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Parses HLS (.m3u8) live stream playlists and downloads the raw audio segments in real-time.
 * Ensures HLS text playlist headers (#EXTM3U) are never written into the audio file.
 */
object HlsResolver {

    fun recordHlsStream(
        httpClient: OkHttpClient,
        m3u8Url: String,
        userAgent: String,
        isRecording: () -> Boolean,
        onAudioData: (buffer: ByteArray, length: Int) -> Unit
    ) {
        val downloadedSegments = HashSet<String>()
        var currentPlaylistUrl = m3u8Url

        while (isRecording()) {
            try {
                val request = Request.Builder()
                    .url(currentPlaylistUrl)
                    .header("User-Agent", userAgent)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    try { Thread.sleep(3000) } catch (e: Exception) { Log.w("HlsResolver", "Interrupted during retry sleep", e) }
                    continue
                }

                val bodyStr = response.body?.string() ?: ""
                response.close()

                val baseUri = URI.create(currentPlaylistUrl)
                val lines = bodyStr.lines()

                // Check if Master Playlist pointing to a variant media playlist
                val variantLine = lines.firstOrNull {
                    it.trim().isNotEmpty() && !it.startsWith("#") && (it.contains(".m3u8") || it.contains("index"))
                }
                if (variantLine != null && variantLine != currentPlaylistUrl) {
                    currentPlaylistUrl = baseUri.resolve(variantLine.trim()).toString()
                    continue
                }

                // Parse Media Playlist audio segments
                val segmentUrls = mutableListOf<String>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val segmentUri = baseUri.resolve(trimmed).toString()
                        segmentUrls.add(segmentUri)
                    }
                }

                // Download new audio segments
                for (segmentUrl in segmentUrls) {
                    if (!isRecording()) break
                    if (downloadedSegments.contains(segmentUrl)) continue

                    downloadedSegments.add(segmentUrl)
                    if (downloadedSegments.size > 200) {
                        downloadedSegments.clear()
                    }

                    downloadSegment(httpClient, segmentUrl, userAgent, onAudioData)
                }

                try { Thread.sleep(3500) } catch (e: Exception) { Log.w("HlsResolver", "Interrupted during segment sleep", e) }
            } catch (e: Exception) {
                if (!isRecording()) break
                Log.w("HlsResolver", "HLS poll error", e)
                try { Thread.sleep(3000) } catch (e2: Exception) { Log.w("HlsResolver", "Interrupted during error sleep", e2) }
            }
        }
    }

    private fun downloadSegment(
        httpClient: OkHttpClient,
        segmentUrl: String,
        userAgent: String,
        onAudioData: (ByteArray, Int) -> Unit
    ) {
        try {
            val req = Request.Builder()
                .url(segmentUrl)
                .header("User-Agent", userAgent)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return
                    if (bytes.isNotEmpty()) {
                        onAudioData(bytes, bytes.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HlsResolver", "Error downloading HLS segment: $segmentUrl", e)
        }
    }
}
