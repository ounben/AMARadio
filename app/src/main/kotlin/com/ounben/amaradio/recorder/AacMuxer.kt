package com.ounben.amaradio.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Loss-lessly encapsulates raw ADTS AAC or MPEG-TS audio streams from a .part file
 * into an M4A (MP4) container file with zero-based, strictly monotonic timestamps
 * so duration and timeline seeking work accurately in ExoPlayer and external players.
 */
object AacMuxer {

    private const val TAG = "AacMuxer"

    data class MuxResult(
        val success: Boolean,
        val durationMs: Long = 0L,
        val audioMime: String = ""
    )

    fun muxToM4a(inputFile: File, outputFile: File): MuxResult {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            var audioMime = ""

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    audioMime = mime
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in input file: ${inputFile.name}")
                return MuxResult(success = false)
            }

            // MediaMuxer MP4 container supports AAC (audio/mp4a-latm) cleanly;
            // MP3 inside HLS-TS is also supported on API 26+ or can fall back.
            extractor.selectTrack(audioTrackIndex)

            val sampleRate = try {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } catch (_: Exception) {
                44100
            }
            // Approximate single AAC/MP3 frame duration in microseconds for discontinuity smoothing
            val defaultFrameDurationUs = if (sampleRate > 0) {
                (1024L * 1_000_000L) / sampleRate
            } else {
                23220L
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()
            isMuxerStarted = true

            val formatMaxSize = try {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                64 * 1024
            }
            var buffer = ByteBuffer.allocate(maxOf(formatMaxSize, 256 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            var baseOffsetUs = Long.MIN_VALUE
            var lastWrittenPtsUs = -1L
            var sampleCount = 0

            while (true) {
                buffer.clear()
                val sampleSize = try {
                    extractor.readSampleData(buffer, 0)
                } catch (_: IllegalArgumentException) {
                    // Grow buffer if a single sample exceeds current capacity
                    buffer = ByteBuffer.allocate(buffer.capacity() * 2)
                    extractor.readSampleData(buffer, 0)
                }
                if (sampleSize < 0) break

                val rawPtsUs = extractor.sampleTime
                if (rawPtsUs < 0L) {
                    if (!extractor.advance()) break
                    continue
                }

                if (baseOffsetUs == Long.MIN_VALUE) {
                    baseOffsetUs = rawPtsUs
                }

                var normalizedPtsUs = rawPtsUs - baseOffsetUs

                // Guard against timestamp wraparound or HLS segment discontinuity
                if (lastWrittenPtsUs >= 0L) {
                    val delta = normalizedPtsUs - lastWrittenPtsUs
                    if (delta <= 0L || delta > 5_000_000L) {
                        val nextPts = lastWrittenPtsUs + defaultFrameDurationUs
                        baseOffsetUs = rawPtsUs - nextPts
                        normalizedPtsUs = nextPts
                    }
                } else if (normalizedPtsUs < 0L) {
                    normalizedPtsUs = 0L
                }

                val sampleFlags = extractor.sampleFlags
                var codecFlags = 0
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = normalizedPtsUs
                bufferInfo.flags = codecFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                lastWrittenPtsUs = normalizedPtsUs
                sampleCount++

                if (!extractor.advance()) break
            }

            muxer.stop()
            isMuxerStarted = false

            if (sampleCount == 0) {
                Log.w(TAG, "No audio samples were muxed for ${inputFile.name}")
                return MuxResult(success = false)
            }

            val totalDurationMs = ((lastWrittenPtsUs + defaultFrameDurationUs) / 1000L).coerceAtLeast(0L)
            Log.i(TAG, "Muxed $sampleCount samples ($audioMime) to M4A, duration=${totalDurationMs}ms")
            return MuxResult(success = true, durationMs = totalDurationMs, audioMime = audioMime)
        } catch (e: Exception) {
            Log.e(TAG, "Error muxing audio to M4A", e)
            return MuxResult(success = false)
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
