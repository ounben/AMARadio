package com.ounben.amaradio.recorder

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Parses HLS (.m3u8) live stream playlists — including TV streams with separate audio renditions
 * (`#EXT-X-MEDIA:TYPE=AUDIO`), multi-bitrate video+audio variants, fMP4/CMAF initialization segments
 * (`#EXT-X-MAP`), and AES-128 encrypted segments (`#EXT-X-KEY`) — and downloads raw audio segments
 * in real-time starting at the live edge.
 */
object HlsResolver {

    private const val TAG = "HlsResolver"
    private const val MAX_TRACKED_SEGMENTS = 300

    private data class Aes128KeyInfo(
        val keyUri: String,
        val explicitIv: ByteArray?
    )

    private data class MediaSegment(
        val url: String,
        val sequenceNumber: Long,
        val initMapUrl: String?,
        val keyInfo: Aes128KeyInfo?
    )

    fun recordHlsStream(
        httpClient: OkHttpClient,
        m3u8Url: String,
        userAgent: String,
        isRecording: () -> Boolean,
        onAudioData: (buffer: ByteArray, length: Int) -> Unit
    ) {
        // Insertion-ordered set so we can evict only the oldest entries without re-downloading active segments
        val downloadedSegments = LinkedHashSet<String>()
        val keyCache = HashMap<String, ByteArray>()
        var currentPlaylistUrl = m3u8Url
        var isFirstMediaPlaylistPoll = true
        var writtenInitMapUrl: String? = null

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

                // Use the final redirected URL as base for relative segment/variant URIs
                val effectivePlaylistUrl = response.request.url.toString()
                val bodyStr = response.body.string()
                response.close()

                val baseUri = URI.create(effectivePlaylistUrl)
                val lines = bodyStr.lines()

                // 1. Check if Master Playlist (TV/multi-bitrate streams) and pick the best audio playlist
                val selectedVariant = selectBestMasterVariantUrl(lines, baseUri)
                if (selectedVariant != null && selectedVariant != currentPlaylistUrl) {
                    Log.i(TAG, "Selected HLS audio/variant playlist: $selectedVariant")
                    currentPlaylistUrl = selectedVariant
                    continue
                }

                // 2. Parse Media Playlist segments, target duration, #EXT-X-MAP (fMP4), and #EXT-X-KEY (AES-128)
                var targetDurationSec = 6.0
                var mediaSequence = 0L
                var currentInitMapUrl: String? = null
                var currentKeyInfo: Aes128KeyInfo? = null
                val segments = mutableListOf<MediaSegment>()

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    when {
                        trimmed.startsWith("#EXT-X-TARGETDURATION:") -> {
                            trimmed.substringAfter(":").trim().toDoubleOrNull()?.let {
                                if (it > 0) targetDurationSec = it
                            }
                        }

                        trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                            trimmed.substringAfter(":").trim().toLongOrNull()?.let {
                                mediaSequence = it
                            }
                        }

                        trimmed.startsWith("#EXT-X-MAP:") -> {
                            val mapUri = extractAttribute(trimmed, "URI")
                            if (!mapUri.isNullOrBlank()) {
                                currentInitMapUrl = resolveSafeUri(baseUri, mapUri)
                            }
                        }

                        trimmed.startsWith("#EXT-X-KEY:") -> {
                            val method = extractAttribute(trimmed, "METHOD")?.uppercase() ?: "NONE"
                            if (method == "NONE") {
                                currentKeyInfo = null
                            } else if (method == "AES-128") {
                                val keyUri = extractAttribute(trimmed, "URI")
                                if (!keyUri.isNullOrBlank()) {
                                    val resolvedKeyUrl = resolveSafeUri(baseUri, keyUri)
                                    val ivHex = extractAttribute(trimmed, "IV")
                                    currentKeyInfo = Aes128KeyInfo(
                                        keyUri = resolvedKeyUrl,
                                        explicitIv = ivHex?.let { parseHexIv(it) }
                                    )
                                }
                            } else {
                                Log.w(TAG, "Unsupported HLS encryption method: $method")
                            }
                        }

                        !trimmed.startsWith("#") -> {
                            val segUrl = resolveSafeUri(baseUri, trimmed)
                            val seq = mediaSequence + segments.size
                            segments.add(
                                MediaSegment(
                                    url = segUrl,
                                    sequenceNumber = seq,
                                    initMapUrl = currentInitMapUrl,
                                    keyInfo = currentKeyInfo
                                )
                            )
                        }
                    }
                }

                // 3. On first poll of a live playlist, skip historical segments and start at the live edge
                val segmentsToFetch = if (isFirstMediaPlaylistPoll && segments.size > 1) {
                    isFirstMediaPlaylistPoll = false
                    segments.dropLast(1).forEach { downloadedSegments.add(it.url) }
                    listOf(segments.last())
                } else {
                    if (segments.isNotEmpty()) isFirstMediaPlaylistPoll = false
                    segments
                }

                // 4. Download new segments (including fMP4 init map if present)
                for (segment in segmentsToFetch) {
                    if (!isRecording()) break
                    if (downloadedSegments.contains(segment.url)) continue

                    // Write fMP4/CMAF initialization segment once before media fragments
                    if (segment.initMapUrl != null && segment.initMapUrl != writtenInitMapUrl) {
                        val initBytes = fetchRawBytes(httpClient, segment.initMapUrl, userAgent)
                        if (initBytes != null && initBytes.isNotEmpty()) {
                            onAudioData(initBytes, initBytes.size)
                            writtenInitMapUrl = segment.initMapUrl
                            Log.i(TAG, "Wrote HLS #EXT-X-MAP init segment (${initBytes.size} bytes)")
                        }
                    }

                    downloadedSegments.add(segment.url)
                    while (downloadedSegments.size > MAX_TRACKED_SEGMENTS) {
                        val oldest = downloadedSegments.iterator().next()
                        downloadedSegments.remove(oldest)
                    }

                    downloadSegment(httpClient, segment, userAgent, keyCache, onAudioData)
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

    /**
     * Selects the best audio-bearing media playlist from an HLS Master Playlist:
     * 1. Dedicated `#EXT-X-MEDIA:TYPE=AUDIO` rendition (zero video overhead for demuxed TV streams).
     * 2. Audio-only `#EXT-X-STREAM-INF` variant (highest bandwidth for best audio quality).
     * 3. Lowest-bandwidth muxed video+audio `#EXT-X-STREAM-INF` variant (minimal video download,
     *    audio track is later extracted cleanly by AacMuxer).
     * 4. Fallback: nested `.m3u8` URL if playlist lacks `#EXT-X-STREAM-INF` tags.
     */
    internal fun selectBestMasterVariantUrl(lines: List<String>, baseUri: URI): String? {
        val isMaster = lines.any {
            val t = it.trim()
            t.startsWith("#EXT-X-STREAM-INF:") || t.startsWith("#EXT-X-MEDIA:")
        }

        if (!isMaster) {
            val nestedM3u8 = lines.firstOrNull {
                val t = it.trim()
                t.isNotEmpty() && !t.startsWith("#") && t.contains(".m3u8", ignoreCase = true)
            }
            return nestedM3u8?.let { resolveSafeUri(baseUri, it.trim()) }
        }

        // Priority 1: Separate #EXT-X-MEDIA:TYPE=AUDIO rendition with explicit URI
        var fallbackAudioUri: String? = null
        var autoSelectAudioUri: String? = null
        for (line in lines) {
            val t = line.trim()
            if (!t.startsWith("#EXT-X-MEDIA:")) continue
            val type = extractAttribute(t, "TYPE")?.uppercase()
            if (type != "AUDIO") continue
            val uri = extractAttribute(t, "URI")
            if (uri.isNullOrBlank()) continue

            val resolved = resolveSafeUri(baseUri, uri)
            val isDefault = extractAttribute(t, "DEFAULT")?.equals("YES", ignoreCase = true) == true
            val isAutoSelect = extractAttribute(t, "AUTOSELECT")?.equals("YES", ignoreCase = true) == true
            if (isDefault) return resolved
            if (isAutoSelect && autoSelectAudioUri == null) autoSelectAudioUri = resolved
            if (fallbackAudioUri == null) fallbackAudioUri = resolved
        }
        if (autoSelectAudioUri != null) return autoSelectAudioUri
        if (fallbackAudioUri != null) return fallbackAudioUri

        // Priority 2 & 3: Parse #EXT-X-STREAM-INF variants
        data class Variant(val url: String, val bandwidth: Long, val isAudioOnly: Boolean)
        val variants = mutableListOf<Variant>()

        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.startsWith("#EXT-X-STREAM-INF:")) {
                val bandwidth = extractAttribute(t, "BANDWIDTH")?.toLongOrNull() ?: Long.MAX_VALUE
                val codecs = extractAttribute(t, "CODECS")?.lowercase() ?: ""
                val hasResolution = extractAttribute(t, "RESOLUTION") != null
                val hasVideoCodec = codecs.contains("avc") ||
                    codecs.contains("hev") ||
                    codecs.contains("hvc") ||
                    codecs.contains("vp0") ||
                    codecs.contains("av01") ||
                    codecs.contains("mp4v")
                val isAudioOnly = !hasResolution && codecs.isNotEmpty() && !hasVideoCodec

                // Find next non-empty, non-comment URI line
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j].trim()
                    if (next.isNotEmpty() && !next.startsWith("#")) {
                        variants.add(Variant(resolveSafeUri(baseUri, next), bandwidth, isAudioOnly))
                        break
                    }
                    j++
                }
                i = j
            }
            i++
        }

        if (variants.isEmpty()) return null

        // Prefer highest-quality audio-only variant if available
        val audioOnlyVariants = variants.filter { it.isAudioOnly }
        if (audioOnlyVariants.isNotEmpty()) {
            return audioOnlyVariants.maxByOrNull { it.bandwidth }?.url
        }

        // All variants are muxed video+audio TV streams -> pick lowest bandwidth to minimize video download
        return variants.minByOrNull { it.bandwidth }?.url
    }

    internal fun extractAttribute(tagLine: String, attributeName: String): String? {
        val afterColon = tagLine.substringAfter(":", "")
        if (afterColon.isEmpty()) return null
        val regex = Regex("""(?:^|,)\s*$attributeName=("([^"]*)"|([^,\s]*))""", RegexOption.IGNORE_CASE)
        val match = regex.find(afterColon) ?: return null
        return match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { null }
    }

    private fun resolveSafeUri(baseUri: URI, relativeOrAbsolute: String): String {
        return try {
            baseUri.resolve(relativeOrAbsolute).toString()
        } catch (_: Exception) {
            relativeOrAbsolute
        }
    }

    private fun downloadSegment(
        httpClient: OkHttpClient,
        segment: MediaSegment,
        userAgent: String,
        keyCache: MutableMap<String, ByteArray>,
        onAudioData: (ByteArray, Int) -> Unit
    ) {
        val downloaded = fetchRawBytes(httpClient, segment.url, userAgent) ?: return
        if (downloaded.isEmpty()) return

        val plainBytes = if (segment.keyInfo != null) {
            decryptAes128Segment(httpClient, userAgent, downloaded, segment.keyInfo, segment.sequenceNumber, keyCache)
                ?: return
        } else {
            downloaded
        }

        val offset = findHlsSegmentAudioOffset(plainBytes)
        if (offset > 0 && offset < plainBytes.size) {
            val trimmed = plainBytes.copyOfRange(offset, plainBytes.size)
            onAudioData(trimmed, trimmed.size)
        } else {
            onAudioData(plainBytes, plainBytes.size)
        }
    }

    private fun fetchRawBytes(httpClient: OkHttpClient, url: String, userAgent: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body.bytes() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading HLS resource: $url", e)
            null
        }
    }

    private fun decryptAes128Segment(
        httpClient: OkHttpClient,
        userAgent: String,
        encrypted: ByteArray,
        keyInfo: Aes128KeyInfo,
        sequenceNumber: Long,
        keyCache: MutableMap<String, ByteArray>
    ): ByteArray? {
        return try {
            val keyBytes = keyCache[keyInfo.keyUri] ?: run {
                val fetched = fetchRawBytes(httpClient, keyInfo.keyUri, userAgent)
                if (fetched != null && fetched.size == 16) {
                    keyCache[keyInfo.keyUri] = fetched
                    fetched
                } else {
                    Log.e(TAG, "Invalid AES-128 key size from ${keyInfo.keyUri}")
                    return null
                }
            }

            val ivBytes = keyInfo.explicitIv ?: ByteArray(16).apply {
                for (i in 0 until 8) {
                    this[15 - i] = ((sequenceNumber ushr (i * 8)) and 0xFFL).toByte()
                }
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting AES-128 HLS segment", e)
            null
        }
    }

    private fun parseHexIv(ivStr: String): ByteArray? {
        val hex = ivStr.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        if (hex.length != 32) return null
        return try {
            ByteArray(16) { idx ->
                hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether an HTTP response (URL, Content-Type, or initial body bytes) is an HLS playlist,
     * even when the station's DB metadata does not have `Hls == 1` or `.m3u8` in the URL.
     */
    fun isHlsPlaylistResponse(url: String, contentType: String?, sampleBytes: ByteArray): Boolean {
        if (url.lowercase().contains(".m3u8")) return true
        val lowerCt = contentType?.lowercase() ?: ""
        val text = if (sampleBytes.isNotEmpty()) String(sampleBytes, Charsets.ISO_8859_1) else ""
        val hasHlsTags = text.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
            text.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
            text.contains("#EXT-X-MEDIA", ignoreCase = true) ||
            text.contains("#EXT-X-VERSION", ignoreCase = true) ||
            text.contains("#EXTINF:", ignoreCase = true)
        if (hasHlsTags) return true
        if ((lowerCt.contains("mpegurl") || lowerCt.contains("apple")) && text.contains("#EXTM3U", ignoreCase = true)) {
            return true
        }
        return false
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
