package com.ounben.amaradio.recorder

import android.util.Log

/**
 * Handles live OGG (Vorbis / Opus / OggFLAC) stream synchronization, granulepos normalization, and CRC32 recalculation.
 * Synchronizes incoming OGG stream bytes to the initial OggS BOS page.
 * Zero-bases live stream granulepos values starting from sample 0 and recalculates Ogg page CRC32 checksums
 * so recorded .ogg files have accurate durations and pass FFmpeg/VLC/Musicolet validation without CRC mismatches.
 */
class OggStreamHandler {

    private var hasSyncedBos = false
    private var initialGranulePos: Long = -1L
    private var discardedLeadingBytes = 0

    fun processOggChunk(buffer: ByteArray, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)

        var startIndex = 0

        // 1. Synchronize to initial OggS BOS page (Header Type 0x02)
        if (!hasSyncedBos) {
            var foundOffset = -1
            var i = 0
            while (i <= length - 6) {
                if (buffer[i] == 0x4F.toByte() && buffer[i + 1] == 0x67.toByte() &&
                    buffer[i + 2] == 0x67.toByte() && buffer[i + 3] == 0x53.toByte()
                ) {
                    val version = buffer[i + 4].toInt() and 0xFF
                    val headerType = buffer[i + 5].toInt() and 0xFF

                    if (version == 0) {
                        foundOffset = i
                        val isBos = (headerType and 0x02) != 0

                        val pageSegments = buffer[i + 26].toInt() and 0xFF
                        val payloadStart = i + 27 + pageSegments
                        var detectedCodec = "UNKNOWN"

                        if (payloadStart + 7 < length) {
                            if (buffer[payloadStart] == 0x01.toByte() &&
                                buffer[payloadStart + 1] == 'v'.code.toByte() &&
                                buffer[payloadStart + 2] == 'o'.code.toByte() &&
                                buffer[payloadStart + 3] == 'r'.code.toByte() &&
                                buffer[payloadStart + 4] == 'b'.code.toByte() &&
                                buffer[payloadStart + 5] == 'i'.code.toByte() &&
                                buffer[payloadStart + 6] == 's'.code.toByte()
                            ) {
                                detectedCodec = "OGG_VORBIS"
                            } else if (buffer[payloadStart] == 'O'.code.toByte() &&
                                buffer[payloadStart + 1] == 'p'.code.toByte() &&
                                buffer[payloadStart + 2] == 'u'.code.toByte() &&
                                buffer[payloadStart + 3] == 's'.code.toByte() &&
                                buffer[payloadStart + 4] == 'H'.code.toByte() &&
                                buffer[payloadStart + 5] == 'e'.code.toByte() &&
                                buffer[payloadStart + 6] == 'a'.code.toByte() &&
                                buffer[payloadStart + 7] == 'd'.code.toByte()
                            ) {
                                detectedCodec = "OGG_OPUS"
                            } else if ((buffer[payloadStart] == 0x7F.toByte() &&
                                buffer[payloadStart + 1] == 'F'.code.toByte() &&
                                buffer[payloadStart + 2] == 'L'.code.toByte() &&
                                buffer[payloadStart + 3] == 'A'.code.toByte() &&
                                buffer[payloadStart + 4] == 'C'.code.toByte()) ||
                                (buffer[payloadStart] == 'f'.code.toByte() &&
                                buffer[payloadStart + 1] == 'L'.code.toByte() &&
                                buffer[payloadStart + 2] == 'a'.code.toByte() &&
                                buffer[payloadStart + 3] == 'C'.code.toByte())
                            ) {
                                detectedCodec = "OGG_FLAC"
                                Log.w("OggStreamHandler", "Detected OggFLAC stream. Note: OggFLAC recorded files use .oga extension and wallClockDurationMs for maximum player compatibility.")
                            }
                        }

                        Log.i("OggStreamHandler", "Synchronized to OggS page at offset $i (isBOS=$isBos, codec=$detectedCodec, discarded $discardedLeadingBytes leading bytes)")
                        break
                    }
                }
                i++
            }

            if (foundOffset >= 0) {
                hasSyncedBos = true
                startIndex = foundOffset
            } else {
                discardedLeadingBytes += length
                return ByteArray(0)
            }
        }

        val outputLen = length - startIndex
        if (outputLen <= 0) return ByteArray(0)

        val output = buffer.copyOfRange(startIndex, length)

        // 2. Normalize live OGG granulepos (G_new = G_raw - G_start) & recalculate page CRC32
        var j = 0
        while (j <= outputLen - 27) {
            if (output[j] == 0x4F.toByte() && output[j + 1] == 0x67.toByte() &&
                output[j + 2] == 0x67.toByte() && output[j + 3] == 0x53.toByte()
            ) {
                val pageSegments = output[j + 26].toInt() and 0xFF
                var payloadSize = 0
                for (s in 0 until pageSegments) {
                    if (j + 27 + s < outputLen) {
                        payloadSize += output[j + 27 + s].toInt() and 0xFF
                    }
                }

                val pageHeaderSize = 27 + pageSegments
                val totalPageSize = pageHeaderSize + payloadSize

                if (j + totalPageSize > outputLen) break

                val rawGranulePos = readInt64LE(output, j + 6)

                if (rawGranulePos > 0L) {
                    if (initialGranulePos < 0L) {
                        initialGranulePos = rawGranulePos
                        Log.i("OggStreamHandler", "Captured initial OGG live granulepos: $initialGranulePos")
                    }

                    val adjustedGranulePos = (rawGranulePos - initialGranulePos).coerceAtLeast(0L)
                    writeInt64LE(output, j + 6, adjustedGranulePos)

                    // Zero out checksum bytes (22..25) before CRC32 calculation
                    output[j + 22] = 0
                    output[j + 23] = 0
                    output[j + 24] = 0
                    output[j + 25] = 0

                    // Recalculate 32-bit Ogg CRC32 checksum
                    val newCrc = OggCrc32.calculate(output, j, totalPageSize)
                    writeInt32LE(output, j + 22, newCrc)
                }

                j += totalPageSize
            } else {
                j++
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

    private fun writeInt32LE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}

/**
 * Standard Ogg CRC32 lookup table generator and calculator (RFC 3533 Section 6.1, Polynomial 0x04C11DB7).
 */
private object OggCrc32 {
    private val crcTable = IntArray(256)

    init {
        for (i in 0 until 256) {
            var reg = i shl 24
            for (j in 0 until 8) {
                reg = if ((reg and 0x80000000.toInt()) != 0) {
                    (reg shl 1) xor 0x04C11DB7
                } else {
                    reg shl 1
                }
            }
            crcTable[i] = reg
        }
    }

    fun calculate(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val byteVal = data[i].toInt() and 0xFF
            crc = (crc shl 8) xor crcTable[((crc ushr 24) xor byteVal) and 0xFF]
        }
        return crc
    }
}
