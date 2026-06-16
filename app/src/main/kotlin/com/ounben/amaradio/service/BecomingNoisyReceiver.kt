package com.ounben.amaradio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.preference.PreferenceManager

class BecomingNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action && PlayerServiceUtil.isPlaying()) {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
            if (sharedPref.getBoolean("pause_when_noisy", true)) {
                PlayerServiceUtil.pause(PauseReason.BECAME_NOISY)
            }
        }
    }
}
