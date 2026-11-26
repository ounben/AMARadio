package com.ounben.amaradio.recorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles lossless radio stream recording and finalization into Scoped Storage (Android MediaStore).
 * Records into a temporary .part file first, then finalizes per format so duration and seeking
 * work accurately in RecordingPlayerDialog and external players:
 *  - MP3: synchronizes to first valid MPEG frame, scans frames for exact duration, and prepends a
 *         valid ID3v2 cover tag + fresh Xing/Info seek-table header.
 *  - AAC / AAC+ / HLS: losslessly muxes raw ADTS or MPEG-TS audio via AacMuxer into an M4A container
 *         with zero-based timestamps and embedded cover art.
 *  - OGG / OggFLAC: buffers complete Ogg pages, zero-bases live granulepos across chunk boundaries
 *         and chained streams, recalculates CRC32, and computes exact duration from granulepos.
 */
class MediaWriter(
    private val context: Context,
    private val stationName: String,
    private val format: FormatParser.AudioFormat,
    private val isHls: Boolean = false
) {
    companion object {
        private const val TAG = "MediaWriter"
    }

    private var tempPartFile: File? = null
    private var outputStream: OutputStream? = null
    private var baseFileName: String = ""
    private var firstAudioWriteTimeMs: Long = 0L
    private var totalAudioBytesWritten: Long = 0L

    private val oggStreamHandler = OggStreamHandler()
    private var mp3Synced = false

    fun openStream(stationUuid: String = ""): Boolean {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val sanitizedStation = sanitizeFileName(stationName)
        baseFileName = "${sanitizedStation}_$timeStr"
        firstAudioWriteTimeMs = 0L
        totalAudioBytesWritten = 0L
        mp3Synced = false

        return try {
            val tmpDir = File(context.cacheDir, "recordings_tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()

            val partExt = if (isHls) "hls.part" else "${format.extension}.part"
            val tmpFile = File(tmpDir, "${baseFileName}.$partExt")
            tempPartFile = tmpFile
            outputStream = BufferedOutputStream(FileOutputStream(tmpFile), 64 * 1024)
            Log.i(TAG, "Opened temp recording stream: ${tmpFile.absolutePath} (format=$format, isHls=$isHls)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temp recording file", e)
            false
        }
    }

    fun write(data: ByteArray, length: Int) {
        if (length <= 0) return
        try {
            val os = outputStream ?: return

            if (format == FormatParser.AudioFormat.OGG || format == FormatParser.AudioFormat.OGG_FLAC) {
                val processed = oggStreamHandler.processOggChunk(data, length)
                if (processed.isEmpty()) return
                if (firstAudioWriteTimeMs == 0L) {
                    firstAudioWriteTimeMs = System.currentTimeMillis()
                }
                os.write(processed, 0, processed.size)
                totalAudioBytesWritten += processed.size
                return
            }

            if (format == FormatParser.AudioFormat.MP3 && !isHls && !mp3Synced) {
                val syncOffset = Mp3FrameHelper.findFirstValidMp3FrameOffset(data, length)
                if (syncOffset < 0) {
                    // Discard leading garbage until we sync to a valid MP3 frame
                    return
                }
                mp3Synced = true
                val validLen = length - syncOffset
                if (validLen > 0) {
                    if (firstAudioWriteTimeMs == 0L) {
                        firstAudioWriteTimeMs = System.currentTimeMillis()
                    }
                    os.write(data, syncOffset, validLen)
                    totalAudioBytesWritten += validLen
                }
                return
            }

            if (firstAudioWriteTimeMs == 0L) {
                firstAudioWriteTimeMs = System.currentTimeMillis()
            }
            os.write(data, 0, length)
            totalAudioBytesWritten += length
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio bytes", e)
            throw e
        }
    }

    fun finalizeFile(stationUuid: String): RecordingResult? {
        val partFile = tempPartFile ?: return null
        try {
            if (format == FormatParser.AudioFormat.OGG || format == FormatParser.AudioFormat.OGG_FLAC) {
                val tail = oggStreamHandler.flush()
                if (tail.isNotEmpty()) {
                    outputStream?.write(tail, 0, tail.size)
                    totalAudioBytesWritten += tail.size
                }
            }
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing temp output stream", e)
        } finally {
            outputStream = null
        }

        if (!partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            return null
        }

        val wallClockDurationMs = if (firstAudioWriteTimeMs > 0L) {
            (System.currentTimeMillis() - firstAudioWriteTimeMs).coerceAtLeast(0L)
        } else {
            0L
        }

        var muxedTempFile: File? = null
        try {
            return when {
                // 1. AAC / AAC+ / HLS -> losslessly mux into M4A container with zero-based timestamps
                isHls || format == FormatParser.AudioFormat.AAC || format == FormatParser.AudioFormat.AAC_PLUS -> {
                    val m4aFile = File(partFile.parentFile, "$baseFileName.m4a")
                    muxedTempFile = m4aFile
                    val muxResult = AacMuxer.muxToM4a(partFile, m4aFile)

                    if (muxResult.success && m4aFile.exists() && m4aFile.length() > 0L) {
                        if (stationUuid.isNotEmpty()) {
                            TagEmbedder.embedM4aCoverArt(m4aFile, context, stationUuid, stationName)
                        }
                        val exactDuration = resolveBestDurationMs(
                            computedMs = muxResult.durationMs,
                            file = m4aFile,
                            wallClockMs = wallClockDurationMs
                        )
                        publishFileToMediaStore(
                            sourceFile = m4aFile,
                            finalFormat = FormatParser.AudioFormat.M4A,
                            durationMs = exactDuration
                        )
                    } else {
                        // Graceful fallback if an exotic stream cannot be muxed by MediaMuxer
                        Log.w(TAG, "M4A muxing failed for $baseFileName; falling back to raw ${format.extension}")
                        val fallbackDuration = resolveBestDurationMs(
                            computedMs = 0L,
                            file = partFile,
                            wallClockMs = wallClockDurationMs
                        )
                        publishFileToMediaStore(
                            sourceFile = partFile,
                            finalFormat = format,
                            durationMs = fallbackDuration
                        )
                    }
                }

                // 2. MP3 -> exact frame scan + ID3v2 cover art + fresh Xing/Info seek header
                format == FormatParser.AudioFormat.MP3 || format == FormatParser.AudioFormat.UNKNOWN -> {
                    val analysis = Mp3FrameHelper.analyzeMp3File(partFile)
                    val id3Tag = if (stationUuid.isNotEmpty()) {
                        TagEmbedder.createId3v2CoverArtTag(context, stationUuid, stationName)
                    } else {
                        null
                    }
                    val exactDuration = when {
                        analysis.durationMs > 0L -> analysis.durationMs
                        else -> resolveBestDurationMs(0L, partFile, wallClockDurationMs)
                    }
                    publishMp3ToMediaStore(
                        rawPartFile = partFile,
                        analysis = analysis,
                        id3Tag = id3Tag,
                        durationMs = exactDuration
                    )
                }

                // 3. OGG / OggFLAC -> exact duration from normalized granule positions
                format == FormatParser.AudioFormat.OGG || format == FormatParser.AudioFormat.OGG_FLAC -> {
                    val computedOggMs = oggStreamHandler.getComputedDurationMs()
                    val exactDuration = resolveBestDurationMs(
                        computedMs = computedOggMs,
                        file = partFile,
                        wallClockMs = wallClockDurationMs,
                        trustRetrieverOnlyIfReasonable = true
                    )
                    publishFileToMediaStore(
                        sourceFile = partFile,
                        finalFormat = format,
                        durationMs = exactDuration
                    )
                }

                // 4. FLAC or other formats
                else -> {
                    val exactDuration = resolveBestDurationMs(
                        computedMs = 0L,
                        file = partFile,
                        wallClockMs = wallClockDurationMs
                    )
                    publishFileToMediaStore(
                        sourceFile = partFile,
                        finalFormat = format,
                        durationMs = exactDuration
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording file", e)
            return null
        } finally {
            try { partFile.delete() } catch (_: Exception) {}
            try { muxedTempFile?.delete() } catch (_: Exception) {}
            tempPartFile = null
        }
    }

    private fun resolveBestDurationMs(
        computedMs: Long,
        file: File,
        wallClockMs: Long,
        trustRetrieverOnlyIfReasonable: Boolean = false
    ): Long {
        if (computedMs > 0L) return computedMs

        val retrieverMs = getFileDurationMs(file)
        if (retrieverMs > 0L) {
            if (!trustRetrieverOnlyIfReasonable || wallClockMs <= 0L) {
                return retrieverMs
            }
            val upperBound = maxOf(wallClockMs * 3L, wallClockMs + 60_000L)
            if (retrieverMs <= upperBound) {
                return retrieverMs
            }
        }
        return wallClockMs
    }

    private fun createMediaStoreEntry(finalFileName: String, finalFormat: FormatParser.AudioFormat): Uri? {
        val sanitizedStation = sanitizeFileName(stationName)
        val relativePath = "${Environment.DIRECTORY_MUSIC}/AMARadio/$sanitizedStation"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "AMARadio/$sanitizedStation")
                if (!targetDir.exists()) targetDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating target directory for $sanitizedStation", e)
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, finalFormat.mimeType)
            put(MediaStore.Audio.Media.TITLE, sanitizedStation)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        return context.contentResolver.insert(collection, contentValues)
    }

    private fun finishMediaStoreEntry(uri: Uri, finalFileName: String, durationMs: Long, totalBytes: Long) {
        val updateValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Audio.Media.DURATION, durationMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }
        try {
            val count = context.contentResolver.update(uri, updateValues, null, null)
            Log.i(TAG, "Finalized MediaStore entry (IS_PENDING=0, DURATION=${durationMs}ms, updated=$count) for $finalFileName ($totalBytes bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing MediaStore entry", e)
        }
    }

    private fun publishMp3ToMediaStore(
        rawPartFile: File,
        analysis: Mp3FrameHelper.Mp3Analysis,
        id3Tag: ByteArray?,
        durationMs: Long
    ): RecordingResult? {
        val finalFormat = FormatParser.AudioFormat.MP3
        val finalFileName = "$baseFileName.${finalFormat.extension}"
        val uri = createMediaStoreEntry(finalFileName, finalFormat) ?: return null

        val bytesWritten = context.contentResolver.openOutputStream(uri)?.use { os ->
            Mp3FrameHelper.writeFinalizedMp3(rawPartFile, analysis, id3Tag, os)
        } ?: return null

        finishMediaStoreEntry(uri, finalFileName, durationMs, bytesWritten)

        return RecordingResult(
            uri = uri,
            fileName = finalFileName,
            durationMs = durationMs,
            fileSizeBytes = bytesWritten,
            format = finalFormat
        )
    }

    private fun publishFileToMediaStore(
        sourceFile: File,
        finalFormat: FormatParser.AudioFormat,
        durationMs: Long
    ): RecordingResult? {
        val finalFileName = "$baseFileName.${finalFormat.extension}"
        val uri = createMediaStoreEntry(finalFileName, finalFormat) ?: return null

        var bytesWritten = 0L
        context.contentResolver.openOutputStream(uri)?.use { rawOs ->
            BufferedOutputStream(rawOs, 64 * 1024).use { bos ->
                BufferedInputStream(FileInputStream(sourceFile), 64 * 1024).use { bis ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = bis.read(buf)
                        if (r <= 0) break
                        bos.write(buf, 0, r)
                        bytesWritten += r
                    }
                    bos.flush()
                }
            }
        } ?: return null

        finishMediaStoreEntry(uri, finalFileName, durationMs, bytesWritten)

        return RecordingResult(
            uri = uri,
            fileName = finalFileName,
            durationMs = durationMs,
            fileSizeBytes = bytesWritten,
            format = finalFormat
        )
    }

    private fun getFileDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun sanitizeFileName(name: String): String {
        var sanitized = name.replace(Regex("""[\\/:*?"<>|!#$%&'()+,;=@\[\]^`{}~.]"""), "_").trim()
        while (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1).trim()
        }
        while (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length - 1).trim()
        }
        return sanitized.ifEmpty { "Radio_Station" }
    }

    data class RecordingResult(
        val uri: Uri,
        val fileName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: FormatParser.AudioFormat
    )
}
