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

    fun embedM4aCoverArt(m4aFile: File, context: Context, stationUuid: String, stationName: String): Boolean {
        val jpegBytes = getStationJpegBytes(context, stationUuid, stationName) ?: return false
        return try {
            val fileBytes = m4aFile.readBytes()

            // Find 'moov' atom
            val moovOffset = findAtomOffset(fileBytes, "moov")
            if (moovOffset < 0) return false

            val oldMoovSize = readInt32(fileBytes, moovOffset)
            if (oldMoovSize <= 8 || moovOffset + oldMoovSize > fileBytes.size) return false

            val udtaAtom = createUdtaAtom(jpegBytes, stationName)

            // Build new file: [0 .. moovEnd-1] + udtaAtom + [moovEnd .. EOF]
            val moovEnd = moovOffset + oldMoovSize
            val newMoovSize = oldMoovSize + udtaAtom.size

            val baos = ByteArrayOutputStream(fileBytes.size + udtaAtom.size)
            baos.write(fileBytes, 0, moovOffset)
            baos.write(writeInt32(newMoovSize))
            baos.write(fileBytes, moovOffset + 4, oldMoovSize - 4)
            baos.write(udtaAtom)
            baos.write(fileBytes, moovEnd, fileBytes.size - moovEnd)

            m4aFile.writeBytes(baos.toByteArray())
            true
        } catch (e: Exception) {
            Log.e("TagEmbedder", "Error embedding M4A cover art & album metadata", e)
            false
        }
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

    private fun findAtomOffset(data: ByteArray, atomType: String): Int {
        val typeBytes = atomType.toByteArray(Charsets.ISO_8859_1)
        var i = 0
        while (i <= data.size - 8) {
            val size = readInt32(data, i)
            if (size < 8) {
                i++
                continue
            }
            if (data[i + 4] == typeBytes[0] &&
                data[i + 5] == typeBytes[1] &&
                data[i + 6] == typeBytes[2] &&
                data[i + 7] == typeBytes[3]
            ) {
                return i
            }
            i += size
        }
        return -1
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun writeInt32(value: Int): ByteArray {
        val arr = ByteArray(4)
        writeInt32(arr, 0, value)
        return arr
    }

    private fun writeInt32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value shr 24).toByte()
        data[offset + 1] = (value shr 16).toByte()
        data[offset + 2] = (value shr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    private fun getStationJpegBytes(context: Context, stationUuid: String, stationName: String): ByteArray? {
        val iconDir = File(context.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")

        if (iconFile.exists()) {
            return try {
                iconFile.readBytes()
            } catch (_: Exception) { null }
        }

        return try {
            val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(stationName, stationUuid)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            baos.toByteArray()
        } catch (_: Exception) { null }
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
