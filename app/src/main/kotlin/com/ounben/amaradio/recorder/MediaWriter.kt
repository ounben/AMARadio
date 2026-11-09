package com.ounben.amaradio.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles loss-less file writing to Scoped Storage using Android MediaStore API.
 * Writes to Music/AMARadio/<StationName>/ as a .part file, then finalizes upon completion.
 */
class MediaWriter(
    private val context: Context,
    private val stationName: String,
    private val format: FormatParser.AudioFormat
) {
    private var fileUri: Uri? = null
    private var outputStream: OutputStream? = null
    private var partFileName: String = ""
    private var finalFileName: String = ""
    private var startTimeMs: Long = System.currentTimeMillis()
    private var totalBytesWritten: Long = 0L

    fun openStream(stationUuid: String = ""): Boolean {
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTimeMs))
        val sanitizedStation = sanitizeFileName(stationName)
        
        finalFileName = "${sanitizedStation}_$timeStr.${format.extension}"
        partFileName = "$finalFileName.part"

        val relativePath = "${Environment.DIRECTORY_MUSIC}/AMARadio/$sanitizedStation"

        // Ensure target directory exists on legacy storage (API < 29)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "AMARadio/$sanitizedStation")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
            } catch (e: Exception) {
                Log.e("MediaWriter", "Error creating target directory for $sanitizedStation", e)
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
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

        return try {
            fileUri = context.contentResolver.insert(collection, contentValues)
            if (fileUri != null) {
                Log.i("MediaWriter", "MediaStore file created: $finalFileName at URI: $fileUri ($relativePath)")
                val rawOs = context.contentResolver.openOutputStream(fileUri!!)
                outputStream = if (rawOs != null) java.io.BufferedOutputStream(rawOs, 64 * 1024) else null

                // Write ID3v2 Cover Art Tag at offset 0 if format is MP3
                if (outputStream != null && format == FormatParser.AudioFormat.MP3 && stationUuid.isNotEmpty()) {
                    val id3Tag = TagEmbedder.createId3v2CoverArtTag(context, stationUuid, stationName)
                    if (id3Tag != null && id3Tag.isNotEmpty()) {
                        outputStream?.write(id3Tag)
                        totalBytesWritten += id3Tag.size
                        Log.i("MediaWriter", "Wrote ID3v2 cover art tag (${id3Tag.size} bytes) for $stationName")
                    }
                }

                outputStream != null
            } else false
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error creating MediaStore entry", e)
            false
        }
    }

    private val oggStreamHandler = OggStreamHandler()

    fun write(data: ByteArray, length: Int) {
        try {
            if (totalBytesWritten == 0L) {
                startTimeMs = System.currentTimeMillis()
            }
            val bytesToWrite = if (format == FormatParser.AudioFormat.OGG || format == FormatParser.AudioFormat.OGG_FLAC) {
                oggStreamHandler.processOggChunk(data, length)
            } else {
                data
            }
            outputStream?.write(bytesToWrite, 0, length)
            totalBytesWritten += length
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error writing audio bytes", e)
            throw e
        }
    }

    fun finalizeFile(stationUuid: String): RecordingResult? {
        val uri = fileUri ?: return null
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error closing output stream", e)
        }

        // If AAC stream, encapsulate raw ADTS .part file into M4A container (0% re-encoding, 100% loss-less)
        if (format == FormatParser.AudioFormat.AAC) {
            try {
                val tempPartFile = File(context.cacheDir, "temp_rec_${startTimeMs}.part")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempPartFile.outputStream().use { output -> input.copyTo(output) }
                }

                val tempM4aFile = File(context.cacheDir, "temp_rec_${startTimeMs}.m4a")
                if (AacMuxer.muxAdtsToM4a(tempPartFile, tempM4aFile)) {
                    if (stationUuid.isNotEmpty()) {
                        TagEmbedder.embedM4aCoverArt(tempM4aFile, context, stationUuid, stationName)
                    }
                    val pfd = context.contentResolver.openFileDescriptor(uri, "rwt")
                    pfd?.use { descriptor ->
                        java.io.FileOutputStream(descriptor.fileDescriptor).use { os ->
                            os.channel.truncate(0)
                            tempM4aFile.inputStream().use { input -> input.copyTo(os) }
                            os.flush()
                        }
                    }
                    totalBytesWritten = tempM4aFile.length()
                }
                try { tempPartFile.delete() } catch (_: Exception) {}
                try { tempM4aFile.delete() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("MediaWriter", "Error converting AAC to M4A container", e)
            }
        }

        // If MP3 stream, sanitize Xing/Info header in-place so C++ MediaPlayerNative doesn't cap durationMs
        if (format == FormatParser.AudioFormat.MP3) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                pfd?.use { descriptor ->
                    val buffer = ByteArray(65536)
                    var read = -1
                    java.io.FileInputStream(descriptor.fileDescriptor).use { fis ->
                        read = fis.read(buffer)
                    }
                    if (read > 8) {
                        for (i in 0 until read - 4) {
                            if ((buffer[i] == 'X'.code.toByte() && buffer[i + 1] == 'i'.code.toByte() && buffer[i + 2] == 'n'.code.toByte() && buffer[i + 3] == 'g'.code.toByte()) ||
                                (buffer[i] == 'I'.code.toByte() && buffer[i + 1] == 'n'.code.toByte() && buffer[i + 2] == 'f'.code.toByte() && buffer[i + 3] == 'o'.code.toByte())
                            ) {
                                java.io.FileOutputStream(descriptor.fileDescriptor).use { fos ->
                                    val channel = fos.channel
                                    channel.position(i.toLong())
                                    channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0)))
                                    channel.force(true)
                                }
                                Log.i("MediaWriter", "Sanitized MP3 Xing/Info header at offset $i in $finalFileName")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaWriter", "Error sanitizing MP3 header in MediaStore", e)
            }
        }

        val wallClockDurationMs = System.currentTimeMillis() - startTimeMs
        val exactAudioDurationMs = getExactAudioFileDurationMs(context, uri)
        val finalDurationMs = if (format == FormatParser.AudioFormat.OGG || format == FormatParser.AudioFormat.OGG_FLAC || exactAudioDurationMs <= 0L || exactAudioDurationMs > wallClockDurationMs * 3) {
            wallClockDurationMs
        } else {
            exactAudioDurationMs
        }

        // Finalize MediaStore entry: Rename .part to final name, write DURATION, and clear IS_PENDING
        val updateValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Audio.Media.DURATION, finalDurationMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }

        try {
            val count = context.contentResolver.update(uri, updateValues, null, null)
            Log.i("MediaWriter", "Finalized MediaStore entry (IS_PENDING=0, DURATION=${finalDurationMs}ms, updated=$count) for $finalFileName ($totalBytesWritten bytes)")
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error finalizing MediaStore entry", e)
        }

        return RecordingResult(
            uri = uri,
            fileName = finalFileName,
            durationMs = finalDurationMs,
            fileSizeBytes = totalBytesWritten,
            format = format
        )
    }

    private fun getExactAudioFileDurationMs(context: Context, fileUri: Uri): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, fileUri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
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
