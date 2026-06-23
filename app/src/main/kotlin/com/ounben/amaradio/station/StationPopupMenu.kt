package com.ounben.amaradio.station

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayStationTask
import com.ounben.amaradio.players.selector.PlayerType

object StationPopupMenu {
    fun open(view: View, context: Context, activity: FragmentActivity, station: DataRadioStation): PopupMenu {
        val rootView = view.rootView
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        val playExternal = sharedPref.getBoolean("play_external", false)
        val gravity = if ((view.y + view.height) > (view.rootView.height / 2)) Gravity.TOP else Gravity.BOTTOM

        val popup = PopupMenu(context, view, gravity)
        popup.menuInflater.inflate(R.menu.station_popup_menu, popup.menu)

        // Tint icons based on theme
        val iconColor = Utils.themeAttributeToColor(android.R.attr.textColorPrimary, context, android.graphics.Color.BLACK)
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            item.icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon.mutate())
                DrawableCompat.setTint(wrapped, iconColor)
                item.icon = wrapped
            }
        }

        // Dynamic visibility
        popup.menu.findItem(R.id.action_play_in_amaradio).isVisible = playExternal
        popup.menu.findItem(R.id.action_play_in_external_player).isVisible = !playExternal
        popup.menu.findItem(R.id.action_create_shortcut).isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play_in_amaradio -> {
                    StationActions.playInAMARadio(context, station)
                    true
                }
                R.id.action_play_in_external_player -> {
                    Utils.playAndWarnIfMetered(
                        context.applicationContext as AMARadioApp,
                        station,
                        PlayerType.EXTERNAL,
                    ) {
                        PlayStationTask.playExternal(station, context).execute()
                    }
                    true
                }
                R.id.action_visit_homepage -> {
                    StationActions.openStationHomeUrl(activity, station)
                    true
                }
                R.id.action_share -> {
                    StationActions.share(context, station)
                    true
                }
                R.id.action_create_shortcut -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        station.prepareShortcut(context) { shortcut ->
                            val sm = context.getSystemService(ShortcutManager::class.java)
                            if ((sm != null) && sm.isRequestPinShortcutSupported) {
                                sm.requestPinShortcut(shortcut, null)
                            }
                        }
                    }
                    true
                }
                R.id.action_delete -> {
                    StationActions.removeFromFavourites(context, rootView, station)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return popup
    }
}
