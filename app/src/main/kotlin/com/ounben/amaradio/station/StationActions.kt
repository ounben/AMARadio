package com.ounben.amaradio.station

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.ActivityMain
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.selector.PlayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object StationActions {
    private const val TAG = "StationActions"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @JvmStatic
    fun openStationHomeUrl(activity: Context, station: DataRadioStation) {
        if (!TextUtils.isEmpty(station.HomePageUrl)) {
            val stationUrl = station.HomePageUrl.toUri()
            val newIntent = Intent(Intent.ACTION_VIEW, stationUrl)
            Utils.safeStartActivity(activity, newIntent, R.string.error_no_browser)
        }
    }

    private fun retrieveAndCopyStreamUrlToClipboard(context: Context, station: DataRadioStation) {
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val app = ctx.applicationContext as AMARadioApp
                val httpClient = app.httpClient
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }

            val ctx = contextRef.get() ?: return@launch
            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))

            if (result != null) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    val clip = ClipData.newPlainText("Stream Url", result)
                    clipboard.setPrimaryClip(clip)
                    (ctx as? Activity)?.let { Utils.showModernToast(it, R.string.notify_stream_url_copied) }
                } else {
                    Log.e(TAG, "Clipboard is NULL!")
                }
            } else {
                (ctx as? Activity)?.let { Utils.showModernToast(it, R.string.error_station_load) }
            }
        }
    }

    @JvmStatic
    fun markAsFavourite(context: Context, station: DataRadioStation) {
        val app = context.applicationContext as AMARadioApp
        app.favouriteManager.add(station)
        (context as? Activity)?.let { Utils.showModernToast(it, R.string.notify_starred) }
    }

    @JvmStatic
    fun removeFromFavourites(context: Context, station: DataRadioStation) {
        val app = context.applicationContext as AMARadioApp
        val favouriteManager = app.favouriteManager
        favouriteManager.remove(station.StationUuid)
        // Undo logic can be implemented via a generic Snackbar manager in Compose if needed
    }

    @JvmStatic
    fun share(context: Context, station: DataRadioStation) {
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val app = ctx.applicationContext as AMARadioApp
                val httpClient = app.httpClient
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }

            val ctx = contextRef.get() ?: return@launch
            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))

            if (result != null) {
                val share = Intent(Intent.ACTION_SEND)
                share.type = "text/plain"
                share.putExtra(Intent.EXTRA_SUBJECT, station.Name)
                share.putExtra(Intent.EXTRA_TEXT, result)
                val title = ctx.resources.getString(R.string.share_action)
                val chooser = Intent.createChooser(share, title)
                ctx.startActivity(chooser)
            } else {
                (ctx as? Activity)?.let { Utils.showModernToast(it, R.string.error_station_load) }
            }
        }
    }

    @JvmStatic
    fun playInAMARadio(context: Context, station: DataRadioStation) {
        Utils.playAndWarnIfMetered(context, station, PlayerType.AMARadio) {
            Utils.play(station)
        }
    }
}
