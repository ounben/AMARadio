package net.ounben.AMARadio.players.exoplayer

import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class IcyDataSourceTest {

    @BeforeEach
    fun init() {
        transferredBytesWithoutMetadata = StringBuilder()
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleMultipleMetadataFrames() {
        val buffer = "OFFSETaudio1audio2\u0001METADATAMETADATAaudio3audio4audio5\u0002METADATAMETADATAMETADATAMETADATAaudio6".toByteArray()
        val offset = 6
        icyDataSource!!.remainingUntilMetadata = "audioN".length * 2
        icyDataSource!!.shoutcastInfo!!.metadataOffset = "audioN".length * 3
        icyDataSource!!.metadataBytesToSkip = 0
        icyDataSource!!.sendToDataSourceListenersWithoutMetadata(buffer, offset, buffer.size - offset)
        assertEquals("audio1audio2audio3audio4audio5audio6", transferredBytesWithoutMetadata.toString())
        assertEquals(icyDataSource!!.shoutcastInfo!!.metadataOffset - "audioN".length, icyDataSource!!.remainingUntilMetadata)
        assertEquals(0, icyDataSource!!.metadataBytesToSkip)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleIncompleteMetaDataFrames() {
        val buffer = "OFFSETaudio7audio8\u0001METADATAMETADATAaudio9audioAaudioB\u0001META".toByteArray()
        val offset = 6
        icyDataSource!!.remainingUntilMetadata = "audioN".length * 2
        icyDataSource!!.shoutcastInfo!!.metadataOffset = "audioN".length * 3
        icyDataSource!!.metadataBytesToSkip = 0
        icyDataSource!!.sendToDataSourceListenersWithoutMetadata(buffer, offset, buffer.size - offset)
        assertEquals("audio7audio8audio9audioAaudioB", transferredBytesWithoutMetadata.toString())
        assertEquals(16 - "META".length, icyDataSource!!.metadataBytesToSkip)
        assertEquals(icyDataSource!!.shoutcastInfo!!.metadataOffset + 16 - "META".length, icyDataSource!!.remainingUntilMetadata)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleInterruptedMetadata() {
        sendToDataSourceListenersWithoutMetadata_canHandleIncompleteMetaDataFrames()
        val buffer = "DATAMETADATAaudioCaudioDaudioE\u0001METADATAMETADATAaudioF".toByteArray()
        icyDataSource!!.sendToDataSourceListenersWithoutMetadata(buffer, 0, buffer.size)
        assertEquals("audio7audio8audio9audioAaudioBaudioCaudioDaudioEaudioF", transferredBytesWithoutMetadata.toString())
        assertEquals(0, icyDataSource!!.metadataBytesToSkip)
        assertEquals("audioN".length * 2, icyDataSource!!.remainingUntilMetadata)
    }

    @Test
    fun sendToDataSourceListenersWithoutMetadata_canHandleInterruptedAudioData() {
        sendToDataSourceListenersWithoutMetadata_canHandleMultipleMetadataFrames()
        val buffer = "audio7audio8".toByteArray()
        icyDataSource!!.sendToDataSourceListenersWithoutMetadata(buffer, 0, buffer.size)
        assertEquals("audio1audio2audio3audio4audio5audio6audio7audio8", transferredBytesWithoutMetadata.toString())
        assertEquals(0, icyDataSource!!.metadataBytesToSkip)
        assertEquals(0, icyDataSource!!.metadataBytesToSkip)
    }

    private class TestDataSourceListener : IcyDataSource.IcyDataSourceListener {
        override fun onDataSourceConnected() {}
        override fun onDataSourceConnectionLost() {}
        override fun onDataSourceConnectionLostIrrecoverably() {}
        override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {}
        override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {}
        override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
            transferredBytesWithoutMetadata.append(String(buffer, offset, length))
        }
    }

    private class TestTransferListener : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }

    companion object {
        private var icyDataSource: IcyDataSource? = null
        private lateinit var transferredBytesWithoutMetadata: StringBuilder

        @JvmStatic
        @BeforeAll
        fun setup() {
            icyDataSource = IcyDataSource(OkHttpClient(), TestTransferListener(), TestDataSourceListener())
            icyDataSource!!.shoutcastInfo = ShoutcastInfo()
            icyDataSource!!.shoutcastInfo!!.metadataOffset = 16000
        }
    }
}
