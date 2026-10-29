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

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, partFileName)
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
                val rawOs = context.contentResolver.openOutputStream(fileUri!!)
                outputStream = if (rawOs != null) java.io.BufferedOutputStream(rawOs, 64 * 1024) else null

                // Write ID3v2 Cover Art Tag at offset 0 if format is MP3
                if (outputStream != null && format == FormatParser.AudioFormat.MP3 && stationUuid.isNotEmpty()) {
                    val id3Tag = TagEmbedder.createId3v2CoverArtTag(context, stationUuid, stationName)
                    if (id3Tag != null && id3Tag.isNotEmpty()) {
                        outputStream?.write(id3Tag)
                        totalBytesWritten += id3Tag.size
                    }
                }

                outputStream != null
            } else false
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error creating MediaStore entry", e)
            false
        }
    }

    fun write(data: ByteArray, length: Int) {
        try {
            outputStream?.write(data, 0, length)
            totalBytesWritten += length
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error writing audio bytes", e)
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
                    val pfd = context.contentResolver.openFileDescriptor(uri, "rwt")
                    pfd?.use { descriptor ->
                        java.io.FileOutputStream(descriptor.fileDescriptor).use { os ->
                            tempM4aFile.inputStream().use { input -> input.copyTo(os) }
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

        val durationMs = System.currentTimeMillis() - startTimeMs

        // Finalize MediaStore entry: Rename .part to final name and clear IS_PENDING
        val updateValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }

        try {
            context.contentResolver.update(uri, updateValues, null, null)
        } catch (e: Exception) {
            Log.e("MediaWriter", "Error finalizing MediaStore entry", e)
        }

        return RecordingResult(
            uri = uri,
            fileName = finalFileName,
            durationMs = durationMs,
            fileSizeBytes = totalBytesWritten,
            format = format
        )
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Radio_Station" }
    }

    data class RecordingResult(
        val uri: Uri,
        val fileName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: FormatParser.AudioFormat
    )
}
