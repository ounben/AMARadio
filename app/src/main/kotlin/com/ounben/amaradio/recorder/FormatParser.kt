package com.ounben.amaradio.recorder

/**
 * Detects audio format (MP3, AAC, OGG, FLAC) from audio Magic Bytes or Content-Type headers.
 */
object FormatParser {

    enum class AudioFormat(val extension: String, val mimeType: String) {
        MP3("mp3", "audio/mpeg"),
        AAC("m4a", "audio/mp4"),
        OGG("ogg", "audio/ogg"),
        FLAC("flac", "audio/flac"),
        UNKNOWN("mp3", "audio/mpeg") // Safe default
    }

    fun detectFormat(codec: String?, contentType: String?, sampleBytes: ByteArray): AudioFormat {
        // 1. Check Station Codec from DB first if explicit
        if (!codec.isNullOrBlank()) {
            val lowerCodec = codec.lowercase()
            if (lowerCodec.contains("aac")) return AudioFormat.AAC
            if (lowerCodec.contains("ogg")) return AudioFormat.OGG
            if (lowerCodec.contains("flac")) return AudioFormat.FLAC
            if (lowerCodec.contains("mp3") || lowerCodec.contains("mpeg")) return AudioFormat.MP3
        }

        // 2. Check Content-Type header
        if (!contentType.isNullOrBlank()) {
            val lowerCT = contentType.lowercase()
            if (lowerCT.contains("aac")) return AudioFormat.AAC
            if (lowerCT.contains("ogg")) return AudioFormat.OGG
            if (lowerCT.contains("flac")) return AudioFormat.FLAC
            if (lowerCT.contains("mpeg") || lowerCT.contains("mp3")) return AudioFormat.MP3
        }

        // 3. Check Magic Bytes
        if (sampleBytes.size >= 4) {
            val b0 = sampleBytes[0].toInt() and 0xFF
            val b1 = sampleBytes[1].toInt() and 0xFF
            val b2 = sampleBytes[2].toInt() and 0xFF
            val b3 = sampleBytes[3].toInt() and 0xFF

            // OGG: "OggS" (0x4F 0x67 0x67 0x53)
            if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) {
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
