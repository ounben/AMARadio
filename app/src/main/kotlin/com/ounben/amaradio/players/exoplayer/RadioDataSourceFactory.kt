package com.ounben.amaradio.players.exoplayer

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import okhttp3.OkHttpClient

@UnstableApi
class RadioDataSourceFactory(
    private val httpClient: OkHttpClient,
    private val transferListener: TransferListener,
    private val dataSourceListener: IcyDataSource.IcyDataSourceListener,
    private val isHls: Boolean
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return IcyDataSource(httpClient, transferListener, dataSourceListener, isHls)
    }
}
