package com.ounben.amaradio.recorder

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Provides MP3 frame-boundary synchronization, exact millisecond duration calculation,
 * and valid Xing/Info seek-table header generation for recorded radio streams.
 */
object Mp3FrameHelper {

    private const val TAG = "Mp3FrameHelper"

    // MPEG1 Layer III bitrates (kbps)
    private val BITRATES_V1_L3 = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    )
    // MPEG1 Layer II bitrates (kbps)
    private val BITRATES_V1_L2 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0
    )
    // MPEG1 Layer I bitrates (kbps)
    private val BITRATES_V1_L1 = intArrayOf(
        0, 32, 64, 96, 128, 160, 192, 224, 256, 320, 352, 384, 416, 448, 0, 0
    )
    // MPEG2 / 2.5 Layer II & III bitrates (kbps)
    private val BITRATES_V2_L23 = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    )
    // MPEG2 / 2.5 Layer I bitrates (kbps)
    private val BITRATES_V2_L1 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0
    )

    private val SAMPLE_RATES_V1 = intArrayOf(44100, 48000, 32000, 0)
    private val SAMPLE_RATES_V2 = intArrayOf(22050, 24000, 16000, 0)
    private val SAMPLE_RATES_V25 = intArrayOf(11025, 12000, 8000, 0)

    data class FrameHeader(
        val versionBits: Int,     // 3=MPEG1, 2=MPEG2, 0=MPEG2.5
        val layerBits: Int,       // 3=Layer1, 2=Layer2, 1=Layer3
        val bitrateKbps: Int,
        val sampleRate: Int,
        val padding: Int,
        val channelMode: Int,     // 3=Mono
        val frameSize: Int,
        val samplesPerFrame: Int
    ) {
        val isMpeg1: Boolean get() = versionBits == 3
        val isLayer3: Boolean get() = layerBits == 1
        val sideInfoSize: Int
            get() = if (isMpeg1) {
                if (channelMode == 3) 17 else 32
            } else {
                if (channelMode == 3) 9 else 17
            }
    }

    data class Mp3Analysis(
        val totalAudioFrames: Int,
        val totalAudioBytes: Long,
        val durationMs: Long,
        val firstAudioOffset: Long,
        val firstFrame: FrameHeader?,
        val isVbr: Boolean,
        val frameOffsets: LongArray
    )

    fun parseFrameHeader(data: ByteArray, offset: Int, length: Int = data.size): FrameHeader? {
        if (offset < 0 || offset + 4 > length) return null

        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF

        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

        val versionBits = (b1 ushr 3) and 0x03
        val layerBits = (b1 ushr 1) and 0x03
        if (versionBits == 1 || layerBits == 0) return null

        val bitrateIdx = (b2 ushr 4) and 0x0F
        val sampleRateIdx = (b2 ushr 2) and 0x03
        val padding = (b2 ushr 1) and 0x01
        val channelMode = (b3 ushr 6) and 0x03

        if (bitrateIdx == 0 || bitrateIdx == 15 || sampleRateIdx == 3) return null

        val bitrateKbps = when {
            versionBits == 3 && layerBits == 1 -> BITRATES_V1_L3[bitrateIdx]
            versionBits == 3 && layerBits == 2 -> BITRATES_V1_L2[bitrateIdx]
            versionBits == 3 && layerBits == 3 -> BITRATES_V1_L1[bitrateIdx]
            layerBits == 3 -> BITRATES_V2_L1[bitrateIdx]
            else -> BITRATES_V2_L23[bitrateIdx]
        }
        if (bitrateKbps <= 0) return null

        val sampleRate = when (versionBits) {
            3 -> SAMPLE_RATES_V1[sampleRateIdx]
            2 -> SAMPLE_RATES_V2[sampleRateIdx]
            0 -> SAMPLE_RATES_V25[sampleRateIdx]
            else -> 0
        }
        if (sampleRate <= 0) return null

        val samplesPerFrame = when (layerBits) {
            3 -> 384
            2 -> 1152
            1 -> if (versionBits == 3) 1152 else 576
            else -> return null
        }

        val frameSize = if (layerBits == 3) {
            ((12 * bitrateKbps * 1000 / sampleRate) + padding) * 4
        } else {
            val factor = if (layerBits == 1 && versionBits != 3) 72 else 144
            (factor * bitrateKbps * 1000 / sampleRate) + padding
        }

        if (frameSize < 24 || frameSize > 4096) return null

        return FrameHeader(
            versionBits = versionBits,
            layerBits = layerBits,
            bitrateKbps = bitrateKbps,
            sampleRate = sampleRate,
            padding = padding,
            channelMode = channelMode,
            frameSize = frameSize,
            samplesPerFrame = samplesPerFrame
        )
    }

    /**
     * Finds the byte offset of the first valid, verified MP3 frame sync in [data].
     * Verifies by checking that a second compatible frame header follows at `offset + frameSize`
     * whenever enough bytes are available.
     * Returns -1 if no valid frame sync is found.
     */
    fun findFirstValidMp3FrameOffset(data: ByteArray, length: Int): Int {
        if (length < 4) return -1

        var start = 0
        // Skip ID3v2 header if present at start of stream
        if (length >= 10 &&
            data[0] == 'I'.code.toByte() &&
            data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()
        ) {
            val id3Size = ((data[6].toInt() and 0x7F) shl 21) or
                ((data[7].toInt() and 0x7F) shl 14) or
                ((data[8].toInt() and 0x7F) shl 7) or
                (data[9].toInt() and 0x7F)
            val afterId3 = 10 + id3Size
            if (afterId3 in 10 until length) {
                start = afterId3
            }
        }

        var firstUnverified = -1
        var i = start
        while (i <= length - 4) {
            val h1 = parseFrameHeader(data, i, length)
            if (h1 != null) {
                if (firstUnverified < 0) firstUnverified = i
                val nextOffset = i + h1.frameSize
                if (nextOffset + 4 <= length) {
                    val h2 = parseFrameHeader(data, nextOffset, length)
                    if (h2 != null &&
                        h2.versionBits == h1.versionBits &&
                        h2.layerBits == h1.layerBits &&
                        h2.sampleRate == h1.sampleRate
                    ) {
                        return i
                    }
                } else {
                    return i
                }
            }
            i++
        }
        return firstUnverified
    }

    /**
     * Scans a raw MP3 file frame-by-frame to compute exact audio duration, frame count,
     * and frame offsets (skipping any stale Xing/Info/VBRI header frames from live streams).
     */
    fun analyzeMp3File(file: File): Mp3Analysis {
        val fileLen = file.length()
        if (fileLen < 4) {
            return Mp3Analysis(0, 0L, 0L, 0L, null, false, LongArray(0))
        }

        val offsets = LongArrayList(4096)
        var totalFrames = 0
        var totalAudioBytes = 0L
        var totalSamples = 0L
        var firstAudioOffset = -1L
        var firstFrame: FrameHeader? = null
        var firstBitrate = -1
        var isVbr = false

        try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                var pos = 0L

                // Skip ID3v2 header if present
                if (fileLen >= 10) {
                    val id3 = ByteArray(10)
                    raf.seek(0L)
                    if (raf.read(id3) == 10 &&
                        id3[0] == 'I'.code.toByte() &&
                        id3[1] == 'D'.code.toByte() &&
                        id3[2] == '3'.code.toByte()
                    ) {
                        val id3Size = ((id3[6].toInt() and 0x7F) shl 21) or
                            ((id3[7].toInt() and 0x7F) shl 14) or
                            ((id3[8].toInt() and 0x7F) shl 7) or
                            (id3[9].toInt() and 0x7F)
                        val afterId3 = 10L + id3Size
                        if (afterId3 < fileLen) pos = afterId3
                    }
                }

                val buf = ByteArray(64 * 1024)
                var bufFileStart = pos
                var bufValid = 0

                fun ensureAvailable(fileOffset: Long, needed: Int): Boolean {
                    val rel = fileOffset - bufFileStart
                    if (rel >= 0 && rel + needed <= bufValid) return true
                    if (fileOffset + needed > fileLen) return false
                    raf.seek(fileOffset)
                    val read = raf.read(buf, 0, minOf(buf.size.toLong(), fileLen - fileOffset).toInt())
                    if (read < needed) return false
                    bufFileStart = fileOffset
                    bufValid = read
                    return true
                }

                while (pos + 4 <= fileLen) {
                    if (!ensureAvailable(pos, 4)) break
                    val rel = (pos - bufFileStart).toInt()
                    val header = parseFrameHeader(buf, rel, bufValid)
                    if (header == null) {
                        pos++
                        continue
                    }
                    if (pos + header.frameSize > fileLen) {
                        // Incomplete trailing frame at EOF
                        break
                    }

                    // For the first few frames, verify sync with the next frame if possible
                    if (totalFrames == 0 && pos + header.frameSize + 4 <= fileLen) {
                        if (ensureAvailable(pos, header.frameSize + 4)) {
                            val rel2 = (pos - bufFileStart).toInt()
                            val nextH = parseFrameHeader(buf, rel2 + header.frameSize, bufValid)
                            if (nextH == null ||
                                nextH.versionBits != header.versionBits ||
                                nextH.layerBits != header.layerBits ||
                                nextH.sampleRate != header.sampleRate
                            ) {
                                pos++
                                continue
                            }
                        }
                    }

                    // Skip any stale Xing / Info / VBRI metadata frame at the start
                    if (totalFrames == 0 && ensureAvailable(pos, header.frameSize)) {
                        val relFrame = (pos - bufFileStart).toInt()
                        if (isMetadataFrame(buf, relFrame, header)) {
                            pos += header.frameSize
                            continue
                        }
                    }

                    if (firstAudioOffset < 0L) {
                        firstAudioOffset = pos
                        firstFrame = header
                        firstBitrate = header.bitrateKbps
                    } else if (header.bitrateKbps != firstBitrate) {
                        isVbr = true
                    }

                    offsets.add(pos - firstAudioOffset)
                    totalFrames++
                    totalAudioBytes += header.frameSize
                    totalSamples += header.samplesPerFrame
                    pos += header.frameSize
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing MP3 file", e)
        }

        val sr = firstFrame?.sampleRate ?: 0
        val durationMs = if (sr > 0 && totalSamples > 0L) {
            (totalSamples * 1000L) / sr
        } else {
            0L
        }

        return Mp3Analysis(
            totalAudioFrames = totalFrames,
            totalAudioBytes = totalAudioBytes,
            durationMs = durationMs,
            firstAudioOffset = if (firstAudioOffset >= 0L) firstAudioOffset else 0L,
            firstFrame = firstFrame,
            isVbr = isVbr,
            frameOffsets = offsets.toArray()
        )
    }

    private fun isMetadataFrame(buf: ByteArray, frameOffset: Int, header: FrameHeader): Boolean {
        val xingPos = frameOffset + 4 + header.sideInfoSize
        if (xingPos + 4 <= frameOffset + header.frameSize) {
            val tag = String(buf, xingPos, 4, Charsets.ISO_8859_1)
            if (tag == "Xing" || tag == "Info") return true
        }
        val vbriPos = frameOffset + 4 + 32
        if (vbriPos + 4 <= frameOffset + header.frameSize) {
            val tag = String(buf, vbriPos, 4, Charsets.ISO_8859_1)
            if (tag == "VBRI") return true
        }
        return false
    }

    /**
     * Builds a spec-compliant MP3 Xing (VBR) or Info (CBR) header frame with exact
     * frame count, byte size, and 100-entry seek TOC for Layer III MP3 streams.
     * Returns null if the stream is not Layer III or has no frames.
     */
    fun buildXingHeaderFrame(analysis: Mp3Analysis): ByteArray? {
        val first = analysis.firstFrame ?: return null
        if (!first.isLayer3 || analysis.totalAudioFrames <= 0 || analysis.totalAudioBytes <= 0L) {
            return null
        }

        // Choose a bitrate index guaranteed to yield a frame >= 4 + sideInfoSize + 116 bytes
        val bitrateIdx = if (first.isMpeg1) 9 else 12 // 128 kbps in both MPEG1 and MPEG2/2.5
        val sampleRateIdx = when (first.sampleRate) {
            44100, 22050, 11025 -> 0
            48000, 24000, 12000 -> 1
            32000, 16000, 8000 -> 2
            else -> return null
        }

        val factor = if (first.isMpeg1) 144 else 72
        val xingFrameSize = (factor * 128 * 1000) / first.sampleRate
        val xingOffset = 4 + first.sideInfoSize
        if (xingFrameSize < xingOffset + 120) return null

        val frame = ByteArray(xingFrameSize)
        // 4-byte MPEG Audio header: sync=0xFFE, version, layer=01 (L3), protection=1 (no CRC)
        frame[0] = 0xFF.toByte()
        frame[1] = (0xE0 or (first.versionBits shl 3) or (first.layerBits shl 1) or 0x01).toByte()
        frame[2] = ((bitrateIdx shl 4) or (sampleRateIdx shl 2)).toByte()
        frame[3] = (first.channelMode shl 6).toByte()

        val tag = if (analysis.isVbr) "Xing" else "Info"
        frame[xingOffset] = tag[0].code.toByte()
        frame[xingOffset + 1] = tag[1].code.toByte()
        frame[xingOffset + 2] = tag[2].code.toByte()
        frame[xingOffset + 3] = tag[3].code.toByte()

        // Flags: 0x07 = Frames (0x01) | Bytes (0x02) | TOC (0x04)
        writeInt32BE(frame, xingOffset + 4, 0x00000007)

        // Frame count: exact number of audio frames (ExoPlayer XingSeeker multiplies by samplesPerFrame)
        writeInt32BE(frame, xingOffset + 8, analysis.totalAudioFrames)

        // Byte count: total bytes from start of Xing frame to end of last audio frame
        val totalBytesWithXing = (analysis.totalAudioBytes + xingFrameSize)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        writeInt32BE(frame, xingOffset + 12, totalBytesWithXing)

        // 100-entry TOC (percent 0..99 mapped to 0..255 byte fraction)
        val tocOffset = xingOffset + 16
        val frameOffsets = analysis.frameOffsets
        val frameCount = frameOffsets.size
        frame[tocOffset] = 0
        for (percent in 1 until 100) {
            val frameIdx = ((percent.toLong() * frameCount) / 100L).toInt().coerceIn(0, frameCount - 1)
            val bytePos = frameOffsets[frameIdx] + xingFrameSize
            val scaled = ((bytePos * 256L) / totalBytesWithXing.coerceAtLeast(1)).toInt().coerceIn(0, 255)
            frame[tocOffset + percent] = scaled.toByte()
        }

        return frame
    }

    /**
     * Writes a clean, fully seekable MP3 file to [outputStream]:
     * `[Optional ID3v2 Tag] + [Fresh Xing/Info Header Frame] + [Clean MP3 Audio Frames]`.
     * Returns total bytes written.
     */
    fun writeFinalizedMp3(
        rawFile: File,
        analysis: Mp3Analysis,
        id3Tag: ByteArray?,
        outputStream: OutputStream
    ): Long {
        var totalWritten = 0L
        val bos = BufferedOutputStream(outputStream, 64 * 1024)

        if (id3Tag != null && id3Tag.isNotEmpty()) {
            bos.write(id3Tag)
            totalWritten += id3Tag.size
        }

        val xingFrame = buildXingHeaderFrame(analysis)
        if (xingFrame != null) {
            bos.write(xingFrame)
            totalWritten += xingFrame.size
        }

        FileInputStream(rawFile).use { fis ->
            val skipTarget = analysis.firstAudioOffset
            var skipped = 0L
            while (skipped < skipTarget) {
                val s = fis.skip(skipTarget - skipped)
                if (s <= 0) break
                skipped += s
            }

            val bis = BufferedInputStream(fis, 64 * 1024)
            val buffer = ByteArray(64 * 1024)
            var remaining = if (analysis.totalAudioBytes > 0L) analysis.totalAudioBytes else Long.MAX_VALUE
            while (remaining > 0L) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = bis.read(buffer, 0, toRead)
                if (read <= 0) break
                bos.write(buffer, 0, read)
                totalWritten += read
                remaining -= read
            }
        }

        bos.flush()
        return totalWritten
    }

    private fun writeInt32BE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    private class LongArrayList(initialCapacity: Int) {
        private var array = LongArray(initialCapacity)
        var size = 0
            private set

        fun add(value: Long) {
            if (size == array.size) {
                array = array.copyOf(array.size * 2)
            }
            array[size++] = value
        }

        fun toArray(): LongArray = array.copyOf(size)
    }
}
