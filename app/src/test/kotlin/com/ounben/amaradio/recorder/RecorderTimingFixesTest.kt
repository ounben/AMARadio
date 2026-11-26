package com.ounben.amaradio.recorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path

internal class RecorderTimingFixesTest {

    // -------------------------------------------------------------------------
    // MP3 frame sync, exact duration calculation & Xing/Info header tests
    // -------------------------------------------------------------------------

    @Test
    fun mp3FindFirstValidFrameOffset_skipsLeadingGarbage() {
        val garbage = byteArrayOf(0x12, 0x34, 0x56, 0xFF.toByte(), 0x00, 0x78)
        val frame1 = createMp3FrameMpeg1L3(bitrateIdx = 9, sampleRateIdx = 0) // 128 kbps, 44100 Hz -> 417 bytes
        val frame2 = createMp3FrameMpeg1L3(bitrateIdx = 9, sampleRateIdx = 0)
        val combined = garbage + frame1 + frame2

        val offset = Mp3FrameHelper.findFirstValidMp3FrameOffset(combined, combined.size)
        assertEquals(garbage.size, offset)
    }

    @Test
    fun mp3AnalyzeAndBuildXingHeader_calculatesExactDurationAndSkipsStaleXing(@TempDir tempDir: Path) {
        val rawFile = File(tempDir.toFile(), "test.mp3")
        val baos = ByteArrayOutputStream()

        // Leading garbage
        baos.write(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        // Stale server Xing frame claiming wrong values
        val staleXing = createMp3FrameMpeg1L3(bitrateIdx = 9, sampleRateIdx = 0)
        "Xing".toByteArray(Charsets.ISO_8859_1).copyInto(staleXing, destinationOffset = 36)
        baos.write(staleXing)

        // 100 real MPEG1 Layer III frames at 44100 Hz (1152 samples/frame)
        // Exact duration = 100 * 1152 * 1000 / 44100 = 2612 ms
        val audioFrameCount = 100
        repeat(audioFrameCount) {
            baos.write(createMp3FrameMpeg1L3(bitrateIdx = 9, sampleRateIdx = 0))
        }
        rawFile.writeBytes(baos.toByteArray())

        val analysis = Mp3FrameHelper.analyzeMp3File(rawFile)
        assertEquals(audioFrameCount, analysis.totalAudioFrames)
        assertEquals((100L * 1152L * 1000L) / 44100L, analysis.durationMs)
        assertEquals(false, analysis.isVbr)

        val xingFrame = Mp3FrameHelper.buildXingHeaderFrame(analysis)
        assertNotNull(xingFrame)
        val tag = String(xingFrame!!, 36, 4, Charsets.ISO_8859_1)
        assertEquals("Info", tag)

        val outBytes = ByteArrayOutputStream()
        val written = Mp3FrameHelper.writeFinalizedMp3(rawFile, analysis, id3Tag = null, outputStream = outBytes)
        assertEquals(xingFrame.size + analysis.totalAudioBytes, written)
        assertEquals(written.toInt(), outBytes.size())
    }

    @Test
    fun mp3AnalyzeVbr_marksVbrAndCreatesXingTag(@TempDir tempDir: Path) {
        val rawFile = File(tempDir.toFile(), "vbr.mp3")
        val baos = ByteArrayOutputStream()
        repeat(20) { i ->
            val bitrateIdx = if (i % 2 == 0) 9 else 11 // 128 kbps and 192 kbps
            baos.write(createMp3FrameMpeg1L3(bitrateIdx = bitrateIdx, sampleRateIdx = 1)) // 48 kHz
        }
        rawFile.writeBytes(baos.toByteArray())

        val analysis = Mp3FrameHelper.analyzeMp3File(rawFile)
        assertEquals(20, analysis.totalAudioFrames)
        assertTrue(analysis.isVbr)
        assertEquals((20L * 1152L * 1000L) / 48000L, analysis.durationMs)

        val xingFrame = Mp3FrameHelper.buildXingHeaderFrame(analysis)
        assertNotNull(xingFrame)
        assertEquals("Xing", String(xingFrame!!, 36, 4, Charsets.ISO_8859_1))
    }

    // -------------------------------------------------------------------------
    // OGG page buffering across chunk boundaries & chained stream tests
    // -------------------------------------------------------------------------

    @Test
    fun oggStreamHandler_handlesPagesSplitAcrossSmallChunksAndNormalizesGranulePos() {
        val handler = OggStreamHandler()

        // BOS page with OpusHead (granule = 0)
        val opusHeadPayload = ByteArray(19)
        "OpusHead".toByteArray(Charsets.ISO_8859_1).copyInto(opusHeadPayload, 0)
        val bosPage = createOggPage(
            headerType = 0x02,
            granulePos = 0L,
            serialNumber = 1001,
            pageSequence = 0,
            payload = opusHeadPayload
        )

        // Live audio page 1 with huge live server granulepos (e.g. 500_000_000) and large payload > 4KB
        val bigPayload1 = ByteArray(5000) { 0x5A }
        val audioPage1 = createOggPage(
            headerType = 0x00,
            granulePos = 500_000_000L,
            serialNumber = 1001,
            pageSequence = 1,
            payload = bigPayload1
        )

        // Live audio page 2: +96000 samples (exactly 2000 ms later at 48kHz)
        val bigPayload2 = ByteArray(5000) { 0x3C }
        val audioPage2 = createOggPage(
            headerType = 0x00,
            granulePos = 500_096_000L,
            serialNumber = 1001,
            pageSequence = 2,
            payload = bigPayload2
        )

        val rawStream = byteArrayOf(0x00, 0x11, 0x22) + bosPage + audioPage1 + audioPage2

        // Feed in 1024-byte chunks (smaller than a single Ogg page!)
        val output = ByteArrayOutputStream()
        var pos = 0
        while (pos < rawStream.size) {
            val end = minOf(pos + 1024, rawStream.size)
            val chunk = rawStream.copyOfRange(pos, end)
            val processed = handler.processOggChunk(chunk, chunk.size)
            if (processed.isNotEmpty()) {
                output.write(processed)
            }
            pos = end
        }
        val tail = handler.flush()
        if (tail.isNotEmpty()) output.write(tail)

        val result = output.toByteArray()
        assertEquals(bosPage.size + audioPage1.size + audioPage2.size, result.size)

        // Verify granulepos of page 1 was zero-based to 0, and page 2 to 96000
        val page1Offset = bosPage.size
        val page2Offset = bosPage.size + audioPage1.size
        assertEquals(0L, readInt64LE(result, page1Offset + 6))
        assertEquals(96_000L, readInt64LE(result, page2Offset + 6))

        // Verify exact computed duration = 2000 ms
        assertEquals(2000L, handler.getComputedDurationMs())
    }

    @Test
    fun oggStreamHandler_handlesChainedStreamsAcrossSongChanges() {
        val handler = OggStreamHandler()
        val opusHead = ByteArray(19).also { "OpusHead".toByteArray(Charsets.ISO_8859_1).copyInto(it, 0) }

        // Chain 1 (serial 1): 3 seconds (144000 samples at 48kHz)
        val c1Bos = createOggPage(0x02, 0L, 1, 0, opusHead)
        val c1P1 = createOggPage(0x00, 10_000_000L, 1, 1, ByteArray(100))
        val c1P2 = createOggPage(0x04, 10_144_000L, 1, 2, ByteArray(100))

        // Chain 2 (serial 2): fresh song starting near 0, duration 4 seconds (192000 samples)
        val c2Bos = createOggPage(0x02, 0L, 2, 0, opusHead)
        val c2P1 = createOggPage(0x00, 48_000L, 2, 1, ByteArray(100))
        val c2P2 = createOggPage(0x04, 192_000L, 2, 2, ByteArray(100))

        val stream = c1Bos + c1P1 + c1P2 + c2Bos + c2P1 + c2P2
        val out = handler.processOggChunk(stream, stream.size)

        assertTrue(out.isNotEmpty())
        // Chain 1 (3000ms) + Chain 2 (4000ms) = 7000ms
        assertEquals(7000L, handler.getComputedDurationMs())
    }

    // -------------------------------------------------------------------------
    // HLS per-segment ID3 timestamp header stripping test
    // -------------------------------------------------------------------------

    @Test
    fun hlsResolver_stripsLeadingId3BeforeAdtsSync() {
        val id3PayloadSize = 20
        val id3 = ByteArray(10 + id3PayloadSize)
        id3[0] = 'I'.code.toByte()
        id3[1] = 'D'.code.toByte()
        id3[2] = '3'.code.toByte()
        id3[3] = 0x04
        id3[9] = id3PayloadSize.toByte()

        // ADTS AAC sync header: 0xFF 0xF1 ...
        val adts = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x02, 0x1F, 0xFC.toByte())
        val segment = id3 + adts

        val audioOffset = HlsResolver.findHlsSegmentAudioOffset(segment)
        assertEquals(id3.size, audioOffset)
    }

    // -------------------------------------------------------------------------
    // TV stream (HLS separate audio, lowest-bandwidth video+audio, MPEG-TS) tests
    // -------------------------------------------------------------------------

    @Test
    fun hlsResolver_prefersDedicatedAudioRenditionOnTvMasterPlaylist() {
        val master = listOf(
            "#EXTM3U",
            """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",DEFAULT=NO,URI="audio_en.m3u8"""",
            """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Deutsch",DEFAULT=YES,URI="audio_de.m3u8"""",
            """#EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="aac"""",
            "video_1080p.m3u8"
        )
        val selected = HlsResolver.selectBestMasterVariantUrl(master, java.net.URI.create("https://tv.example.com/live/master.m3u8"))
        assertEquals("https://tv.example.com/live/audio_de.m3u8", selected)
    }

    @Test
    fun hlsResolver_picksLowestBandwidthVariantWhenAllVariantsAreMuxedTvVideo() {
        val master = listOf(
            "#EXTM3U",
            """#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"""",
            "high.m3u8",
            """#EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=480x270,CODECS="avc1.42e01e,mp4a.40.2"""",
            "low.m3u8",
            """#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"""",
            "mid.m3u8"
        )
        val selected = HlsResolver.selectBestMasterVariantUrl(master, java.net.URI.create("https://tv.example.com/live/master.m3u8"))
        assertEquals("https://tv.example.com/live/low.m3u8", selected)
    }

    @Test
    fun formatParser_detectsMpegTsTvStreamEvenWhenDbCodecIsAac() {
        // 3 consecutive 188-byte MPEG-TS packets preceded by 7 garbage bytes
        val tsData = ByteArray(7 + 188 * 3)
        tsData[7] = 0x47
        tsData[7 + 188] = 0x47
        tsData[7 + 376] = 0x47

        assertEquals(7, FormatParser.findMpegTsSyncOffset(tsData))
        assertEquals(
            FormatParser.AudioFormat.MPEG_TS,
            FormatParser.detectFormat(codec = "AAC", contentType = "application/octet-stream", sampleBytes = tsData)
        )
    }

    @Test
    fun hlsResolver_dynamicallyDetectsHlsWithoutM3u8Url() {
        val body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg01.ts\n".toByteArray()
        assertTrue(HlsResolver.isHlsPlaylistResponse("https://tv.example.com/live/aac", "application/octet-stream", body))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createMp3FrameMpeg1L3(bitrateIdx: Int, sampleRateIdx: Int): ByteArray {
        val bitrates = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        val sampleRates = intArrayOf(44100, 48000, 32000, 0)
        val frameSize = (144 * bitrates[bitrateIdx] * 1000) / sampleRates[sampleRateIdx]
        val frame = ByteArray(frameSize)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte() // MPEG1 (11), Layer III (01), no CRC (1)
        frame[2] = ((bitrateIdx shl 4) or (sampleRateIdx shl 2)).toByte()
        frame[3] = 0x00 // Stereo
        return frame
    }

    private fun createOggPage(
        headerType: Int,
        granulePos: Long,
        serialNumber: Int,
        pageSequence: Int,
        payload: ByteArray
    ): ByteArray {
        val segmentSizes = mutableListOf<Int>()
        var remaining = payload.size
        while (remaining >= 255) {
            segmentSizes.add(255)
            remaining -= 255
        }
        segmentSizes.add(remaining)

        val page = ByteArray(27 + segmentSizes.size + payload.size)
        page[0] = 'O'.code.toByte()
        page[1] = 'g'.code.toByte()
        page[2] = 'g'.code.toByte()
        page[3] = 'S'.code.toByte()
        page[4] = 0 // version
        page[5] = headerType.toByte()

        for (i in 0 until 8) {
            page[6 + i] = ((granulePos ushr (i * 8)) and 0xFFL).toByte()
        }
        for (i in 0 until 4) {
            page[14 + i] = ((serialNumber ushr (i * 8)) and 0xFF).toByte()
        }
        for (i in 0 until 4) {
            page[18 + i] = ((pageSequence ushr (i * 8)) and 0xFF).toByte()
        }
        page[26] = segmentSizes.size.toByte()
        for (i in segmentSizes.indices) {
            page[27 + i] = segmentSizes[i].toByte()
        }
        payload.copyInto(page, destinationOffset = 27 + segmentSizes.size)
        return page
    }

    private fun readInt64LE(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (idx in 0 until 8) {
            value = value or ((data[offset + idx].toLong() and 0xFFL) shl (idx * 8))
        }
        return value
    }
}
