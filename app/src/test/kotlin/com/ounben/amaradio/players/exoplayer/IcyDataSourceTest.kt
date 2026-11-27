package com.ounben.amaradio.players.exoplayer

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream

@UnstableApi
internal class IcyDataSourceTest {

    private lateinit var transferredBytesWithoutMetadata: StringBuilder
    private lateinit var receivedLiveInfo: MutableList<StreamLiveInfo>
    private lateinit var icyDataSource: IcyDataSource

    @BeforeEach
    fun init() {
        transferredBytesWithoutMetadata = StringBuilder()
        receivedLiveInfo = mutableListOf()
        icyDataSource = IcyDataSource(
            httpClient = OkHttpClient(),
            transferListener = TestTransferListener(),
            dataSourceListener = TestDataSourceListener(transferredBytesWithoutMetadata, receivedLiveInfo),
            isHls = false
        )
    }

    @Test
    fun read_stripsMultipleIcyMetadataFramesAndEmitsStreamLiveInfo() {
        val metaInterval = 12 // e.g. "audio1audio2"
        val streamBytes = buildIcyStream(
            chunk("audio1audio2"),
            metadataBlock("StreamTitle='First Artist - First Track';"),
            chunk("audio3audio4"),
            metadataBlock("StreamTitle='Second Artist - Second Track';"),
            chunk("audio5")
        )

        icyDataSource.attachTestStream(ByteArrayInputStream(streamBytes), metaInterval)
        val audio = readAllAudio(icyDataSource, readChunkSize = 64)

        assertEquals("audio1audio2audio3audio4audio5", audio)
        assertEquals("audio1audio2audio3audio4audio5", transferredBytesWithoutMetadata.toString())
        assertEquals(2, receivedLiveInfo.size)
        assertEquals("First Artist", receivedLiveInfo[0].artist)
        assertEquals("First Track", receivedLiveInfo[0].track)
        assertEquals("Second Artist", receivedLiveInfo[1].artist)
        assertEquals("Second Track", receivedLiveInfo[1].track)
    }

    @Test
    fun read_handlesFragmentedNetworkReadsSplittingAudioAndMetadata() {
        val metaInterval = 18 // "audio7audio8audio9"
        val streamBytes = buildIcyStream(
            chunk("audio7audio8audio9"),
            metadataBlock("StreamTitle='Split Band - Split Song';"),
            chunk("audioAaudioBaudioC"),
            metadataBlock("StreamTitle='Next Band - Next Song';"),
            chunk("audioD")
        )

        // Simulate fragmented TCP packets returning at most 3 bytes per read()
        val fragmentedStream = MaxChunkInputStream(ByteArrayInputStream(streamBytes), maxBytesPerRead = 3)
        icyDataSource.attachTestStream(fragmentedStream, metaInterval)
        val audio = readAllAudio(icyDataSource, readChunkSize = 5)

        assertEquals("audio7audio8audio9audioAaudioBaudioCaudioD", audio)
        assertEquals("audio7audio8audio9audioAaudioBaudioCaudioD", transferredBytesWithoutMetadata.toString())
        assertEquals(2, receivedLiveInfo.size)
        assertEquals("Split Band", receivedLiveInfo[0].artist)
        assertEquals("Split Song", receivedLiveInfo[0].track)
        assertEquals("Next Band", receivedLiveInfo[1].artist)
        assertEquals("Next Song", receivedLiveInfo[1].track)
    }

    @Test
    fun read_skipsZeroLengthMetadataAndSuppressesDuplicateMetadata() {
        val metaInterval = 6
        val streamBytes = buildIcyStream(
            chunk("audio1"),
            byteArrayOf(0), // zero-length ICY metadata block (no change)
            chunk("audio2"),
            metadataBlock("StreamTitle='Same Artist - Same Song';"),
            chunk("audio3"),
            metadataBlock("StreamTitle='Same Artist - Same Song';"),
            chunk("audio4")
        )

        icyDataSource.attachTestStream(ByteArrayInputStream(streamBytes), metaInterval)
        val audio = readAllAudio(icyDataSource, readChunkSize = 16)

        assertEquals("audio1audio2audio3audio4", audio)
        assertEquals(1, receivedLiveInfo.size)
        assertEquals("Same Artist", receivedLiveInfo[0].artist)
        assertEquals("Same Song", receivedLiveInfo[0].track)
    }

    @Test
    fun read_passesAllBytesUnchangedWhenNoMetadataInterval() {
        val payload = "plain_audio_stream_without_icy_metadata"
        icyDataSource.attachTestStream(ByteArrayInputStream(payload.toByteArray()), metadataInterval = 0)
        val audio = readAllAudio(icyDataSource, readChunkSize = 11)

        assertEquals(payload, audio)
        assertEquals(payload, transferredBytesWithoutMetadata.toString())
        assertEquals(0, receivedLiveInfo.size)
    }

    private fun readAllAudio(source: IcyDataSource, readChunkSize: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(readChunkSize)
        while (true) {
            val read = source.read(buf, 0, buf.size)
            if (read == -1) break
            out.write(buf, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun chunk(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    private fun metadataBlock(metadata: String): ByteArray {
        val raw = metadata.toByteArray(Charsets.UTF_8)
        val blocks = (raw.size + 15) / 16
        val padded = ByteArray(1 + blocks * 16)
        padded[0] = blocks.toByte()
        System.arraycopy(raw, 0, padded, 1, raw.size)
        return padded
    }

    private fun buildIcyStream(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) {
            out.write(part)
        }
        return out.toByteArray()
    }

    private class MaxChunkInputStream(
        input: InputStream,
        private val maxBytesPerRead: Int
    ) : FilterInputStream(input) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return super.read(b, off, minOf(len, maxBytesPerRead))
        }
    }

    private class TestDataSourceListener(
        private val bytesOut: StringBuilder,
        private val liveInfoOut: MutableList<StreamLiveInfo>
    ) : IcyDataSource.IcyDataSourceListener {
        override fun onDataSourceConnected() {}
        override fun onDataSourceConnectionLost() {}
        override fun onDataSourceConnectionLostIrrecoverably() {}
        override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {}
        override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
            liveInfoOut.add(streamLiveInfo)
        }

        override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
            bytesOut.append(String(buffer, offset, length, Charsets.UTF_8))
        }
    }

    private class TestTransferListener : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }
}
