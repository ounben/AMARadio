package com.ounben.amaradio.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.selector.PlayerType

class HeadsetConnectionReceiver : BroadcastReceiver() {
    private var headsetConnected: Boolean? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (PlayerServiceUtil.getPauseReason() != PauseReason.BECAME_NOISY) {
            return
        }
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val resumeOnWiredHeadset = sharedPref.getBoolean("auto_resume_on_wired_headset_connection", false)
        val resumeOnBluetoothHeadset = sharedPref.getBoolean("auto_resume_on_bluetooth_a2dp_connection", false)
        if (!resumeOnWiredHeadset && !resumeOnBluetoothHeadset) {
            return
        }
        if (PlayerServiceUtil.isPlaying()) {
            return
        }
        var play = false
        when (intent.action) {
            AudioManager.ACTION_HEADSET_PLUG -> {
                if (resumeOnWiredHeadset) {
                    val state = intent.getIntExtra("state", 0)
                    play = state == 1 && headsetConnected == false
                    headsetConnected = state == 1
                }
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                if (resumeOnBluetoothHeadset) {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    play = state == BluetoothProfile.STATE_CONNECTED && headsetConnected == false
                    headsetConnected = state == BluetoothProfile.STATE_CONNECTED
                }
            }
        }
        if (play) {
            val AMARadioApp = context.applicationContext as AMARadioApp
            val historyManager = AMARadioApp.historyManager
            val lastStation = historyManager.first
            if (lastStation != null) {
                if (!PlayerServiceUtil.isPlaying() && !AMARadioApp.mpdClient.isMpdEnabled) {
                    Utils.playAndWarnIfMetered(AMARadioApp, lastStation, PlayerType.AMARadio) {
                        Utils.play(lastStation)
                    }
                }
            }
        }
    }
}
