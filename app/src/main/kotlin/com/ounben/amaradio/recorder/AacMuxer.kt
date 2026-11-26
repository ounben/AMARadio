package com.ounben.amaradio.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Losslessly extracts the audio track from raw ADTS AAC, MPEG-TS (TV streams with video+audio),
 * or fMP4/CMAF HLS streams into a clean M4A (MP4) container (or raw MP3 if the container carries
 * MPEG Audio) with zero-based, strictly monotonic timestamps.
 */
object AacMuxer {

    private const val TAG = "AacMuxer"

    data class MuxResult(
        val success: Boolean,
        val durationMs: Long = 0L,
        val audioMime: String = "",
        val outputFormat: FormatParser.AudioFormat = FormatParser.AudioFormat.M4A
    )

    /**
     * Demuxes [inputFile], selects the best audio track (ignoring any H.264/HEVC video tracks),
     * and writes either:
     *  - a clean `.m4a` file to [m4aOutputFile] for AAC/MP4-compatible audio, OR
     *  - a raw elementary `.mp3` stream to [mp3OutputFile] if the TV/HLS container carries `audio/mpeg`.
     */
    fun extractAudioFromContainer(
        inputFile: File,
        m4aOutputFile: File,
        mp3OutputFile: File? = null
    ): MuxResult {
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            val selectedTrack = selectBestAudioTrack(extractor)
            if (selectedTrack == null) {
                Log.e(TAG, "No audio track found in input file: ${inputFile.name}")
                return MuxResult(success = false)
            }

            val (audioTrackIndex, format, audioMime) = selectedTrack
            Log.i(TAG, "Selected audio track #$audioTrackIndex ($audioMime) out of ${extractor.trackCount} tracks in ${inputFile.name}")

            // If a TV/HLS container carries MP3 (audio/mpeg), extract raw MP3 frames so Mp3FrameHelper
            // can produce a universally compatible, seekable .mp3 (many Android MediaMuxers reject MP3-in-MP4).
            if (audioMime.equals("audio/mpeg", ignoreCase = true) && mp3OutputFile != null) {
                extractor.selectTrack(audioTrackIndex)
                return extractRawElementaryStream(extractor, format, audioMime, mp3OutputFile, FormatParser.AudioFormat.MP3)
            }

            extractor.selectTrack(audioTrackIndex)
            return muxSelectedTrackToM4a(extractor, format, audioMime, m4aOutputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio from container ${inputFile.name}", e)
            return MuxResult(success = false)
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    /** Convenience wrapper for ADTS / TS -> M4A */
    fun muxToM4a(inputFile: File, outputFile: File): MuxResult {
        return extractAudioFromContainer(inputFile, outputFile, mp3OutputFile = null)
    }

    private data class SelectedTrack(
        val index: Int,
        val format: MediaFormat,
        val mime: String
    )

    /**
     * Picks the best audio track from a potentially multi-track TV stream:
     * Priority: `audio/mp4a-latm` (AAC) > `audio/mpeg` (MP3) > any other `audio/` track.
     */
    private fun selectBestAudioTrack(extractor: MediaExtractor): SelectedTrack? {
        var aacTrack: SelectedTrack? = null
        var mp3Track: SelectedTrack? = null
        var fallbackAudioTrack: SelectedTrack? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (!mime.startsWith("audio/", ignoreCase = true)) continue

            val candidate = SelectedTrack(i, trackFormat, mime)
            when {
                mime.equals("audio/mp4a-latm", ignoreCase = true) && aacTrack == null -> aacTrack = candidate
                mime.equals("audio/mpeg", ignoreCase = true) && mp3Track == null -> mp3Track = candidate
                fallbackAudioTrack == null -> fallbackAudioTrack = candidate
            }
        }

        return aacTrack ?: mp3Track ?: fallbackAudioTrack
    }

    private fun muxSelectedTrackToM4a(
        extractor: MediaExtractor,
        format: MediaFormat,
        audioMime: String,
        outputFile: File
    ): MuxResult {
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        try {
            val sampleRate = try {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } catch (_: Exception) {
                44100
            }
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

                // Guard against timestamp wraparound or HLS/TV segment discontinuity
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
                Log.w(TAG, "No audio samples were muxed")
                return MuxResult(success = false)
            }

            val totalDurationMs = ((lastWrittenPtsUs + defaultFrameDurationUs) / 1000L).coerceAtLeast(0L)
            Log.i(TAG, "Muxed $sampleCount samples ($audioMime) to M4A, duration=${totalDurationMs}ms")
            return MuxResult(
                success = true,
                durationMs = totalDurationMs,
                audioMime = audioMime,
                outputFormat = FormatParser.AudioFormat.M4A
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error muxing audio ($audioMime) to M4A", e)
            return MuxResult(success = false)
        } finally {
            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun extractRawElementaryStream(
        extractor: MediaExtractor,
        format: MediaFormat,
        audioMime: String,
        outputFile: File,
        outputFormat: FormatParser.AudioFormat
    ): MuxResult {
        return try {
            val formatMaxSize = try {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) {
                64 * 1024
            }
            var buffer = ByteBuffer.allocate(maxOf(formatMaxSize, 256 * 1024))
            var byteArr = ByteArray(buffer.capacity())
            var sampleCount = 0

            BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { bos ->
                while (true) {
                    buffer.clear()
                    val sampleSize = try {
                        extractor.readSampleData(buffer, 0)
                    } catch (_: IllegalArgumentException) {
                        buffer = ByteBuffer.allocate(buffer.capacity() * 2)
                        byteArr = ByteArray(buffer.capacity())
                        extractor.readSampleData(buffer, 0)
                    }
                    if (sampleSize < 0) break

                    buffer.position(0)
                    buffer.get(byteArr, 0, sampleSize)
                    bos.write(byteArr, 0, sampleSize)
                    sampleCount++

                    if (!extractor.advance()) break
                }
                bos.flush()
            }

            if (sampleCount == 0 || outputFile.length() == 0L) {
                MuxResult(success = false)
            } else {
                Log.i(TAG, "Extracted $sampleCount elementary frames ($audioMime) to ${outputFile.name}")
                MuxResult(
                    success = true,
                    durationMs = 0L,
                    audioMime = audioMime,
                    outputFormat = outputFormat
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting elementary stream ($audioMime)", e)
            MuxResult(success = false)
        }
    }
}
