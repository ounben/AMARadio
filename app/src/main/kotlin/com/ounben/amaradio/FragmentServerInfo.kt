package com.ounben.amaradio

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ounben.amaradio.adapters.ItemAdapterStatistics
import com.ounben.amaradio.data.DataStatistics
import com.ounben.amaradio.interfaces.IFragmentRefreshable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FragmentServerInfo : Fragment(), IFragmentRefreshable {
    private var itemAdapterStatistics: ItemAdapterStatistics? = null
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_statistics, null)

        if (itemAdapterStatistics == null) {
            itemAdapterStatistics = ItemAdapterStatistics(requireActivity(), R.layout.list_item_statistic)
        }

        val lv = view.findViewById<ListView>(R.id.listViewStatistics)
        lv.adapter = itemAdapterStatistics

        download(forceUpdate = false)

        return view
    }

    private fun download(forceUpdate: Boolean) {
        val context = context ?: return
        AppEventManager.sendEvent(Intent(ActivityMain.ACTION_SHOW_LOADING))

        val app = requireActivity().application as AMARadioApp
        val httpClient = app.httpClient

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(httpClient, requireActivity(), "json/stats", forceUpdate, null)
            }

            AppEventManager.sendEvent(Intent(ActivityMain.ACTION_HIDE_LOADING))
            
            if (result != null) {
                itemAdapterStatistics?.clear()
                val items = DataStatistics.DecodeJson(result) ?: emptyArray()
                for (item in items) {
                    itemAdapterStatistics?.add(item)
                }
            } else {
                try {
                    Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ERR", e.toString())
                }
            }
        }
    }

    override fun refresh() {
        download(forceUpdate = true)
    }

    override fun onDestroyView() {
        downloadJob?.cancel()
        super.onDestroyView()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
