package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ounben.amaradio.proxy.ProxySettingsDialog
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.SettingsScreen
import com.ounben.amaradio.ui.SettingsViewModel

class FragmentSettings : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AMARadioTheme {
                    val settingsViewModel: SettingsViewModel = viewModel()
                    var batterySummary by remember { mutableStateOf(getBatterySummary()) }

                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenProxy = {
                            ProxySettingsDialog().show(parentFragmentManager, "proxy")
                        },
                        onOpenAbout = {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.containerView, FragmentAbout())
                                .addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString())
                                .commitAllowingStateLoss()
                        },
                        onOpenStatistics = {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.containerView, FragmentServerInfo())
                                .addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString())
                                .commitAllowingStateLoss()
                        },
                        onOpenEqualizer = {
                            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                            if (requireContext().packageManager.resolveActivity(intent, 0) == null) {
                                Toast.makeText(requireContext(), R.string.error_no_equalizer_found, Toast.LENGTH_SHORT).show()
                            } else {
                                intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                @Suppress("DEPRECATION")
                                startActivityForResult(intent, ActivityMain.LAUNCH_EQUALIZER_REQUEST)
                            }
                        },
                        onBatteryOptimize = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                        batterySummary = batterySummary
                    )
                    
                    LaunchedEffect(Unit) {
                        batterySummary = getBatterySummary()
                    }
                }
            }
        }
    }

    private fun getBatterySummary(): String {
        val ctx = context ?: return ""
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            getString(R.string.settings_ignore_battery_optimization_summary_on)
        } else {
            getString(R.string.settings_ignore_battery_optimization_summary_off)
        }
    }
}
