package com.ounben.amaradio.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Losslessly extracts the audio track from raw ADTS AAC, MPEG-TS (HLS/TV streams with video+audio),
 * or fMP4/CMAF HLS streams into a clean M4A (MP4) container (or raw MP3 if the container carries
 * MPEG Audio) with zero-based, strictly monotonic per-frame timestamps.
 */
@OptIn(UnstableApi::class)
object AacMuxer {

    private const val TAG = "AacMuxer"
    private const val TS_PROBE_MAX_BYTES = 2 * 1024 * 1024L

    data class MuxResult(
        val success: Boolean,
        val durationMs: Long = 0L,
        val audioMime: String = "",
        val outputFormat: FormatParser.AudioFormat = FormatParser.AudioFormat.M4A
    )

    internal data class TsAudioProbeResult(
        val trackId: Int,
        val format: Format,
        val mime: String
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
        // For MPEG-TS containers (e.g. HLS live streams like BBC Radio or TV streams), Android's
        // MediaExtractor merges multiple ADTS AAC frames from one PES packet into a single sample,
        // producing invalid MP4 samples that fail in ExoPlayer/Musicolet. Use Media3 TsExtractor
        // (MODE_HLS) to split every PES packet into individual, spec-compliant audio frames.
        val tsSyncOffset = detectMpegTsSyncOffset(inputFile)
        if (tsSyncOffset >= 0) {
            val tsResult = extractAudioFromMpegTs(inputFile, tsSyncOffset.toLong(), m4aOutputFile, mp3OutputFile)
            if (tsResult.success) {
                return tsResult
            }
            Log.w(TAG, "Media3 TsExtractor path failed for ${inputFile.name}; falling back to MediaExtractor")
            try { m4aOutputFile.delete() } catch (_: Exception) {}
            try { mp3OutputFile?.delete() } catch (_: Exception) {}
        }

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

    internal fun detectMpegTsSyncOffset(inputFile: File): Int {
        return try {
            val header = ByteArray(2048)
            val readLen = FileInputStream(inputFile).use { fis -> fis.read(header) }
            if (readLen <= 0) -1 else FormatParser.findMpegTsSyncOffset(header, readLen)
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Pass 1: Probes the MPEG-TS container with Media3 [TsExtractor] until all audio tracks in the
     * PMT have reported their [Format] (typically within the first few kilobytes), then picks the
     * best audio track (`audio/mp4a-latm` > `audio/mpeg` > any other audio track).
     */
    internal fun probeBestMpegTsAudioTrack(inputFile: File, startOffset: Long = 0L): TsAudioProbeResult? {
        val discoveredTracks = LinkedHashMap<Int, Format?>()
        var tracksEnded = false

        val tsExtractor = createHlsTsExtractor()
        tsExtractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                if (type != C.TRACK_TYPE_AUDIO) {
                    return DiscardingTrackOutput()
                }
                discoveredTracks.putIfAbsent(id, null)
                return object : TrackOutput {
                    override fun format(format: Format) {
                        discoveredTracks[id] = format
                    }

                    override fun sampleData(
                        input: DataReader,
                        length: Int,
                        allowEndOfInput: Boolean,
                        sampleDataPart: Int
                    ): Int {
                        val skipBuf = ByteArray(minOf(length, 4096))
                        var remaining = length
                        while (remaining > 0) {
                            val read = input.read(skipBuf, 0, minOf(remaining, skipBuf.size))
                            if (read == C.RESULT_END_OF_INPUT) {
                                return if (allowEndOfInput) C.RESULT_END_OF_INPUT else throw EOFException()
                            }
                            remaining -= read
                        }
                        return length
                    }

                    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                        data.skipBytes(length)
                    }

                    override fun sampleMetadata(
                        timeUs: Long,
                        flags: Int,
                        size: Int,
                        offset: Int,
                        cryptoData: TrackOutput.CryptoData?
                    ) = Unit
                }
            }

            override fun endTracks() {
                tracksEnded = true
            }

            override fun seekMap(seekMap: SeekMap) = Unit
        })

        runTsExtractorLoop(inputFile, startOffset, tsExtractor) { currentPos ->
            val allAudioFormatsKnown = tracksEnded &&
                discoveredTracks.isNotEmpty() &&
                discoveredTracks.values.all { it != null }
            allAudioFormatsKnown || (currentPos - startOffset) >= TS_PROBE_MAX_BYTES
        }

        var aacCandidate: TsAudioProbeResult? = null
        var mp3Candidate: TsAudioProbeResult? = null
        var fallbackCandidate: TsAudioProbeResult? = null

        for ((trackId, fmt) in discoveredTracks) {
            if (fmt == null) continue
            val mime = fmt.sampleMimeType ?: continue
            if (!mime.startsWith("audio/", ignoreCase = true)) continue
            val candidate = TsAudioProbeResult(trackId, fmt, mime)
            when {
                mime.equals(MimeTypes.AUDIO_AAC, ignoreCase = true) && aacCandidate == null -> aacCandidate = candidate
                mime.equals(MimeTypes.AUDIO_MPEG, ignoreCase = true) && mp3Candidate == null -> mp3Candidate = candidate
                fallbackCandidate == null -> fallbackCandidate = candidate
            }
        }

        return aacCandidate ?: mp3Candidate ?: fallbackCandidate
    }

    /**
     * Pass 2: Streams individual elementary audio frames of [selectedTrackId] from an MPEG-TS file
     * via Media3 [TsExtractor] (`MODE_HLS`), invoking [onSample] once per individual audio frame.
     */
    internal fun demuxMpegTsAudioTrack(
        inputFile: File,
        startOffset: Long,
        selectedTrackId: Int,
        onSample: (data: ByteArray, size: Int, ptsUs: Long, flags: Int) -> Unit
    ): Int {
        var sampleCount = 0
        val tsExtractor = createHlsTsExtractor()
        val collector = SingleTrackSampleCollector { data, size, ptsUs, flags ->
            onSample(data, size, ptsUs, flags)
            sampleCount++
        }

        tsExtractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput {
                return if (type == C.TRACK_TYPE_AUDIO && id == selectedTrackId) {
                    collector
                } else {
                    DiscardingTrackOutput()
                }
            }

            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) = Unit
        })

        runTsExtractorLoop(inputFile, startOffset, tsExtractor) { false }
        return sampleCount
    }

    private fun extractAudioFromMpegTs(
        inputFile: File,
        startOffset: Long,
        m4aOutputFile: File,
        mp3OutputFile: File?
    ): MuxResult {
        return try {
            val probed = probeBestMpegTsAudioTrack(inputFile, startOffset) ?: run {
                Log.w(TAG, "TsExtractor found no audio track in ${inputFile.name}")
                return MuxResult(success = false)
            }

            Log.i(
                TAG,
                "TsExtractor selected track #${probed.trackId} (${probed.mime}, ${probed.format.sampleRate}Hz, ${probed.format.channelCount}ch) in ${inputFile.name}"
            )

            // Raw elementary MP3 extraction (Mp3FrameHelper will analyze exact duration + Xing header)
            if (probed.mime.equals(MimeTypes.AUDIO_MPEG, ignoreCase = true) && mp3OutputFile != null) {
                var writtenFrames = 0
                BufferedOutputStream(FileOutputStream(mp3OutputFile), 64 * 1024).use { bos ->
                    writtenFrames = demuxMpegTsAudioTrack(inputFile, startOffset, probed.trackId) { data, size, _, _ ->
                        bos.write(data, 0, size)
                    }
                    bos.flush()
                }
                if (writtenFrames == 0 || mp3OutputFile.length() == 0L) {
                    return MuxResult(success = false)
                }
                Log.i(TAG, "TsExtractor extracted $writtenFrames elementary MP3 frames to ${mp3OutputFile.name}")
                return MuxResult(
                    success = true,
                    durationMs = 0L,
                    audioMime = probed.mime,
                    outputFormat = FormatParser.AudioFormat.MP3
                )
            }

            val mediaFormat = buildAndroidMediaFormat(probed.format) ?: run {
                Log.w(TAG, "Could not build Android MediaFormat from TsExtractor format: ${probed.format}")
                return MuxResult(success = false)
            }

            val sampleRate = if (probed.format.sampleRate > 0) probed.format.sampleRate else 44100
            val defaultFrameDurationUs = (1024L * 1_000_000L) / sampleRate
            val maxNormalDeltaUs = maxOf(defaultFrameDurationUs * 4L, 100_000L)

            var muxer: MediaMuxer? = null
            var isMuxerStarted = false
            try {
                muxer = MediaMuxer(m4aOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxerTrackIndex = muxer.addTrack(mediaFormat)
                muxer.start()
                isMuxerStarted = true

                val bufferInfo = MediaCodec.BufferInfo()
                var baseOffsetUs = Long.MIN_VALUE
                var lastWrittenPtsUs = -1L
                var sampleCount = 0

                demuxMpegTsAudioTrack(inputFile, startOffset, probed.trackId) { data, size, rawPtsUs, flags ->
                    if (size <= 0) return@demuxMpegTsAudioTrack

                    val normalizedPtsUs: Long
                    if (rawPtsUs == C.TIME_UNSET || rawPtsUs < 0L) {
                        normalizedPtsUs = if (lastWrittenPtsUs < 0L) 0L else lastWrittenPtsUs + defaultFrameDurationUs
                    } else {
                        if (baseOffsetUs == Long.MIN_VALUE) {
                            baseOffsetUs = rawPtsUs
                        }
                        var pts = rawPtsUs - baseOffsetUs
                        if (lastWrittenPtsUs >= 0L) {
                            val delta = pts - lastWrittenPtsUs
                            if (delta <= 0L || delta > maxNormalDeltaUs) {
                                val nextPts = lastWrittenPtsUs + defaultFrameDurationUs
                                baseOffsetUs = rawPtsUs - nextPts
                                pts = nextPts
                            }
                        } else if (pts < 0L) {
                            pts = 0L
                        }
                        normalizedPtsUs = pts
                    }

                    var codecFlags = 0
                    if ((flags and C.BUFFER_FLAG_KEY_FRAME) != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = size
                    bufferInfo.presentationTimeUs = normalizedPtsUs
                    bufferInfo.flags = codecFlags

                    muxer.writeSampleData(muxerTrackIndex, ByteBuffer.wrap(data, 0, size), bufferInfo)
                    lastWrittenPtsUs = normalizedPtsUs
                    sampleCount++
                }

                muxer.stop()
                isMuxerStarted = false

                if (sampleCount == 0) {
                    Log.w(TAG, "TsExtractor produced 0 audio samples")
                    return MuxResult(success = false)
                }

                val totalDurationMs = ((lastWrittenPtsUs + defaultFrameDurationUs) / 1000L).coerceAtLeast(0L)
                Log.i(TAG, "TsExtractor muxed $sampleCount individual samples (${probed.mime}) to M4A, duration=${totalDurationMs}ms")
                MuxResult(
                    success = true,
                    durationMs = totalDurationMs,
                    audioMime = probed.mime,
                    outputFormat = FormatParser.AudioFormat.M4A
                )
            } finally {
                try {
                    if (isMuxerStarted) {
                        muxer?.stop()
                    }
                } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error demuxing MPEG-TS via Media3 TsExtractor for ${inputFile.name}", e)
            MuxResult(success = false)
        }
    }

    private fun createHlsTsExtractor(): TsExtractor {
        return TsExtractor(
            TsExtractor.MODE_HLS,
            TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            SubtitleParser.Factory.UNSUPPORTED,
            TimestampAdjuster(0L),
            DefaultTsPayloadReaderFactory(0),
            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
        )
    }

    private fun runTsExtractorLoop(
        inputFile: File,
        startOffset: Long,
        extractor: TsExtractor,
        shouldStop: (currentPosition: Long) -> Boolean
    ) {
        RandomAccessFile(inputFile, "r").use { raf ->
            val fileLength = raf.length()
            if (startOffset >= fileLength) return
            raf.seek(startOffset)

            val dataReader = object : DataReader {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val read = raf.read(buffer, offset, length)
                    return if (read == -1) C.RESULT_END_OF_INPUT else read
                }
            }

            var input = DefaultExtractorInput(dataReader, startOffset, fileLength)
            val seekPosition = PositionHolder()
            while (true) {
                val result = try {
                    extractor.read(input, seekPosition)
                } catch (_: EOFException) {
                    Extractor.RESULT_END_OF_INPUT
                }
                when (result) {
                    Extractor.RESULT_END_OF_INPUT -> break
                    Extractor.RESULT_SEEK -> {
                        val target = seekPosition.position.coerceIn(0L, fileLength)
                        raf.seek(target)
                        input = DefaultExtractorInput(dataReader, target, fileLength)
                    }
                    Extractor.RESULT_CONTINUE -> {
                        if (shouldStop(input.position)) break
                    }
                }
            }
        }
    }

    private fun buildAndroidMediaFormat(format: Format): MediaFormat? {
        val mime = format.sampleMimeType ?: return null
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        if (sampleRate <= 0 || channelCount <= 0) return null
        if (mime.equals(MimeTypes.AUDIO_AAC, ignoreCase = true) && format.initializationData.isEmpty()) {
            return null
        }

        val mediaFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)
        if (format.bitrate > 0) {
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate)
        }
        for (i in format.initializationData.indices) {
            mediaFormat.setByteBuffer("csd-$i", ByteBuffer.wrap(format.initializationData[i]))
        }
        return mediaFormat
    }

    private class SingleTrackSampleCollector(
        private val onSample: (data: ByteArray, size: Int, ptsUs: Long, flags: Int) -> Unit
    ) : TrackOutput {
        private var buffer = ByteArray(16 * 1024)
        private var bufferedLen = 0

        override fun format(format: Format) = Unit

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int
        ): Int {
            if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) {
                val scratch = ByteArray(minOf(length, 4096))
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(scratch, 0, minOf(remaining, scratch.size))
                    if (read == C.RESULT_END_OF_INPUT) {
                        return if (allowEndOfInput) C.RESULT_END_OF_INPUT else throw EOFException()
                    }
                    remaining -= read
                }
                return length
            }
            ensureCapacity(bufferedLen + length)
            val bytesRead = input.read(buffer, bufferedLen, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                return if (allowEndOfInput) C.RESULT_END_OF_INPUT else throw EOFException()
            }
            bufferedLen += bytesRead
            return bytesRead
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) {
                data.skipBytes(length)
                return
            }
            ensureCapacity(bufferedLen + length)
            data.readBytes(buffer, bufferedLen, length)
            bufferedLen += length
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?
        ) {
            val sampleEnd = bufferedLen - offset
            val sampleStart = sampleEnd - size
            if (size > 0 && sampleStart >= 0 && sampleEnd <= bufferedLen) {
                if (sampleStart == 0) {
                    onSample(buffer, size, timeUs, flags)
                } else {
                    val slice = buffer.copyOfRange(sampleStart, sampleEnd)
                    onSample(slice, size, timeUs, flags)
                }
            }
            if (offset > 0 && sampleEnd >= 0 && sampleEnd < bufferedLen) {
                System.arraycopy(buffer, sampleEnd, buffer, 0, offset)
                bufferedLen = offset
            } else {
                bufferedLen = 0
            }
        }

        private fun ensureCapacity(required: Int) {
            if (required <= buffer.size) return
            var newSize = buffer.size * 2
            while (newSize < required) {
                newSize *= 2
            }
            buffer = buffer.copyOf(newSize)
        }
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
