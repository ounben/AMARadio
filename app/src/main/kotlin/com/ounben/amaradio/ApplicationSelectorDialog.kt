package com.ounben.amaradio

import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ounben.amaradio.interfaces.IApplicationSelected

class ApplicationSelectorDialog : DialogFragment() {
    private val listInfos = ArrayList<ActivityInfo>()
    private var callback: IApplicationSelected? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arrayAdapter = ArrayAdapter<String>(requireActivity(), android.R.layout.select_dialog_singlechoice)

        val pm = requireContext().packageManager
        val mainIntent = Intent(Intent.ACTION_VIEW)
        mainIntent.setDataAndType(Uri.parse("http://example.com/test.mp3"), "audio/*")
        val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolveInfos) {
            val applicationInfo = info.activityInfo.applicationInfo
            if (Utils.isDebug) {
                Log.d("UUU", "${applicationInfo.packageName} -- ${info.activityInfo.name} -> ")
            }
            arrayAdapter.add("${pm.getApplicationLabel(applicationInfo)}")
            listInfos.add(info.activityInfo)
        }

        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(R.string.alert_select_external_alarm_app)
        builder.setAdapter(arrayAdapter) { _, which ->
            if (Utils.isDebug) {
                Log.d("AAA", "choose : $which")
            }
            callback?.let {
                val info = listInfos[which]
                it.onAppSelected(info.packageName, info.name)
            }
        }

        return builder.create()
    }

    fun setCallback(callback: IApplicationSelected) {
        this.callback = callback
    }
}
