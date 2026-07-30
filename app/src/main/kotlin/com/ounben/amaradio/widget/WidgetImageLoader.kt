package com.ounben.amaradio.widget

import android.content.Context
import android.net.Uri
import androidx.glance.ImageProvider
import com.ounben.amaradio.utils.StationIconProvider

object WidgetImageLoader {
    /**
     * Returns an ImageProvider using the App's internal StationIconProvider (ContentProvider).
     * This handles Caching, Downloading and Placeholders in a robust way.
     */
    fun getStationImage(context: Context, stationUuid: String, stationName: String, remoteUrl: String? = null): ImageProvider {
        val uri = StationIconProvider.getIconUri(stationUuid, stationName, remoteUrl)
        // Explicitly use the factory function for Uris from glance-appwidget
        return androidx.glance.appwidget.ImageProvider(uri)
    }
}
