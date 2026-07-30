package com.ounben.amaradio.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.glance.ImageProvider
import com.ounben.amaradio.utils.StationPlaceholderUtils
import java.io.File

object WidgetImageLoader {
    private const val TAG = "WidgetImage"

    fun getStationImage(context: Context, stationUuid: String, stationName: String): ImageProvider {
        val iconDir = File(context.cacheDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")
        
        if (iconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    return ImageProvider(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode widget icon for $stationUuid", e)
            }
        }
        
        // Fallback: Generate placeholder bitmap
        val placeholder = StationPlaceholderUtils.createPlaceholderBitmap(stationName, stationUuid)
        return ImageProvider(placeholder)
    }
}
