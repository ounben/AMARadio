package net.ounben.AMARadio.players.exoplayer

import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.TransferListener
import okhttp3.OkHttpClient

class RadioDataSourceFactory(
    private val httpClient: OkHttpClient,
    private val transferListener: TransferListener,
    private val dataSourceListener: IcyDataSource.IcyDataSourceListener,
    private val retryTimeout: Long,
    private val retryDelay: Long
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return IcyDataSource(httpClient, transferListener, dataSourceListener)
    }
}
