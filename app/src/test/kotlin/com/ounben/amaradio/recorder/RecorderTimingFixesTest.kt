package com.ounben.amaradio.recorder

import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
internal class RecorderTimingFixesTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun configureMedia3ForJvmUnitTests() {
            // Media3 1.11.1 ParsableByteArray.isRunningInTest() calls Ascii.equalsIgnoreCase("robolectric", Build.FINGERPRINT),
            // which throws NPE in plain JVM unit tests where android.os.Build.FINGERPRINT is null.
            // Pre-setting the override avoids loading IsRunningInTestHolder while enforcing bounds checks.
            val field = ParsableByteArray::class.java.getDeclaredField("shouldEnforceLimitOnLegacyMethods")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(null) as AtomicReference<Boolean?>).set(true)

            // Media3's default Logger calls android.util.Log.getStackTraceString(), which returns null
            // under returnDefaultValues = true on plain JVM tests.
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        }
    }

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

    @Test
    fun recordingPlayer_adaptiveSeekStepScalesWithDuration() {
        assertEquals(5_000L, calculateAdaptiveSeekStepMs(90_000L))          // < 2 min -> 5s
        assertEquals(15_000L, calculateAdaptiveSeekStepMs(2L * 60_000L))  // 2 min -> 15s
        assertEquals(15_000L, calculateAdaptiveSeekStepMs(30L * 60_000L)) // 30 min -> 15s
        assertEquals(60_000L, calculateAdaptiveSeekStepMs(45L * 60_000L)) // 45 min -> 1m
        assertEquals(60_000L, calculateAdaptiveSeekStepMs(120L * 60_000L))// 2 h -> 1m
        assertEquals(300_000L, calculateAdaptiveSeekStepMs(150L * 60_000L))// > 2 h -> 5m
    }

    @Test
    fun recordingPlayer_playbackSpeedCyclesAndFormatsCleanly() {
        assertEquals("1x", formatPlaybackSpeed(1.0f))
        assertEquals("1.25x", formatPlaybackSpeed(1.25f))
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
        assertEquals("2x", formatPlaybackSpeed(2.0f))
        assertEquals("2.5x", formatPlaybackSpeed(2.5f))
        assertEquals("3x", formatPlaybackSpeed(3.0f))

        assertEquals(1.25f, nextPlaybackSpeed(1.0f))
        assertEquals(1.5f, nextPlaybackSpeed(1.25f))
        assertEquals(2.0f, nextPlaybackSpeed(1.5f))
        assertEquals(2.5f, nextPlaybackSpeed(2.0f))
        assertEquals(3.0f, nextPlaybackSpeed(2.5f))
        assertEquals(1.0f, nextPlaybackSpeed(3.0f))
    }

    // -------------------------------------------------------------------------
    // MPEG-TS multi-frame PES demuxing tests (BBC Radio / HLS AAC regression)
    // -------------------------------------------------------------------------

    @Test
    fun aacMuxer_demuxMpegTsSplitsMultiFramePesPacketsIntoIndividualAacFrames(@TempDir tempDir: Path) {
        val tsFile = File(tempDir.toFile(), "bbc_hls_multiframe.ts")

        // Build 2 PES packets where EACH PES packet bundles 10 ADTS AAC frames (48 kHz, stereo, AAC-LC)
        // — reproducing BBC Radio 2 HLS where Android's MediaExtractor merged all frames of a PES
        // packet into one broken ~3 KB sample.
        val framesPerPes = 10
        val pesCount = 2
        val rawPayloadSize = 64
        val frameDuration90k = 1920L // 1024 samples @ 48 kHz = 21.333... ms = 1920 ticks @ 90 kHz

        val tsStream = ByteArrayOutputStream()
        tsStream.write(createPatPacket(pmtPid = 0x1000))
        tsStream.write(createPmtPacket(pmtPid = 0x1000, videoPid = 0x0101, audioPid = 0x0100))

        var cc = 0
        for (pesIdx in 0 until pesCount) {
            val pesPayload = ByteArrayOutputStream()
            for (f in 0 until framesPerPes) {
                val marker = ((pesIdx * framesPerPes) + f + 1).toByte()
                val rawAacPayload = ByteArray(rawPayloadSize) { marker }
                pesPayload.write(createAdtsAacFrame48kStereo(rawAacPayload))
            }
            val pts90k = 90_000L + pesIdx * framesPerPes * frameDuration90k
            val pesBytes = wrapInAudioPesPacket(pts90k, pesPayload.toByteArray())
            cc = writePesAsTsPackets(tsStream, pid = 0x0100, pesBytes = pesBytes, startCc = cc)
        }
        tsFile.writeBytes(tsStream.toByteArray())

        assertEquals(0, AacMuxer.detectMpegTsSyncOffset(tsFile))

        val probed = AacMuxer.probeBestMpegTsAudioTrack(tsFile)
        assertNotNull(probed)
        assertEquals("audio/mp4a-latm", probed!!.mime)
        assertEquals(48000, probed.format.sampleRate)
        assertEquals(2, probed.format.channelCount)
        assertEquals(1, probed.format.initializationData.size)
        // 48 kHz stereo AAC-LC AudioSpecificConfig = 0x11 0x90
        assertEquals(0x11.toByte(), probed.format.initializationData[0][0])
        assertEquals(0x90.toByte(), probed.format.initializationData[0][1])

        val extractedSizes = mutableListOf<Int>()
        val extractedPts = mutableListOf<Long>()
        val extractedMarkers = mutableListOf<Byte>()
        val count = AacMuxer.demuxMpegTsAudioTrack(tsFile, 0L, probed.trackId) { data, size, ptsUs, _ ->
            extractedSizes.add(size)
            extractedPts.add(ptsUs)
            extractedMarkers.add(data[0])
        }

        // Must emit ALL 20 individual AAC frames (NOT 2 merged multi-frame PES blobs!)
        assertEquals(framesPerPes * pesCount, count)
        assertTrue(extractedSizes.all { it == rawPayloadSize })
        for (i in 0 until count) {
            assertEquals((i + 1).toByte(), extractedMarkers[i])
            if (i > 0) {
                val deltaUs = extractedPts[i] - extractedPts[i - 1]
                // Each 48 kHz AAC frame is 21333..21334 us
                assertTrue(deltaUs in 21_330L..21_340L, "Unexpected PTS delta at frame $i: $deltaUs us")
            }
        }
    }

    // -------------------------------------------------------------------------
    // TagEmbedder M4A 64-bit mdat + stco chunk offset shifting tests (BBC CWR fix)
    // -------------------------------------------------------------------------

    @Test
    fun tagEmbedder_fastStartM4aWith64BitMdat_shiftsStcoOffsetsToPreserveAudioData() {
        val ftyp = mp4Box("ftyp", ByteArray(16) { 0x01 })
        val audioPayload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x42, 0x43)

        // Build placeholder moov to calculate exact initial mdat audio offset
        fun buildFastStartMoov(chunkOffset: Int): ByteArray {
            val stcoPayload = ByteArray(12).apply {
                // version(0) + flags(0) = 4 zero bytes, entry_count = 1, chunk_offset
                putBe32(this, 4, 1)
                putBe32(this, 8, chunkOffset)
            }
            val stco = mp4Box("stco", stcoPayload)
            val stbl = mp4Box("stbl", stco)
            val minf = mp4Box("minf", stbl)
            val mdia = mp4Box("mdia", minf)
            val trak = mp4Box("trak", mdia)
            return mp4Box("moov", trak)
        }

        val sampleMoov = buildFastStartMoov(0)
        // 64-bit mdat header is 16 bytes (size=1 + "mdat" + uint64 largesize)
        val initialAudioOffset = ftyp.size + sampleMoov.size + 16
        val moov = buildFastStartMoov(initialAudioOffset)
        val mdat64 = mp4Box64("mdat", audioPayload)
        val originalM4a = ftyp + moov + mdat64

        val fakeJpeg = ByteArray(512) { 0x77 }
        val updated = TagEmbedder.embedM4aMetadataBytes(originalM4a, fakeJpeg, "BBC CWR")
        assertNotNull(updated)
        val out = updated!!
        assertTrue(out.size > originalM4a.size)

        // Locate updated stco chunk offset inside moov and verify it STILL points to exact audioPayload!
        val stcoIdx = findAscii(out, "stco")
        assertTrue(stcoIdx >= 4)
        val newChunkOffset = readBe32(out, stcoIdx + 12)
        assertEquals(initialAudioOffset + (out.size - originalM4a.size), newChunkOffset)
        for (i in audioPayload.indices) {
            assertEquals(audioPayload[i], out[newChunkOffset + i])
        }
    }

    @Test
    fun tagEmbedder_nonStreamableM4aWith64BitMdat_embedsMetadataWithoutShiftingStco() {
        val ftyp = mp4Box("ftyp", ByteArray(16) { 0x01 })
        val audioPayload = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val mdat64 = mp4Box64("mdat", audioPayload)
        val initialAudioOffset = ftyp.size + 16

        val stcoPayload = ByteArray(12).apply {
            putBe32(this, 4, 1)
            putBe32(this, 8, initialAudioOffset)
        }
        val moov = mp4Box("moov", mp4Box("trak", mp4Box("mdia", mp4Box("minf", mp4Box("stbl", mp4Box("stco", stcoPayload))))))
        val originalM4a = ftyp + mdat64 + moov

        val fakeJpeg = ByteArray(256) { 0x55 }
        val updated = TagEmbedder.embedM4aMetadataBytes(originalM4a, fakeJpeg, "BBC Radio 2")
        assertNotNull(updated)
        val out = updated!!
        assertTrue(out.size > originalM4a.size)

        val stcoIdx = findAscii(out, "stco")
        assertTrue(stcoIdx >= 4)
        val chunkOffsetAfter = readBe32(out, stcoIdx + 12)
        assertEquals(initialAudioOffset, chunkOffsetAfter)
        for (i in audioPayload.indices) {
            assertEquals(audioPayload[i], out[chunkOffsetAfter + i])
        }
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val box = ByteArray(8 + payload.size)
        putBe32(box, 0, box.size)
        type.toByteArray(Charsets.ISO_8859_1).copyInto(box, destinationOffset = 4)
        payload.copyInto(box, destinationOffset = 8)
        return box
    }

    private fun mp4Box64(type: String, payload: ByteArray): ByteArray {
        val total = 16L + payload.size
        val box = ByteArray(total.toInt())
        putBe32(box, 0, 1) // 64-bit largesize indicator
        type.toByteArray(Charsets.ISO_8859_1).copyInto(box, destinationOffset = 4)
        for (i in 0 until 8) {
            box[8 + i] = ((total ushr ((7 - i) * 8)) and 0xFFL).toByte()
        }
        payload.copyInto(box, destinationOffset = 16)
        return box
    }

    private fun putBe32(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value shr 24).toByte()
        arr[offset + 1] = (value shr 16).toByte()
        arr[offset + 2] = (value shr 8).toByte()
        arr[offset + 3] = value.toByte()
    }

    private fun readBe32(arr: ByteArray, offset: Int): Int {
        return ((arr[offset].toInt() and 0xFF) shl 24) or
            ((arr[offset + 1].toInt() and 0xFF) shl 16) or
            ((arr[offset + 2].toInt() and 0xFF) shl 8) or
            (arr[offset + 3].toInt() and 0xFF)
    }

    private fun findAscii(arr: ByteArray, needle: String): Int {
        val target = needle.toByteArray(Charsets.ISO_8859_1)
        for (i in 0..(arr.size - target.size)) {
            var match = true
            for (j in target.indices) {
                if (arr[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createAdtsAacFrame48kStereo(rawPayload: ByteArray): ByteArray {
        val frameLength = 7 + rawPayload.size
        val frame = ByteArray(frameLength)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF1.toByte() // MPEG-4, Layer 0, protection_absent=1 (no CRC)
        frame[2] = 0x4C.toByte() // profile=1 (AAC-LC), freqIdx=3 (48kHz), chCfg high bit=0
        frame[3] = ((2 shl 6) or ((frameLength shr 11) and 0x03)).toByte() // chCfg=2 (stereo)
        frame[4] = ((frameLength shr 3) and 0xFF).toByte()
        frame[5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
        frame[6] = 0xFC.toByte()
        rawPayload.copyInto(frame, destinationOffset = 7)
        return frame
    }

    private fun wrapInAudioPesPacket(pts90k: Long, payload: ByteArray): ByteArray {
        val header = ByteArray(14)
        header[0] = 0x00
        header[1] = 0x00
        header[2] = 0x01
        header[3] = 0xC0.toByte() // Audio stream 0
        val pesLen = 8 + payload.size
        header[4] = if (pesLen <= 0xFFFF) ((pesLen shr 8) and 0xFF).toByte() else 0
        header[5] = if (pesLen <= 0xFFFF) (pesLen and 0xFF).toByte() else 0
        header[6] = 0x80.toByte()
        header[7] = 0x80.toByte() // PTS present
        header[8] = 0x05 // PTS header data length
        header[9] = (0x20 or (((pts90k ushr 30) and 0x07L).toInt() shl 1) or 0x01).toByte()
        header[10] = ((pts90k ushr 22) and 0xFFL).toByte()
        header[11] = ((((pts90k ushr 15) and 0x7FL).toInt() shl 1) or 0x01).toByte()
        header[12] = ((pts90k ushr 7) and 0xFFL).toByte()
        header[13] = (((pts90k and 0x7FL).toInt() shl 1) or 0x01).toByte()
        return header + payload
    }

    private fun writePesAsTsPackets(out: ByteArrayOutputStream, pid: Int, pesBytes: ByteArray, startCc: Int): Int {
        var offset = 0
        var cc = startCc
        var first = true
        while (offset < pesBytes.size) {
            val packet = ByteArray(188) { 0xFF.toByte() }
            packet[0] = 0x47
            packet[1] = ((if (first) 0x40 else 0x00) or ((pid shr 8) and 0x1F)).toByte()
            packet[2] = (pid and 0xFF).toByte()
            val remaining = pesBytes.size - offset
            if (remaining >= 184) {
                packet[3] = (0x10 or (cc and 0x0F)).toByte()
                pesBytes.copyInto(packet, destinationOffset = 4, startIndex = offset, endIndex = offset + 184)
                offset += 184
            } else {
                // Adaptation field stuffing so payload fills the exact end of the 188-byte packet
                val stuffingTotal = 184 - remaining
                packet[3] = (0x30 or (cc and 0x0F)).toByte()
                packet[4] = (stuffingTotal - 1).toByte()
                if (stuffingTotal >= 2) {
                    packet[5] = 0x00
                }
                pesBytes.copyInto(packet, destinationOffset = 4 + stuffingTotal, startIndex = offset, endIndex = pesBytes.size)
                offset = pesBytes.size
            }
            out.write(packet)
            cc = (cc + 1) and 0x0F
            first = false
        }
        return cc
    }

    private fun createPatPacket(pmtPid: Int): ByteArray {
        val section = byteArrayOf(
            0x00, 0xB0.toByte(), 0x0D, // table_id=0, section_length=13
            0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, // tsid=1, ver=0, cur=1
            0x00, 0x01, (0xE0 or ((pmtPid shr 8) and 0x1F)).toByte(), (pmtPid and 0xFF).toByte()
        )
        return buildPsiTsPacket(pid = 0x0000, sectionWithoutCrc = section)
    }

    private fun createPmtPacket(pmtPid: Int, videoPid: Int, audioPid: Int): ByteArray {
        val section = byteArrayOf(
            0x02, 0xB0.toByte(), 0x17, // table_id=2, section_length=23
            0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or ((audioPid shr 8) and 0x1F)).toByte(), (audioPid and 0xFF).toByte(), // PCR PID
            0xF0.toByte(), 0x00, // program_info_length=0
            // Video track (H.264) - should be discarded
            0x1B, (0xE0 or ((videoPid shr 8) and 0x1F)).toByte(), (videoPid and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            // Audio track (ADTS AAC) - should be selected
            0x0F, (0xE0 or ((audioPid shr 8) and 0x1F)).toByte(), (audioPid and 0xFF).toByte(), 0xF0.toByte(), 0x00
        )
        return buildPsiTsPacket(pid = pmtPid, sectionWithoutCrc = section)
    }

    private fun buildPsiTsPacket(pid: Int, sectionWithoutCrc: ByteArray): ByteArray {
        val crc = mpeg2Crc32(sectionWithoutCrc)
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = (0x40 or ((pid shr 8) and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = 0x10
        packet[4] = 0x00 // pointer_field
        sectionWithoutCrc.copyInto(packet, destinationOffset = 5)
        val crcOff = 5 + sectionWithoutCrc.size
        packet[crcOff] = ((crc ushr 24) and 0xFF).toByte()
        packet[crcOff + 1] = ((crc ushr 16) and 0xFF).toByte()
        packet[crcOff + 2] = ((crc ushr 8) and 0xFF).toByte()
        packet[crcOff + 3] = (crc and 0xFF).toByte()
        return packet
    }

    private fun mpeg2Crc32(data: ByteArray): Int {
        var crc = -1
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if ((crc and Int.MIN_VALUE) != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
            }
        }
        return crc
    }

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
