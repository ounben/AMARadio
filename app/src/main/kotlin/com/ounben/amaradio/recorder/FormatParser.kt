package com.ounben.amaradio.recorder

/**
 * Detects audio/container format (MP3, AAC, AAC+, M4A, MPEG_TS, OGG, OGG_FLAC, FLAC)
 * from wire Magic Bytes, Content-Type headers, and station codec metadata.
 */
object FormatParser {

    enum class AudioFormat(val extension: String, val mimeType: String) {
        MP3("mp3", "audio/mpeg"),
        AAC("aac", "audio/aac"),
        AAC_PLUS("aac", "audio/aac"),
        M4A("m4a", "audio/mp4"),
        MPEG_TS("ts", "video/mp2t"),
        OGG("ogg", "audio/ogg"),
        OGG_FLAC("oga", "audio/ogg"),
        FLAC("flac", "audio/flac"),
        UNKNOWN("mp3", "audio/mpeg") // Safe default
    }

    /**
     * Searches [data] for 3 consecutive 188-byte MPEG-TS packet sync bytes (0x47).
     * Returns the byte offset of the first aligned TS packet, or -1 if not MPEG-TS.
     */
    fun findMpegTsSyncOffset(data: ByteArray, length: Int = data.size): Int {
        val packetSize = 188
        if (length < packetSize * 2 + 1) return -1
        val maxStart = minOf(length - (packetSize * 2) - 1, packetSize * 2)
        for (i in 0..maxStart) {
            if (data[i] == 0x47.toByte() &&
                data[i + packetSize] == 0x47.toByte() &&
                data[i + packetSize * 2] == 0x47.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    fun detectFormat(codec: String?, contentType: String?, sampleBytes: ByteArray): AudioFormat {
        val sampleString = if (sampleBytes.isNotEmpty()) String(sampleBytes, Charsets.ISO_8859_1) else ""

        // 0. Detect container formats on the wire FIRST (TV streams are often tagged as "AAC"
        // in radio-browser.info even though the wire stream is MPEG-TS or fMP4 with video+audio).
        if (!contentType.isNullOrBlank()) {
            val lowerCT = contentType.lowercase()
            if (lowerCT.contains("mp2t") || lowerCT.contains("video/ts")) {
                return AudioFormat.MPEG_TS
            }
        }
        if (sampleBytes.size >= 8 &&
            sampleBytes[4] == 'f'.code.toByte() &&
            sampleBytes[5] == 't'.code.toByte() &&
            sampleBytes[6] == 'y'.code.toByte() &&
            sampleBytes[7] == 'p'.code.toByte()
        ) {
            return AudioFormat.M4A
        }
        if (findMpegTsSyncOffset(sampleBytes, sampleBytes.size) >= 0) {
            return AudioFormat.MPEG_TS
        }

        // 1. Check Station Codec from DB if explicit
        if (!codec.isNullOrBlank()) {
            val lowerCodec = codec.lowercase()
            if (lowerCodec.contains("aac+") || lowerCodec.contains("he-aac")) return AudioFormat.AAC_PLUS
            if (lowerCodec.contains("aac") || lowerCodec.contains("mp4") || lowerCodec.contains("flv")) return AudioFormat.AAC
            if (lowerCodec.contains("ogg") && lowerCodec.contains("flac")) return AudioFormat.OGG_FLAC
            if (lowerCodec.contains("ogg")) return AudioFormat.OGG
            if (lowerCodec.contains("flac")) return AudioFormat.FLAC
            if (lowerCodec.contains("mp3") || lowerCodec.contains("mpeg")) return AudioFormat.MP3
        }

        // 2. Check Content-Type header
        if (!contentType.isNullOrBlank()) {
            val lowerCT = contentType.lowercase()
            if (lowerCT.contains("aacp") || lowerCT.contains("aac+")) return AudioFormat.AAC_PLUS
            if (lowerCT.contains("aac") || lowerCT.contains("mp4")) return AudioFormat.AAC
            if (lowerCT.contains("ogg") && lowerCT.contains("flac")) return AudioFormat.OGG_FLAC
            if (lowerCT.contains("ogg")) return AudioFormat.OGG
            if (lowerCT.contains("flac")) return AudioFormat.FLAC
            if (lowerCT.contains("mpeg") || lowerCT.contains("mp3")) return AudioFormat.MP3
        }

        // 3. Check Magic Bytes & Ogg Codec Headers
        if (sampleBytes.size >= 4) {
            val b0 = sampleBytes[0].toInt() and 0xFF
            val b1 = sampleBytes[1].toInt() and 0xFF
            val b2 = sampleBytes[2].toInt() and 0xFF
            val b3 = sampleBytes[3].toInt() and 0xFF

            // OGG: "OggS" (0x4F 0x67 0x67 0x53)
            if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) {
                if (sampleString.contains("FLAC", ignoreCase = true) || sampleString.contains("fLaC")) {
                    return AudioFormat.OGG_FLAC
                }
                return AudioFormat.OGG
            }

            // FLAC: "fLaC" (0x66 0x4C 0x61 0x43)
            if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) {
                return AudioFormat.FLAC
            }

            // ADTS AAC: 12 bits sync (0xFF 0xF0) AND Layer bits = 00
            if (b0 == 0xFF && (b1 and 0xF0) == 0xF0 && (b1 and 0x06) == 0x00) {
                return AudioFormat.AAC
            }

            // MP3: Sync bits AND Layer != 00
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0x00) {
                return AudioFormat.MP3
            }
        }

        return AudioFormat.UNKNOWN
    }
}
