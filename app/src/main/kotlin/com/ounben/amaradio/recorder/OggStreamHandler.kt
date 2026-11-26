package com.ounben.amaradio.recorder

import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Handles live OGG (Vorbis / Opus / OggFLAC) stream synchronization, full page buffering across
 * arbitrary network chunk boundaries, granulepos normalization, chained-stream handling,
 * CRC32 recalculation, and exact duration calculation.
 */
class OggStreamHandler {

    companion object {
        private const val TAG = "OggStreamHandler"
        private const val MAX_PENDING_BYTES = 256 * 1024
    }

    private var hasSyncedBos = false
    private var discardedLeadingBytes = 0

    // Internal buffer holding incomplete Ogg pages across chunk boundaries
    private var pending = ByteArray(0)

    // Current logical bitstream tracking (supports chained OGG radio streams)
    private var currentSerialNumber: Int? = null
    private var currentSampleRate: Int = 0
    private var chainInitialGranulePos: Long = -1L
    private var currentChainMaxGranule: Long = 0L
    private var completedChainsDurationMs: Long = 0L

    fun processOggChunk(buffer: ByteArray, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)

        // Append incoming bytes to pending buffer
        val combined = ByteArray(pending.size + length)
        if (pending.isNotEmpty()) {
            System.arraycopy(pending, 0, combined, 0, pending.size)
        }
        System.arraycopy(buffer, 0, combined, pending.size, length)

        var offset = 0
        val totalLen = combined.size

        // 1. Synchronize to initial OggS page (preferring BOS page with headerType & 0x02)
        if (!hasSyncedBos) {
            val foundOffset = findInitialOggPage(combined, totalLen)
            if (foundOffset < 0) {
                // Keep last 3 bytes in case "OggS" straddles the chunk boundary
                val keep = minOf(3, totalLen)
                discardedLeadingBytes += (totalLen - keep)
                pending = combined.copyOfRange(totalLen - keep, totalLen)
                return ByteArray(0)
            }
            discardedLeadingBytes += foundOffset
            hasSyncedBos = true
            offset = foundOffset
        }

        val out = ByteArrayOutputStream(totalLen)

        // 2. Process all COMPLETE Ogg pages currently in the buffer
        while (offset <= totalLen - 27) {
            if (!isOggCapture(combined, offset)) {
                val nextSync = findNextOggCapture(combined, offset + 1, totalLen)
                if (nextSync < 0) {
                    val keep = minOf(3, totalLen - offset)
                    offset = totalLen - keep
                    break
                }
                offset = nextSync
                if (offset > totalLen - 27) break
            }

            val version = combined[offset + 4].toInt() and 0xFF
            if (version != 0) {
                offset++
                continue
            }

            val pageSegments = combined[offset + 26].toInt() and 0xFF
            val pageHeaderSize = 27 + pageSegments
            if (offset + pageHeaderSize > totalLen) {
                // Incomplete page header -> wait for more bytes
                break
            }

            var payloadSize = 0
            for (s in 0 until pageSegments) {
                payloadSize += combined[offset + 27 + s].toInt() and 0xFF
            }
            val totalPageSize = pageHeaderSize + payloadSize
            if (offset + totalPageSize > totalLen) {
                // Incomplete page payload -> wait for more bytes
                break
            }

            val page = combined.copyOfRange(offset, offset + totalPageSize)
            processCompletePage(page, pageHeaderSize)
            out.write(page)
            offset += totalPageSize
        }

        // Preserve remaining incomplete bytes for the next chunk
        pending = if (offset < totalLen) {
            val remLen = totalLen - offset
            if (remLen > MAX_PENDING_BYTES) {
                // Safety valve against malformed non-Ogg streams
                ByteArray(0)
            } else {
                combined.copyOfRange(offset, totalLen)
            }
        } else {
            ByteArray(0)
        }

        return out.toByteArray()
    }

    /**
     * Flushes any buffered bytes at stream end. Only complete Ogg pages are emitted
     * so no truncated trailing page corrupts the file's end-of-stream duration scan.
     */
    fun flush(): ByteArray {
        pending = ByteArray(0)
        return ByteArray(0)
    }

    /**
     * Returns the exact duration computed from normalized Ogg granule positions across
     * all logical bitstreams (including chained radio songs), or 0L if unknown.
     */
    fun getComputedDurationMs(): Long {
        val currentChainMs = if (currentSampleRate > 0 && currentChainMaxGranule > 0L) {
            (currentChainMaxGranule * 1000L) / currentSampleRate
        } else {
            0L
        }
        return completedChainsDurationMs + currentChainMs
    }

    private fun processCompletePage(page: ByteArray, payloadOffset: Int) {
        val headerType = page[5].toInt() and 0xFF
        val isBos = (headerType and 0x02) != 0
        val rawGranulePos = readInt64LE(page, 6)
        val serialNumber = readInt32LE(page, 14)

        if (isBos || (currentSerialNumber != null && currentSerialNumber != serialNumber)) {
            // Finalize duration of the previous logical bitstream in a chained OGG stream
            if (currentSerialNumber != null && currentSampleRate > 0 && currentChainMaxGranule > 0L) {
                completedChainsDurationMs += (currentChainMaxGranule * 1000L) / currentSampleRate
            }
            currentSerialNumber = serialNumber
            chainInitialGranulePos = -1L
            currentChainMaxGranule = 0L

            val detectedSr = detectSampleRateFromBos(page, payloadOffset)
            if (detectedSr > 0) {
                currentSampleRate = detectedSr
            }
        } else if (currentSerialNumber == null) {
            currentSerialNumber = serialNumber
        }

        // Only normalize pages carrying a positive audio granulepos.
        // Header pages have granulepos == 0; continuation pages have granulepos == -1.
        if (rawGranulePos > 0L) {
            if (chainInitialGranulePos < 0L) {
                // If a fresh chained stream already starts near 0 (< 2s), keep offset 0
                val threshold = if (currentSampleRate > 0) currentSampleRate.toLong() * 2L else 96000L
                chainInitialGranulePos = if (rawGranulePos <= threshold) 0L else rawGranulePos
                Log.i(TAG, "Captured OGG chain initial granulepos=$chainInitialGranulePos (raw=$rawGranulePos, sr=$currentSampleRate)")
            }

            val adjustedGranulePos = (rawGranulePos - chainInitialGranulePos).coerceAtLeast(0L)
            if (adjustedGranulePos > currentChainMaxGranule) {
                currentChainMaxGranule = adjustedGranulePos
            }

            if (adjustedGranulePos != rawGranulePos) {
                writeInt64LE(page, 6, adjustedGranulePos)
                // Zero out checksum bytes (22..25) before CRC32 calculation
                page[22] = 0
                page[23] = 0
                page[24] = 0
                page[25] = 0
                val newCrc = OggCrc32.calculate(page, 0, page.size)
                writeInt32LE(page, 22, newCrc)
            }
        }
    }

    private fun detectSampleRateFromBos(page: ByteArray, payloadOffset: Int): Int {
        val payloadLen = page.size - payloadOffset
        if (payloadLen < 8) return 0

        // Vorbis identification header: 0x01 "vorbis" + 4-byte version + 1-byte channels + 4-byte sampleRate LE
        if (payloadLen >= 16 &&
            page[payloadOffset] == 0x01.toByte() &&
            page[payloadOffset + 1] == 'v'.code.toByte() &&
            page[payloadOffset + 2] == 'o'.code.toByte() &&
            page[payloadOffset + 3] == 'r'.code.toByte() &&
            page[payloadOffset + 4] == 'b'.code.toByte() &&
            page[payloadOffset + 5] == 'i'.code.toByte() &&
            page[payloadOffset + 6] == 's'.code.toByte()
        ) {
            val sr = readInt32LE(page, payloadOffset + 12)
            Log.i(TAG, "Detected OGG_VORBIS BOS (sampleRate=$sr)")
            return if (sr in 8000..192000) sr else 44100
        }

        // Opus identification header: "OpusHead" -> granule position is ALWAYS 48000 Hz (RFC 7845)
        if (page[payloadOffset] == 'O'.code.toByte() &&
            page[payloadOffset + 1] == 'p'.code.toByte() &&
            page[payloadOffset + 2] == 'u'.code.toByte() &&
            page[payloadOffset + 3] == 's'.code.toByte() &&
            page[payloadOffset + 4] == 'H'.code.toByte() &&
            page[payloadOffset + 5] == 'e'.code.toByte() &&
            page[payloadOffset + 6] == 'a'.code.toByte() &&
            page[payloadOffset + 7] == 'd'.code.toByte()
        ) {
            Log.i(TAG, "Detected OGG_OPUS BOS (granule sampleRate=48000)")
            return 48000
        }

        // OggFLAC mapping header: 0x7F "FLAC" + version(2) + headers(2) + "fLaC"(4) + blockHeader(4) + STREAMINFO(34)
        if (payloadLen >= 31 &&
            page[payloadOffset] == 0x7F.toByte() &&
            page[payloadOffset + 1] == 'F'.code.toByte() &&
            page[payloadOffset + 2] == 'L'.code.toByte() &&
            page[payloadOffset + 3] == 'A'.code.toByte() &&
            page[payloadOffset + 4] == 'C'.code.toByte()
        ) {
            // STREAMINFO starts at payloadOffset + 17; sample rate is 20 bits starting at byte +10 of STREAMINFO
            val srOffset = payloadOffset + 17 + 10
            val sr = ((page[srOffset].toInt() and 0xFF) shl 12) or
                ((page[srOffset + 1].toInt() and 0xFF) shl 4) or
                ((page[srOffset + 2].toInt() and 0xF0) ushr 4)
            Log.i(TAG, "Detected OGG_FLAC BOS (sampleRate=$sr)")
            return if (sr in 8000..192000) sr else 44100
        }

        return 0
    }

    private fun findInitialOggPage(data: ByteArray, length: Int): Int {
        var firstNonBos = -1
        var i = 0
        while (i <= length - 6) {
            if (isOggCapture(data, i)) {
                val version = data[i + 4].toInt() and 0xFF
                val headerType = data[i + 5].toInt() and 0xFF
                if (version == 0) {
                    if ((headerType and 0x02) != 0) {
                        Log.i(TAG, "Synchronized to OggS BOS page at offset $i (discarded ${discardedLeadingBytes + i} leading bytes)")
                        return i
                    }
                    if (firstNonBos < 0) firstNonBos = i
                }
            }
            i++
        }
        return firstNonBos
    }

    private fun isOggCapture(data: ByteArray, offset: Int): Boolean {
        return data[offset] == 0x4F.toByte() &&
            data[offset + 1] == 0x67.toByte() &&
            data[offset + 2] == 0x67.toByte() &&
            data[offset + 3] == 0x53.toByte()
    }

    private fun findNextOggCapture(data: ByteArray, start: Int, length: Int): Int {
        var i = start
        while (i <= length - 4) {
            if (isOggCapture(data, i)) return i
            i++
        }
        return -1
    }

    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
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
            data[offset + idx] = ((value ushr (idx * 8)) and 0xFFL).toByte()
        }
    }

    private fun writeInt32LE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
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
