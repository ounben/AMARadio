package net.ounben.AMARadio.station

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.github.zawadz88.materialpopupmenu.MaterialPopupMenu
import com.github.zawadz88.materialpopupmenu.popupMenu
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.sizeDp
import net.ounben.AMARadio.R
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.players.PlayStationTask
import net.ounben.AMARadio.players.selector.PlayerType

object StationPopupMenu {
    fun open(view: View, context: Context, activity: FragmentActivity, station: DataRadioStation, itemAdapterStation: ItemAdapterStation): MaterialPopupMenu {
        val rootView = view.rootView
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        val play_external = sharedPref.getBoolean("play_external", false)
        val gravity = if (view.y + view.height > view.rootView.height / 2) Gravity.TOP else Gravity.BOTTOM
        val popupMenu = popupMenu {
            dropdownGravity = gravity
            section {
                if (play_external) {
                    item {
                        labelRes = R.string.context_menu_play_in_AMARadio
                        icon = R.drawable.ic_play_in_amaradio_24dp
                        callback = {
                            StationActions.playInAMARadio(context, station)
                        }
                    }
                } else {
                    item {
                        labelRes = R.string.context_menu_play_in_external_player
                        iconDrawable = IconicsDrawable(context, CommunityMaterial.Icon2.cmd_play_box_outline).sizeDp(24)
                        callback = {
                            Utils.playAndWarnIfMetered(context.applicationContext as AMARadioApp, station,
                                    PlayerType.EXTERNAL) { PlayStationTask.playExternal(station, context).execute() }
                        }
                    }
                }
                item {
                    labelRes = R.string.context_menu_visit_homepage
                    iconDrawable = IconicsDrawable(context, GoogleMaterial.Icon.gmd_home).sizeDp(24)
                    callback = {
                        StationActions.openStationHomeUrl(activity, station)
                    }
                }
                item {
                    labelRes = R.string.context_menu_share
                    icon = R.drawable.ic_share_24dp
                    callback = {
                        StationActions.share(context, station)
                    }
                }
                item {
                    labelRes = R.string.context_menu_add_alarm
                    icon = R.drawable.ic_add_alarm_black_24dp
                    callback = {
                        StationActions.setAsAlarm(activity, station)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    item {
                        labelRes = R.string.context_menu_create_shortcut
                        icon = R.drawable.ic_back_arrow_24dp
                        callback = {
                            station.prepareShortcut(context) { shortcut ->
                                val sm = context.getSystemService(ShortcutManager::class.java)
                                if (sm != null && sm.isRequestPinShortcutSupported) {
                                    sm.requestPinShortcut(shortcut, null)
                                }
                            }
                        }
                    }
                }
                item {
                    labelRes = R.string.context_menu_delete
                    icon = R.drawable.ic_delete_black_24dp
                    callback = {
                        StationActions.removeFromFavourites(context, rootView, station)
                    }
                }
            }
        }
        popupMenu.show(context, view)
        return popupMenu
    }
}
