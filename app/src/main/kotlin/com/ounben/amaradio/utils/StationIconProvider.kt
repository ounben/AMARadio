package com.ounben.amaradio.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class StationIconProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.ounben.amaradio.stationicons"
        
        fun getIconUri(uuid: String, name: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(uuid)
                .appendQueryParameter("name", name)
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val uuid = uri.lastPathSegment ?: return null
        val name = uri.getQueryParameter("name") ?: "Radio"
        
        val cacheDir = context?.cacheDir ?: return null
        val iconDir = File(cacheDir, "station_icons").apply { if (!exists()) mkdirs() }
        val iconFile = File(iconDir, "$uuid.jpg")

        // 1. If cached, serve it
        if (iconFile.exists()) {
            return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // 2. Otherwise generate it on the fly
        try {
            val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(name, uuid)
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e("IconProvider", "Failed to generate icon for $uuid", e)
            return null
        }
    }

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, p1: ContentValues?): Uri? = null
    override fun delete(uri: Uri, p1: String?, p2: Array<out String>?): Int = 0
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0
}
