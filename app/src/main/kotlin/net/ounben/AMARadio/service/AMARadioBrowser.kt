package net.ounben.AMARadio.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants.*
import androidx.preference.PreferenceManager
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import net.ounben.AMARadio.R
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.station.DataRadioStation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AMARadioBrowser(private val AMARadioApp: AMARadioApp) {
    private val stationIdToStation = HashMap<String, DataRadioStation>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AMARadioApp)
        val extras = Bundle()
        extras.putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        if (sharedPref.getBoolean("load_icons", false) && sharedPref.getBoolean("icons_only_favorites_style", false)) {
            Log.d(TAG, "Setting grid style for playables")
            extras.putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        } else {
            Log.d(TAG, "Setting list style for playables")
            extras.putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, extras)
    }

    fun onLoadChildren(parentId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val resources = AMARadioApp.resources
        if (MEDIA_ID_ROOT == parentId) {
            result.sendResult(createBrowsableMediaItemsForRoot(resources))
            return
        }
        var stations: List<DataRadioStation>? = null
        when (parentId) {
            MEDIA_ID_MUSICS_FAVORITE -> stations = AMARadioApp.favouriteManager.getList()
            MEDIA_ID_MUSICS_HISTORY -> stations = AMARadioApp.historyManager.getList()
            MEDIA_ID_MUSICS_TOP -> {}
        }
        if (stations != null && stations.isNotEmpty()) {
            stationIdToStation.clear()
            for (station in stations) {
                stationIdToStation[station.StationUuid] = station
            }
            result.detach()
            retrieveStationsIconAndSendResult(result, stations)
        } else {
            result.sendResult(ArrayList())
        }
    }

    private fun retrieveStationsIconAndSendResult(result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>, stations: List<DataRadioStation>) {
        scope.launch {
            val stationIdToIcon = HashMap<String, Bitmap>()
            val resources = AMARadioApp.resources

            withContext(Dispatchers.IO) {
                for (station in stations) {
                    val url = if (!station.hasIcon()) Utils.resourceToUri(resources, R.mipmap.ic_elgato_launcher).toString() else station.IconUrl
                    val request = ImageRequest.Builder(AMARadioApp)
                        .data(url)
                        .size(128, 128)
                        .transformations(RoundedCornersTransformation(12f))
                        .build()
                    
                    val imageResult = AMARadioApp.imageLoader.execute(request)
                    if (imageResult is SuccessResult) {
                        val bitmap = (imageResult.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            stationIdToIcon[station.StationUuid] = bitmap
                        }
                    }
                }
            }

            val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
            for (station in stations) {
                var stationIcon = stationIdToIcon[station.StationUuid]
                if (stationIcon == null) stationIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_elgato_launcher)
                val extras = Bundle()
                extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, stationIcon)
                extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, stationIcon)
                val mediaItem = MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_ID_MUSICS_HISTORY + LEAF_SEPARATOR + station.StationUuid)
                    .setTitle(station.Name)
                    .setDescription("${station.Country} ${station.Country} ${station.TagsAll}")
                    .setExtras(extras)
                
                val iconUrl = station.IconUrl
                if (!iconUrl.isNullOrEmpty()) {
                    val url = if (iconUrl.startsWith("http:")) iconUrl.replace("http:", "https:") else iconUrl
                    mediaItem.setIconUri(Uri.parse(url))
                } else {
                    mediaItem.setIconUri(Utils.resourceToUri(resources, R.drawable.ic_radio_24dp))
                }
                mediaItems.add(MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            result.sendResult(mediaItems)
        }
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId]

    private fun createBrowsableMediaItemsForRoot(resources: Resources): List<MediaBrowserCompat.MediaItem> {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        mediaItems.add(MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setTitle(resources.getString(R.string.nav_item_starred))
            .setIconUri(Utils.resourceToUri(resources, R.drawable.ic_star_white_24))
            .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
        mediaItems.add(MediaBrowserCompat.MediaItem(MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setTitle(resources.getString(R.string.nav_item_history))
            .setIconUri(Utils.resourceToUri(resources, R.drawable.ic_star_white_24))
            .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
        return mediaItems
    }

    companion object {
        private const val TAG = "AMARadioBrowser"
        private const val MEDIA_ID_ROOT = "__ROOT__"
        private const val MEDIA_ID_MUSICS_FAVORITE = "__FAVORITE__"
        private const val MEDIA_ID_MUSICS_HISTORY = "__HISTORY__"
        private const val MEDIA_ID_MUSICS_TOP = "__TOP__"
        private const val LEAF_SEPARATOR = '|'
        private const val IMAGE_LOAD_TIMEOUT_MS = 2000

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            val separatorIdx = mediaId.indexOf(LEAF_SEPARATOR)
            return if (separatorIdx <= 0) mediaId else mediaId.substring(separatorIdx + 1)
        }
    }
}
