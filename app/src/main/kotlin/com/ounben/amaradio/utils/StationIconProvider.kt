package com.ounben.amaradio.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import coil.imageLoader
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
                // Pass through timestamp if present to invalidate external caches (Android Auto)
                val uri = remoteUrl.toUri()
                uri.getQueryParameter("t")?.let { builder.appendQueryParameter("t", it) }
            }
            
            return builder.build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val stationUuid = uri.lastPathSegment ?: return null
        val name = uri.getQueryParameter("name") ?: "Radio"
        val remoteUrl = uri.getQueryParameter("url")
        
        val ctx = context ?: return null
        
        // 1. Suche im permanenten Ordner station_icons
        val iconDir = File(ctx.filesDir, "station_icons")
        val iconFile = File(iconDir, "$stationUuid.jpg")

        // Nur zurückgeben, wenn die Datei existiert und KEIN kleiner Platzhalter ist
        if (iconFile.exists() && iconFile.length() > 2048) { // Echte Bilder sind meist > 2KB
            return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // 1b. Check if remoteUrl is a local file path
        if (!remoteUrl.isNullOrBlank() && remoteUrl.startsWith("file:/")) {
            try {
                val localFile = File(remoteUrl.toUri().path ?: "")
                if (localFile.exists()) {
                    return ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback: Suche in Coils internem Cache (wo die App-Liste ihre Bilder speichert)
        if (!remoteUrl.isNullOrBlank() && remoteUrl != "null") {
            try {
                // Wir schauen direkt in den Coil Disk Cache. Das ist pfeilschnell und ohne Netzwerk.
                val snapshot = ctx.imageLoader.diskCache?.get(remoteUrl)
                snapshot?.use {
                    val coilFile = it.data.toFile()
                    if (coilFile.exists()) {
                        if (!iconDir.exists()) iconDir.mkdirs()
                        // Kopiere das echte Bild in unseren station_icons Ordner
                        coilFile.copyTo(iconFile, overwrite = true)
                        return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }
            } catch (e: Exception) {
                Log.e("IconProvider", "Coil cache lookup failed for $stationUuid", e)
            }
        }

        // 3. Letzter Ausweg: Platzhalter als RAM-Stream (KEINE Datei schreiben!)
        // So verhindern wir, dass wir den Pfad für spätere echte Bilder mit einem Platzhalter-File blockieren.
        return openPipeHelper(uri, "image/jpeg", null, null) { output, _, _, _, _ ->
            try {
                val bitmap = StationPlaceholderUtils.createPlaceholderBitmap(name, stationUuid, size = 256)
                FileOutputStream(output.fileDescriptor).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            } catch (e: Exception) {
                Log.e("IconProvider", "Pipe transfer failed for $stationUuid", e)
            }
        }
    }

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, p1: ContentValues?): Uri? = null
    override fun delete(uri: Uri, p1: String?, p2: Array<out String>?): Int = 0
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0
}
