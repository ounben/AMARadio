package com.ounben.amaradio.recorder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ounben.amaradio.utils.StationPlaceholderUtils
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Creates ID3v2.3 and MP4 metadata tags (Album, Artist, Title, Cover Art) from local station artwork & station name.
 * Embedded directly into .mp3 and .m4a recording files.
 */
object TagEmbedder {

    fun createId3v2CoverArtTag(context: Context, stationUuid: String, stationName: String): ByteArray? {
        val jpegBytes = getStationJpegBytes(context, stationUuid, stationName) ?: return null
        return try {
            createId3v2Tag(jpegBytes, stationName)
        } catch (e: Exception) {
            Log.e("TagEmbedder", "Error creating ID3 tag", e)
            null
        }
    }

    fun sanitizeMp3File(mp3File: File) {
        try {
            if (!mp3File.exists() || mp3File.length() < 100) return
            val bytes = mp3File.readBytes()
            val maxScan = minOf(bytes.size - 4, 65536)
            for (i in 0 until maxScan) {
                if ((bytes[i] == 'X'.code.toByte() && bytes[i + 1] == 'i'.code.toByte() && bytes[i + 2] == 'n'.code.toByte() && bytes[i + 3] == 'g'.code.toByte()) ||
                    (bytes[i] == 'I'.code.toByte() && bytes[i + 1] == 'n'.code.toByte() && bytes[i + 2] == 'f'.code.toByte() && bytes[i + 3] == 'o'.code.toByte())
                ) {
                    val raf = java.io.RandomAccessFile(mp3File, "rw")
                    raf.seek(i.toLong())
                    raf.write(byteArrayOf(0, 0, 0, 0))
                    raf.close()
                    Log.i("TagEmbedder", "Sanitized Xing/Info header in MP3 file at offset $i")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("TagEmbedder", "Error sanitizing MP3 file header", e)
        }
    }

    fun embedM4aCoverArt(m4aFile: File, context: Context, stationUuid: String, stationName: String): Boolean {
        if (!m4aFile.exists() || m4aFile.length() > 64L * 1024L * 1024L) {
            // Skip in-memory rewrite for very large files to avoid OOM
            return false
        }
        val jpegBytes = getStationJpegBytes(context, stationUuid, stationName) ?: return false
        return try {
            val fileBytes = m4aFile.readBytes()
            val updatedBytes = embedM4aMetadataBytes(fileBytes, jpegBytes, stationName) ?: return false
            m4aFile.writeBytes(updatedBytes)
            true
        } catch (e: Exception) {
            Log.e("TagEmbedder", "Error embedding M4A cover art & album metadata", e)
            false
        }
    }

    private data class Mp4Box(
        val type: String,
        val offset: Int,
        val size: Int,
        val headerSize: Int
    )

    /**
     * Appends an iTunes `udta/meta/ilst` metadata atom (cover art, album, artist, title) into the
     * MP4 `moov` box. Handles both 32-bit and 64-bit (`size == 1`) `mdat` boxes and properly shifts
     * all `stco`/`co64` chunk offsets when `moov` precedes `mdat` (streamable / fast-start MP4).
     */
    internal fun embedM4aMetadataBytes(
        fileBytes: ByteArray,
        jpegBytes: ByteArray,
        stationName: String
    ): ByteArray? {
        val topBoxes = parseTopLevelBoxes(fileBytes)
        val moovBox = topBoxes.firstOrNull { it.type == "moov" } ?: return null
        val mdatBox = topBoxes.firstOrNull { it.type == "mdat" } ?: return null

        // Only modify standard 32-bit header `moov` boxes
        if (moovBox.headerSize != 8 || moovBox.size <= 8 || moovBox.offset + moovBox.size > fileBytes.size) {
            return null
        }

        val udtaAtom = createUdtaAtom(jpegBytes, stationName)
        val newMoovSizeLong = moovBox.size.toLong() + udtaAtom.size.toLong()
        if (newMoovSizeLong > Int.MAX_VALUE) return null
        val newMoovSize = newMoovSizeLong.toInt()

        val moovEnd = moovBox.offset + moovBox.size
        val moovBytes = fileBytes.copyOfRange(moovBox.offset, moovEnd)
        writeInt32(moovBytes, 0, newMoovSize)

        // If `moov` comes before `mdat`, inserting `udta` at `moovEnd` shifts `mdat` forward by
        // `udtaAtom.size` bytes — every `stco`/`co64` chunk offset >= moovEnd MUST be shifted too,
        // otherwise players read the embedded JPEG bytes as audio data!
        if (moovBox.offset < mdatBox.offset) {
            val shiftedOk = shiftChunkOffsetsInMoov(
                moovBytes = moovBytes,
                delta = udtaAtom.size,
                minOffsetToShift = moovEnd.toLong()
            )
            if (!shiftedOk) return null
        }

        val baos = ByteArrayOutputStream(fileBytes.size + udtaAtom.size)
        baos.write(fileBytes, 0, moovBox.offset)
        baos.write(moovBytes)
        baos.write(udtaAtom)
        baos.write(fileBytes, moovEnd, fileBytes.size - moovEnd)
        return baos.toByteArray()
    }

    private fun parseTopLevelBoxes(data: ByteArray): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var offset = 0L
        val total = data.size.toLong()
        while (offset + 8L <= total) {
            val off = offset.toInt()
            val size32 = readUInt32(data, off)
            val type = String(data, off + 4, 4, Charsets.ISO_8859_1)
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    headerSize = 8
                    boxSize = total - offset
                }
                1L -> {
                    if (offset + 16L > total) break
                    headerSize = 16
                    boxSize = readUInt64(data, off + 8)
                }
                else -> {
                    headerSize = 8
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || offset + boxSize > total || boxSize > Int.MAX_VALUE) {
                break
            }
            boxes.add(Mp4Box(type = type, offset = off, size = boxSize.toInt(), headerSize = headerSize))
            offset += boxSize
        }
        return boxes
    }

    private val mp4ContainerTypes = setOf("moov", "trak", "mdia", "minf", "stbl")

    private fun shiftChunkOffsetsInMoov(
        moovBytes: ByteArray,
        delta: Int,
        minOffsetToShift: Long
    ): Boolean {
        return shiftChunkOffsetsRecursive(
            data = moovBytes,
            start = 8,
            end = moovBytes.size,
            delta = delta,
            minOffsetToShift = minOffsetToShift
        )
    }

    private fun shiftChunkOffsetsRecursive(
        data: ByteArray,
        start: Int,
        end: Int,
        delta: Int,
        minOffsetToShift: Long
    ): Boolean {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readUInt32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    headerSize = 8
                    boxSize = (end - pos).toLong()
                }
                1L -> {
                    if (pos + 16 > end) return false
                    headerSize = 16
                    boxSize = readUInt64(data, pos + 8)
                }
                else -> {
                    headerSize = 8
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || pos + boxSize > end) return false
            val boxEnd = (pos + boxSize).toInt()
            val payloadStart = pos + headerSize

            when {
                type in mp4ContainerTypes -> {
                    if (!shiftChunkOffsetsRecursive(data, payloadStart, boxEnd, delta, minOffsetToShift)) {
                        return false
                    }
                }
                type == "stco" -> {
                    if (payloadStart + 8 > boxEnd) return false
                    val entryCount = readUInt32(data, payloadStart + 4)
                    val entriesStart = payloadStart + 8
                    if (entriesStart + entryCount * 4L > boxEnd) return false
                    for (i in 0 until entryCount.toInt()) {
                        val entryOff = entriesStart + i * 4
                        val oldChunkOffset = readUInt32(data, entryOff)
                        if (oldChunkOffset >= minOffsetToShift) {
                            val newChunkOffset = oldChunkOffset + delta.toLong()
                            if (newChunkOffset > 0xFFFFFFFFL) return false
                            writeInt32(data, entryOff, newChunkOffset.toInt())
                        }
                    }
                }
                type == "co64" -> {
                    if (payloadStart + 8 > boxEnd) return false
                    val entryCount = readUInt32(data, payloadStart + 4)
                    val entriesStart = payloadStart + 8
                    if (entriesStart + entryCount * 8L > boxEnd) return false
                    for (i in 0 until entryCount.toInt()) {
                        val entryOff = entriesStart + i * 8
                        val oldChunkOffset = readUInt64(data, entryOff)
                        if (oldChunkOffset >= minOffsetToShift) {
                            val newChunkOffset = oldChunkOffset + delta.toLong()
                            writeInt64(data, entryOff, newChunkOffset)
                        }
                    }
                }
            }
            pos = boxEnd
        }
        return true
    }

    private fun createUdtaAtom(jpegBytes: ByteArray, stationName: String): ByteArray {
        // 1. 'covr' atom
        val dataPayload = ByteArray(16 + jpegBytes.size)
        writeInt32(dataPayload, 0, dataPayload.size)
        dataPayload[4] = 'd'.code.toByte()
        dataPayload[5] = 'a'.code.toByte()
        dataPayload[6] = 't'.code.toByte()
        dataPayload[7] = 'a'.code.toByte()
        dataPayload[8] = 0; dataPayload[9] = 0; dataPayload[10] = 0; dataPayload[11] = 13 // JPEG image
        dataPayload[12] = 0; dataPayload[13] = 0; dataPayload[14] = 0; dataPayload[15] = 0
        System.arraycopy(jpegBytes, 0, dataPayload, 16, jpegBytes.size)

        val covrAtom = createAtom("covr", dataPayload)

        // 2. '©alb' (Album) atom -> Station Name
        val albAtom = createTextAtom("\u00a9alb", stationName)

        // 3. '©ART' (Artist) atom -> Station Name
        val artAtom = createTextAtom("\u00a9ART", stationName)

        // 4. '©nam' (Title) atom -> Station Name
        val namAtom = createTextAtom("\u00a9nam", stationName)

        // Combine metadata atoms into 'ilst'
        val ilstPayload = covrAtom + albAtom + artAtom + namAtom
        val ilstAtom = createAtom("ilst", ilstPayload)

        // 'meta' atom
        val metaHeader = byteArrayOf(0, 0, 0, 0)
        val metaPayload = metaHeader + ilstAtom
        val metaAtom = createAtom("meta", metaPayload)

        // 'udta' atom
        return createAtom("udta", metaAtom)
    }

    private fun createTextAtom(type: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val dataPayload = ByteArray(16 + textBytes.size)
        writeInt32(dataPayload, 0, dataPayload.size)
        dataPayload[4] = 'd'.code.toByte()
        dataPayload[5] = 'a'.code.toByte()
        dataPayload[6] = 't'.code.toByte()
        dataPayload[7] = 'a'.code.toByte()
        dataPayload[8] = 0; dataPayload[9] = 0; dataPayload[10] = 0; dataPayload[11] = 1 // UTF-8 text
        dataPayload[12] = 0; dataPayload[13] = 0; dataPayload[14] = 0; dataPayload[15] = 0
        System.arraycopy(textBytes, 0, dataPayload, 16, textBytes.size)

        return createAtom(type, dataPayload)
    }

    private fun createAtom(type: String, payload: ByteArray): ByteArray {
        val atom = ByteArray(8 + payload.size)
        writeInt32(atom, 0, atom.size)
        atom[4] = type[0].code.toByte()
        atom[5] = type[1].code.toByte()
        atom[6] = type[2].code.toByte()
        atom[7] = type[3].code.toByte()
        System.arraycopy(payload, 0, atom, 8, payload.size)
        return atom
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFFL) shl 24) or
                ((data[offset + 1].toLong() and 0xFFL) shl 16) or
                ((data[offset + 2].toLong() and 0xFFL) shl 8) or
                (data[offset + 3].toLong() and 0xFFL)
    }

    private fun readUInt64(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (data[offset + i].toLong() and 0xFFL)
        }
        return value
    }

    private fun writeInt32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value shr 24).toByte()
        data[offset + 1] = (value shr 16).toByte()
        data[offset + 2] = (value shr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    private fun writeInt64(data: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            data[offset + i] = ((value ushr ((7 - i) * 8)) and 0xFFL).toByte()
        }
    }

    private fun getStationJpegBytes(context: Context, stationUuid: String, stationName: String): ByteArray? {
        val iconDir = File(context.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")

        if (iconFile.exists()) {
            return try {
                iconFile.readBytes()
            } catch (e: Exception) {
                Log.w("TagEmbedder", "Failed to read icon bytes for $stationUuid", e)
                null
            }
        }

        return try {
            val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(stationName, stationUuid)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            baos.toByteArray()
        } catch (e: Exception) {
            Log.w("TagEmbedder", "Failed to create placeholder bitmap for $stationUuid", e)
            null
        }
    }

    private fun createId3v2Tag(jpegBytes: ByteArray, stationName: String): ByteArray {
        // 1. APIC Frame (Cover Art)
        val apicFrame = createApicFrame(jpegBytes)
        // 2. TALB Frame (Album)
        val talbFrame = createTextFrame("TALB", stationName)
        // 3. TPE1 Frame (Artist)
        val tpe1Frame = createTextFrame("TPE1", stationName)

        val framesPayload = apicFrame + talbFrame + tpe1Frame
        val tagSize = framesPayload.size

        val id3Header = ByteArray(10)
        id3Header[0] = 'I'.code.toByte()
        id3Header[1] = 'D'.code.toByte()
        id3Header[2] = '3'.code.toByte()
        id3Header[3] = 0x03
        id3Header[4] = 0x00
        id3Header[5] = 0x00

        id3Header[6] = ((tagSize shr 21) and 0x7F).toByte()
        id3Header[7] = ((tagSize shr 14) and 0x7F).toByte()
        id3Header[8] = ((tagSize shr 7) and 0x7F).toByte()
        id3Header[9] = (tagSize and 0x7F).toByte()

        val totalTag = ByteArray(10 + framesPayload.size)
        System.arraycopy(id3Header, 0, totalTag, 0, 10)
        System.arraycopy(framesPayload, 0, totalTag, 10, framesPayload.size)
        return totalTag
    }

    private fun createApicFrame(jpegBytes: ByteArray): ByteArray {
        val mimeBytes = "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
        val descriptionBytes = byteArrayOf(0)
        val pictureType = 0x03.toByte()

        val apicPayloadSize = 1 + mimeBytes.size + 1 + descriptionBytes.size + jpegBytes.size
        val apicHeader = ByteArray(10)
        apicHeader[0] = 'A'.code.toByte()
        apicHeader[1] = 'P'.code.toByte()
        apicHeader[2] = 'I'.code.toByte()
        apicHeader[3] = 'C'.code.toByte()

        apicHeader[4] = (apicPayloadSize shr 24).toByte()
        apicHeader[5] = (apicPayloadSize shr 16).toByte()
        apicHeader[6] = (apicPayloadSize shr 8).toByte()
        apicHeader[7] = (apicPayloadSize and 0xFF).toByte()

        val apicFrame = ByteArray(10 + apicPayloadSize)
        System.arraycopy(apicHeader, 0, apicFrame, 0, 10)
        var pos = 10
        apicFrame[pos++] = 0x00
        System.arraycopy(mimeBytes, 0, apicFrame, pos, mimeBytes.size)
        pos += mimeBytes.size
        apicFrame[pos++] = pictureType
        System.arraycopy(descriptionBytes, 0, apicFrame, pos, descriptionBytes.size)
        pos += descriptionBytes.size
        System.arraycopy(jpegBytes, 0, apicFrame, pos, jpegBytes.size)
        return apicFrame
    }

    private fun createTextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        val payloadSize = 1 + textBytes.size
        val frame = ByteArray(10 + payloadSize)
        frame[0] = frameId[0].code.toByte()
        frame[1] = frameId[1].code.toByte()
        frame[2] = frameId[2].code.toByte()
        frame[3] = frameId[3].code.toByte()

        frame[4] = (payloadSize shr 24).toByte()
        frame[5] = (payloadSize shr 16).toByte()
        frame[6] = (payloadSize shr 8).toByte()
        frame[7] = (payloadSize and 0xFF).toByte()

        frame[8] = 0x00
        frame[9] = 0x00
        frame[10] = 0x00 // Encoding = ISO-8859-1
        System.arraycopy(textBytes, 0, frame, 11, textBytes.size)
        return frame
    }
}
