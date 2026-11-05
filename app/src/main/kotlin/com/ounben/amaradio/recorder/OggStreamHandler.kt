package com.ounben.amaradio.recorder

import android.util.Log

/**
 * Adjusts live OGG (Vorbis / Opus) stream granule positions in real-time.
 * Subtracts the initial live stream granulepos offset so the recorded file starts at sample 0.
 * Eliminates inflated file durations and trailing silence in recorded OGG files.
 */
class OggStreamHandler {

    private var initialGranulePos: Long = -1L

    fun processOggChunk(buffer: ByteArray, length: Int): ByteArray {
        if (length < 27) return buffer.copyOf(length)

        val output = buffer.copyOf(length)
        var i = 0

        while (i <= length - 27) {
            // Check OggS magic bytes
            if (output[i] == 0x4F.toByte() && output[i + 1] == 0x67.toByte() &&
                output[i + 2] == 0x67.toByte() && output[i + 3] == 0x53.toByte()
            ) {
                val rawGranulePos = readInt64LE(output, i + 6)

                if (rawGranulePos > 0L) {
                    if (initialGranulePos < 0L) {
                        initialGranulePos = rawGranulePos
                        Log.d("OggStreamHandler", "Captured initial OGG live granulepos: $initialGranulePos")
                    }

                    val adjustedGranulePos = (rawGranulePos - initialGranulePos).coerceAtLeast(0L)
                    writeInt64LE(output, i + 6, adjustedGranulePos)
                }

                val pageSegments = output[i + 26].toInt() and 0xFF
                val pageHeaderSize = 27 + pageSegments
                if (i + pageHeaderSize > length) break

                var payloadSize = 0
                for (s in 0 until pageSegments) {
                    if (i + 27 + s < length) {
                        payloadSize += output[i + 27 + s].toInt() and 0xFF
                    }
                }
                i += pageHeaderSize + payloadSize
            } else {
                i++
            }
        }

        return output
    }

    private fun readInt64LE(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (idx in 0 until 8) {
            value = value or ((data[offset + idx].toLong() and 0xFFL) shl (idx * 8))
        }
        return value
    }

    private fun writeInt64LE(data: ByteArray, offset: Int, value: Long) {
        for (idx in 0 until 8) {
            data[offset + idx] = ((value shr (idx * 8)) and 0xFFL).toByte()
        }
    }
}
