package net.ounben.AMARadio.station

import android.app.Activity
import android.content.*
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import net.ounben.AMARadio.*
import net.ounben.AMARadio.alarm.TimePickerFragment
import net.ounben.AMARadio.players.selector.PlayerType
import net.ounben.AMARadio.views.ItemListDialog
import java.lang.ref.WeakReference

object StationActions {
    private const val TAG = "StationActions"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @JvmStatic
    fun setAsAlarm(activity: FragmentActivity, station: DataRadioStation) {
        val AMARadioApp = activity.applicationContext as AMARadioApp
        val newFragment = TimePickerFragment()
        newFragment.setCallback { _, hourOfDay, minute ->
            Log.i(TAG, "Alarm time picked $hourOfDay:$minute")
            AMARadioApp.alarmManager.add(station, hourOfDay, minute)
        }
        newFragment.show(activity.supportFragmentManager, "timePicker")
    }

    @JvmStatic
    fun showWebLinks(activity: FragmentActivity, station: DataRadioStation) {
        ItemListDialog.create(activity, intArrayOf(
            R.string.action_station_visit_website, R.string.action_station_copy_stream_url, R.string.action_station_share
        )) { resourceId ->
            when (resourceId) {
                R.string.action_station_visit_website -> openStationHomeUrl(activity, station)
                R.string.action_station_copy_stream_url -> retrieveAndCopyStreamUrlToClipboard(activity, station)
                R.string.action_station_share -> share(activity, station)
            }
        }.show()
    }

    @JvmStatic
    fun openStationHomeUrl(activity: FragmentActivity, station: DataRadioStation) {
        if (!TextUtils.isEmpty(station.HomePageUrl)) {
            val stationUrl = Uri.parse(station.HomePageUrl)
            if (stationUrl != null) {
                val newIntent = Intent(Intent.ACTION_VIEW, stationUrl)
                activity.startActivity(newIntent)
            }
        }
    }

    private fun retrieveAndCopyStreamUrlToClipboard(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val AMARadioApp = ctx.applicationContext as AMARadioApp
                val httpClient = AMARadioApp.httpClient
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }

            val ctx = contextRef.get() ?: return@launch
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))

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
        val AMARadioApp = context.applicationContext as AMARadioApp
        AMARadioApp.favouriteManager.add(station)
        (context as? Activity)?.let { Utils.showModernToast(it, R.string.notify_starred) }
        vote(context, station)
    }

    @JvmStatic
    fun removeFromFavourites(context: Context, view: View?, station: DataRadioStation) {
        val AMARadioApp = context.applicationContext as AMARadioApp
        val favouriteManager = AMARadioApp.favouriteManager
        val removedIdx = favouriteManager.remove(station.StationUuid)

        if (view != null) {
            val viewAttachTo = view.rootView.findViewById<View>(R.id.fragment_player_small)
            val snackbar = Snackbar.make(viewAttachTo, R.string.notify_station_removed_from_list, 6000)
            snackbar.anchorView = viewAttachTo
            snackbar.setAction(R.string.action_station_removed_from_list_undo) {
                favouriteManager.restore(station, removedIdx)
            }
            snackbar.show()
        }
    }

    @JvmStatic
    fun share(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val AMARadioApp = ctx.applicationContext as AMARadioApp
                val httpClient = AMARadioApp.httpClient
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }

            val ctx = contextRef.get() ?: return@launch
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))

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
            Utils.play(context, station)
        }
    }

    private fun vote(context: Context, station: DataRadioStation) {
        val contextRef = WeakReference(context)
        scope.launch {
            withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext
                val AMARadioApp = ctx.applicationContext as AMARadioApp
                val httpClient = AMARadioApp.httpClient
                Utils.downloadFeedRelative(httpClient, ctx, "json/vote/" + station.StationUuid, true, null)
            }
        }
    }
}
