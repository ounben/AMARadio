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

class StationIconProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.ounben.amaradio.stationicons"
        
        fun getIconUri(uuid: String, name: String, remoteUrl: String? = null): Uri {
            val builder = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(uuid)
                .appendQueryParameter("name", name)
            
            if (!remoteUrl.isNullOrBlank() && remoteUrl != "null") {
                builder.appendQueryParameter("url", remoteUrl)
            }
            
            return builder.build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val uuid = uri.lastPathSegment ?: return null
        val name = uri.getQueryParameter("name") ?: "Radio"
        
        val cacheDir = context?.cacheDir ?: return null
        val iconDir = File(cacheDir, "station_icons").apply { if (!exists()) mkdirs() }
        val iconFile = File(iconDir, "$uuid.jpg")

        // 1. If we have the real logo in cache, serve it immediately.
        if (iconFile.exists() && iconFile.length() > 0) {
            return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // 2. No Network in Provider: Synchronously generate placeholder to prevent IPC timeouts.
        // Download of real icons is handled asynchronously by PlayerService.
        return try {
            val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(name, uuid, size = 256)
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e("IconProvider", "Failed to serve icon for $uuid", e)
            null
        }
    }

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, p1: ContentValues?): Uri? = null
    override fun delete(uri: Uri, p1: String?, p2: Array<out String>?): Int = 0
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0
}
