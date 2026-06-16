package com.ounben.amaradio

import android.content.*
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import com.ounben.amaradio.interfaces.IApplicationSelected
import com.ounben.amaradio.proxy.ProxySettingsDialog
import com.ounben.amaradio.utils.UiScaler
import androidx.core.content.edit

class FragmentSettings : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener,
    IApplicationSelected {

    private fun refreshToplevelIcons() {
        val color = Utils.getAccentColor(requireContext())
        val screen = preferenceScreen ?: return
        val scale = UiScaler.getScaleFactor(requireContext())
        val iconSizePx = (24 * resources.displayMetrics.density * scale).toInt()

        fun processPreference(pref: Preference) {
            // Categories in some themes don't show icons, but we set them anyway
            if (pref is PreferenceCategory) {
                when (pref.key) {
                    "pref_category_ui" -> pref.setIcon(R.drawable.ic_monitor_24dp)
                    "pref_category_startup" -> pref.setIcon(R.drawable.ic_flight_takeoff_24dp)
                    "pref_category_interaction" -> pref.setIcon(R.drawable.ic_touch_app_24dp)
                    "pref_category_player" -> pref.setIcon(R.drawable.ic_play_arrow_24dp)
                    "pref_category_alarm" -> pref.setIcon(R.drawable.ic_access_alarms_black_24dp)
                    "pref_category_connectivity" -> pref.setIcon(R.drawable.ic_sync_black_24dp)
                    "pref_category_mpd" -> pref.setIcon(R.drawable.ic_volume_up_24dp)
                    "pref_category_other" -> pref.setIcon(R.drawable.ic_live_help_24dp)
                }
            }

            pref.icon?.let { icon ->
                // Unwrap if already processed to avoid deep nested wrappers
                val baseIcon = if (icon is android.graphics.drawable.DrawableWrapper) {
                    icon.drawable ?: icon
                } else {
                    icon
                }
                
                val mutatedIcon = baseIcon.mutate()
                mutatedIcon.setTint(color)
                
                // Strictly enforce the icon size to match other preference icons (24dp base)
                val scaledIcon = object : android.graphics.drawable.DrawableWrapper(mutatedIcon) {
                    override fun getIntrinsicWidth(): Int = iconSizePx
                    override fun getIntrinsicHeight(): Int = iconSizePx
                }
                pref.icon = scaledIcon
            }
            
            if (pref is PreferenceGroup) {
                for (i in 0 until pref.preferenceCount) {
                    processPreference(pref.getPreference(i))
                }
            }
        }

        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        
        // Process everything (tint and scale)
        processPreference(screen)
    }

    private fun refreshToolbar() {
        val activity = activity as? ActivityMain ?: return
        val myToolbar = activity.findViewById<Toolbar>(R.id.my_awesome_toolbar)
        val screen = preferenceScreen

        if (myToolbar == null || screen == null) return

        if (Utils.bottomNavigationEnabled(activity)) {
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            activity.supportActionBar?.setDisplayShowHomeEnabled(false)
            myToolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        setPreferencesFromResource(R.xml.preferences, s)
        
        // Explicitly set summary providers for all list preferences
        val listPrefs = listOf("theme_name", "ui_scale_level", "startup_action", "auto_off_timeout", "alarm_timeout")
        listPrefs.forEach { key ->
            findPreference<ListPreference>(key)?.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        refreshToolbar()
        refreshToplevelIcons()

        // Equalizer
        findPreference<Preference>("equalizer")?.setOnPreferenceClickListener {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            if (requireContext().packageManager.resolveActivity(intent, 0) == null) {
                Toast.makeText(context, R.string.error_no_equalizer_found, Toast.LENGTH_SHORT).show()
            } else {
                intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, ActivityMain.LAUNCH_EQUALIZER_REQUEST)
            }
            false
        }

        // Proxy
        findPreference<Preference>("settings_proxy")?.setOnPreferenceClickListener {
            val proxySettingsDialog = ProxySettingsDialog()
            proxySettingsDialog.setCancelable(true)
            proxySettingsDialog.show(parentFragmentManager, "")
            false
        }

        // MPD
        findPreference<Preference>("mpd_servers_viewer")?.setOnPreferenceClickListener {
            val AMARadioApp = requireActivity().application as AMARadioApp
            Utils.showMpdServersDialog(AMARadioApp, requireActivity().supportFragmentManager, null)
            false
        }

        // Statistics
        findPreference<Preference>("show_statistics")?.setOnPreferenceClickListener {
            val f = FragmentServerInfo()
            val fragmentTransaction = parentFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.containerView, f).addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString()).commit()
            false
        }

        // About
        findPreference<Preference>("show_about")?.setOnPreferenceClickListener {
            val f = FragmentAbout()
            val fragmentTransaction = parentFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.containerView, f).addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString()).commit()
            false
        }

        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null) {
            updateBatteryPrefDescription(batPref)
            batPref.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                updateBatteryPrefDescription(batPref)
                true
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (parentFragmentManager.findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") != null) {
            return
        }

        val f: DialogFragment? = when (preference) {
            is ListPreference -> ListPreferenceDialogFragmentCompat.newInstance(preference.key)
            is EditTextPreference -> EditTextPreferenceDialogFragmentCompat.newInstance(preference.key)
            else -> null
        }

        if (f != null) {
            @Suppress("DEPRECATION")
            f.setTargetFragment(this, 0)
            f.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        refreshToolbar()
        refreshToplevelIcons()
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null) {
            updateBatteryPrefDescription(batPref)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    private fun updateBatteryPrefDescription(batPref: Preference) {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            batPref.setSummary(R.string.settings_ignore_battery_optimization_summary_on)
        } else {
            batPref.setSummary(R.string.settings_ignore_battery_optimization_summary_off)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Utils.isDebug) {
            Log.d("AAA", "changed key: $key")
        }
        if (key == "alarm_external") {
            val active = sharedPreferences?.getBoolean(key, false) ?: false
            if (active) {
                val newFragment = ApplicationSelectorDialog()
                newFragment.setCallback(this)
                newFragment.show(requireActivity().supportFragmentManager, "appPicker")
            }
        }
        if (key == "theme_name" || key == "bottom_navigation" || key == UiScaler.PREF_KEY_UI_SCALE) {
            if (isResumed && activity != null && activity?.isFinishing == false) {
                view?.post {
                    if (activity != null && activity?.isFinishing == false) {
                        activity?.recreate()
                    }
                }
            }
        }
    }

    override fun onAppSelected(packageName: String, activityName: String) {
        if (Utils.isDebug) {
            Log.d("SEL", "selected: $packageName/$activityName")
        }
        preferenceManager.sharedPreferences?.edit {
            putString("shareapp_package", packageName)
            putString("shareapp_activity", activityName)
        }
        findPreference<Preference>("shareapp_package")?.summary = packageName
    }
}
