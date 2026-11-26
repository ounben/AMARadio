package com.ounben.amaradio.recorder

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Parses HLS (.m3u8) live stream playlists and downloads raw audio segments in real-time
 * starting at the live edge (avoiding dumping historical playlist backlog on recording start).
 */
object HlsResolver {

    private const val TAG = "HlsResolver"
    private const val MAX_TRACKED_SEGMENTS = 300

    fun recordHlsStream(
        httpClient: OkHttpClient,
        m3u8Url: String,
        userAgent: String,
        isRecording: () -> Boolean,
        onAudioData: (buffer: ByteArray, length: Int) -> Unit
    ) {
        // Insertion-ordered set so we can evict only the oldest entries without re-downloading active segments
        val downloadedSegments = LinkedHashSet<String>()
        var currentPlaylistUrl = m3u8Url
        var isFirstMediaPlaylistPoll = true

        while (isRecording()) {
            try {
                val request = Request.Builder()
                    .url(currentPlaylistUrl)
                    .header("User-Agent", userAgent)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    try { Thread.sleep(2500) } catch (_: InterruptedException) { break }
                    continue
                }

                val bodyStr = response.body?.string() ?: ""
                response.close()

                val baseUri = URI.create(currentPlaylistUrl)
                val lines = bodyStr.lines()

                // Check if Master Playlist pointing to a variant media playlist
                val isMasterPlaylist = lines.any { it.trim().startsWith("#EXT-X-STREAM-INF") }
                if (isMasterPlaylist) {
                    val variantLine = lines.firstOrNull {
                        val t = it.trim()
                        t.isNotEmpty() && !t.startsWith("#")
                    }
                    if (variantLine != null) {
                        val resolved = baseUri.resolve(variantLine.trim()).toString()
                        if (resolved != currentPlaylistUrl) {
                            currentPlaylistUrl = resolved
                            continue
                        }
                    }
                } else {
                    val variantLine = lines.firstOrNull {
                        val t = it.trim()
                        t.isNotEmpty() && !t.startsWith("#") && t.contains(".m3u8", ignoreCase = true)
                    }
                    if (variantLine != null) {
                        val resolved = baseUri.resolve(variantLine.trim()).toString()
                        if (resolved != currentPlaylistUrl) {
                            currentPlaylistUrl = resolved
                            continue
                        }
                    }
                }

                // Parse optional target duration for poll interval tuning
                var targetDurationSec = 6.0
                val segmentUrls = mutableListOf<String>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-TARGETDURATION:")) {
                        trimmed.substringAfter(":").trim().toDoubleOrNull()?.let {
                            if (it > 0) targetDurationSec = it
                        }
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val segmentUri = baseUri.resolve(trimmed).toString()
                        segmentUrls.add(segmentUri)
                    }
                }

                // On first poll of a live playlist, skip historical segments and start at the live edge (last 1 segment)
                val segmentsToFetch = if (isFirstMediaPlaylistPoll && segmentUrls.size > 1) {
                    isFirstMediaPlaylistPoll = false
                    segmentUrls.dropLast(1).forEach { downloadedSegments.add(it) }
                    listOf(segmentUrls.last())
                } else {
                    if (segmentUrls.isNotEmpty()) isFirstMediaPlaylistPoll = false
                    segmentUrls
                }

                // Download new audio segments
                for (segmentUrl in segmentsToFetch) {
                    if (!isRecording()) break
                    if (downloadedSegments.contains(segmentUrl)) continue

                    downloadedSegments.add(segmentUrl)
                    while (downloadedSegments.size > MAX_TRACKED_SEGMENTS) {
                        val oldest = downloadedSegments.iterator().next()
                        downloadedSegments.remove(oldest)
                    }

                    downloadSegment(httpClient, segmentUrl, userAgent, onAudioData)
                }

                val sleepMs = ((targetDurationSec * 500.0).toLong()).coerceIn(1500L, 4000L)
                try { Thread.sleep(sleepMs) } catch (_: InterruptedException) { break }
            } catch (e: Exception) {
                if (!isRecording()) break
                Log.w(TAG, "HLS poll error", e)
                try { Thread.sleep(2500) } catch (_: InterruptedException) { break }
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
                    val rawBytes = resp.body?.bytes() ?: return
                    if (rawBytes.isNotEmpty()) {
                        val offset = findHlsSegmentAudioOffset(rawBytes)
                        if (offset > 0 && offset < rawBytes.size) {
                            val trimmed = rawBytes.copyOfRange(offset, rawBytes.size)
                            onAudioData(trimmed, trimmed.size)
                        } else {
                            onAudioData(rawBytes, rawBytes.size)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading HLS segment: $segmentUrl", e)
        }
    }

    /**
     * HLS audio-only segments (e.g. .aac / .mp3) frequently begin with an ID3v2 header carrying
     * `com.apple.streaming.transportStreamTimestamp`. When segments are concatenated, mid-stream
     * ID3v2 tags can break ADTS/MediaExtractor sync. Strip leading ID3v2 headers when followed by
     * a valid ADTS AAC or MP3 frame sync header.
     */
    internal fun findHlsSegmentAudioOffset(bytes: ByteArray): Int {
        if (bytes.size < 14) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val hasFooter = (bytes[5].toInt() and 0x10) != 0
        val audioOffset = 10 + tagSize + (if (hasFooter) 10 else 0)
        if (audioOffset + 4 > bytes.size) return 0

        val b0 = bytes[audioOffset].toInt() and 0xFF
        val b1 = bytes[audioOffset + 1].toInt() and 0xFF
        val isAdtsOrMp3Sync = (b0 == 0xFF && (b1 and 0xE0) == 0xE0)
        return if (isAdtsOrMp3Sync) audioOffset else 0
    }
}
