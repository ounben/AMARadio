package com.ounben.amaradio.players.selector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ounben.amaradio.R
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.players.mpd.MPDClient
import com.ounben.amaradio.players.mpd.MPDServerData
import com.ounben.amaradio.players.mpd.MPDServersRepository
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.Utils.parseIntWithDefault

class PlayerSelectorDialog() : BottomSheetDialogFragment() {

    companion object {
        const val FRAGMENT_TAG = "mpd_servers_dialog_fragment"
    }

    private lateinit var mpdClient: MPDClient
    private var stationToPlay: DataRadioStation? = null
    private var updateUIReceiver: BroadcastReceiver? = null
    private var recyclerViewServers: RecyclerView? = null
    private var playerSelectorAdapter: PlayerSelectorAdapter? = null
    private lateinit var serversRepository: MPDServersRepository
    private var btnEnableMPD: Button? = null
    private var btnAddMPDServer: Button? = null

    constructor(mpdClient: MPDClient) : this() {
        this.mpdClient = mpdClient
    }

    constructor(mpdClient: MPDClient, stationToPlay: DataRadioStation?) : this() {
        this.mpdClient = mpdClient
        this.stationToPlay = stationToPlay
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        @Suppress("DEPRECATION")
        retainInstance = true
        val view = inflater.inflate(R.layout.dialog_mpd_servers, container, false)
        val AMARadioApp = requireActivity().application as AMARadioApp
        mpdClient = AMARadioApp.mpdClient
        serversRepository = AMARadioApp.mpdClient.mpdServersRepository
        recyclerViewServers = view.findViewById(R.id.recyclerViewMPDServers)
        val llm = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)
        recyclerViewServers?.layoutManager = llm
        playerSelectorAdapter = PlayerSelectorAdapter(requireContext(), stationToPlay)
        playerSelectorAdapter?.setActionListener(object : PlayerSelectorAdapter.ActionListener {
            override fun editServer(mpdServerData: MPDServerData) {
                editOrAddServer(MPDServerData(mpdServerData))
            }
            override fun removeServer(mpdServerData: MPDServerData) {
                serversRepository.removeServer(mpdServerData)
            }
        })
        recyclerViewServers?.adapter = playerSelectorAdapter
        btnEnableMPD = view.findViewById(R.id.btnEnableMPD)
        btnAddMPDServer = view.findViewById(R.id.btnAddMPDServer)
        btnEnableMPD?.setOnClickListener {
            val mpdEnabled = !mpdClient.isMpdEnabled
            mpdClient.setMPDEnabled(mpdEnabled)
            if (mpdEnabled) {
                mpdClient.enableAutoUpdate()
            } else {
                mpdClient.disableAutoUpdate()
            }
            updateEnableMpdButton()
        }
        btnAddMPDServer?.setOnClickListener { editOrAddServer(null) }
        val servers: LiveData<List<MPDServerData>> = serversRepository.allServers
        servers.observe(viewLifecycleOwner) { mpdServers -> playerSelectorAdapter?.setEntries(mpdServers) }
        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (PlayerService.PLAYER_SERVICE_STATE_CHANGE == intent.action) {
                    playerSelectorAdapter?.notifyAMARadioPlaybackStateChanged()
                }
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        if (mpdClient.isMpdEnabled) {
            mpdClient.enableAutoUpdate()
        }
        val filter = IntentFilter()
        filter.addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateUIReceiver!!, filter)
        updateEnableMpdButton()
    }

    override fun onPause() {
        super.onPause()
        mpdClient.disableAutoUpdate()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateUIReceiver!!)
    }

    private fun updateEnableMpdButton() {
        if (mpdClient.isMpdEnabled) {
            btnEnableMPD?.setText(R.string.action_disable_mpd)
        } else {
            btnEnableMPD?.setText(R.string.action_enable_mpd)
        }
    }

    private fun editOrAddServer(server: MPDServerData?) {
        val inflater = layoutInflater
        val serverView = inflater.inflate(R.layout.layout_server_alert, null)
        val editName = serverView.findViewById<EditText>(R.id.mpd_server_name)
        val editHostnameH = serverView.findViewById<EditText>(R.id.mpd_server_hostname)
        val editPassword = serverView.findViewById<EditText>(R.id.mpd_server_password)
        val editPort = serverView.findViewById<EditText>(R.id.mpd_server_port)
        if (server != null) {
            editName.setText(server.name)
            editHostnameH.setText(server.hostname)
            editPort.setText(server.port.toString())
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setView(serverView)
            .setPositiveButton(R.string.alert_select_mpd_server_save, null)
            .setNeutralButton(R.string.alert_select_mpd_server_remove, null)
            .setTitle(R.string.alert_add_or_edit_mpd_server).create()
        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnRemove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            btnPositive.setOnClickListener {
                val serverName = editName.text.toString().trim()
                val hostname = editHostnameH.text.toString().trim()
                val password = editPassword.text.toString().trim()
                val port = parseIntWithDefault(editPort.text.toString().trim(), 0)
                if (serverName.isEmpty() || hostname.isEmpty() || port == 0) {
                    return@setOnClickListener
                }
                if (server != null) {
                    server.name = serverName
                    server.hostname = hostname
                    server.port = port
                    server.password = password
                    serversRepository.updatePersistentData(server)
                } else {
                    val newServer = MPDServerData(serverName, hostname, port, password)
                    serversRepository.addServer(newServer)
                }
                mpdClient.launchQuickCheck()
                dialog.cancel()
            }
            btnRemove.setOnClickListener {
                if (server != null) {
                    serversRepository.removeServer(server)
                    mpdClient.launchQuickCheck()
                }
                dialog.cancel()
            }
        }
        editName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
        }
        editName.requestFocus()
        dialog.show()
    }
}
