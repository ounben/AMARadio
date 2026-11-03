package com.ounben.amaradio.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Loss-lessly encapsulates raw ADTS AAC frames from a .part file into an M4A (MP4) container file.
 * Takes ~100ms for a full recording, requiring no audio re-encoding.
 */
object AacMuxer {

    fun muxAdtsToM4a(inputFile: File, outputFile: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var aacTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    aacTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (aacTrackIndex < 0 || format == null) {
                Log.e("AacMuxer", "No audio track found in ADTS file")
                return false
            }

            extractor.selectTrack(aacTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()
            isMuxerStarted = true

            val maxBufferSize = try {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                64 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            if (isMuxerStarted) {
                muxer.stop()
                isMuxerStarted = false
            }
            return true
        } catch (e: Exception) {
            Log.e("AacMuxer", "Error muxing ADTS to M4A", e)
            return false
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
