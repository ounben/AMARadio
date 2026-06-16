package com.ounben.amaradio.proxy

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import com.ounben.amaradio.R
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import okhttp3.Request
import java.io.IOException
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProxySettingsDialog : DialogFragment() {

    private lateinit var editProxyHost: EditText
    private lateinit var editProxyPort: EditText
    private lateinit var spinnerProxyType: AppCompatSpinner
    private lateinit var editLogin: EditText
    private lateinit var editProxyPassword: EditText
    private lateinit var textProxyTestResult: TextView

    private lateinit var proxyTypeAdapter: ArrayAdapter<Proxy.Type>
    private var proxyTestJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val layout = inflater.inflate(R.layout.dialog_proxy_settings, null)

        editProxyHost = layout.findViewById(R.id.edit_proxy_host)
        editProxyPort = layout.findViewById(R.id.edit_proxy_port)
        spinnerProxyType = layout.findViewById(R.id.spinner_proxy_type)
        editLogin = layout.findViewById(R.id.edit_proxy_login)
        editProxyPassword = layout.findViewById(R.id.edit_proxy_password)
        textProxyTestResult = layout.findViewById(R.id.text_test_proxy_result)

        proxyTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            arrayOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS))
        spinnerProxyType.adapter = proxyTypeAdapter

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        ProxySettings.fromPreferences(sharedPref)?.let { proxySettings ->
            editProxyHost.setText(proxySettings.host)
            editProxyPort.setText(proxySettings.port.toString())
            editLogin.setText(proxySettings.login)
            editProxyPassword.setText(proxySettings.password)
            spinnerProxyType.setSelection(proxyTypeAdapter.getPosition(proxySettings.type))
        }

        val dialog = builder.setView(layout)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val editor = sharedPref.edit()
                val proxySettings = createProxySettings()
                proxySettings.toPreferences(editor)
                editor.apply()

                (requireActivity().application as AMARadioApp).rebuildHttpClient()
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                this.dialog?.cancel()
            }
            .setNeutralButton(R.string.settings_proxy_action_test, null)
            .create()

        dialog.setOnShowListener {
            val button = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL)
            button.setOnClickListener {
                testProxy(createProxySettings())
            }
        }

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        proxyTestJob?.cancel()
    }

    private fun createProxySettings(): ProxySettings {
        val settings = ProxySettings()
        settings.host = editProxyHost.text.toString()
        settings.port = Utils.parseIntWithDefault(editProxyPort.text.toString(), 0)
        settings.login = editLogin.text.toString()
        settings.password = editProxyPassword.text.toString()
        settings.type = proxyTypeAdapter.getItem(spinnerProxyType.selectedItemPosition)
        return settings
    }

    private fun testProxy(proxySettings: ProxySettings) {
        proxyTestJob?.cancel()
        textProxyTestResult.text = ""

        val AMARadioApp = requireActivity().application as AMARadioApp
        val connectionSuccessStr = getString(R.string.settings_proxy_working, TEST_ADDRESS)
        val connectionFailedStr = getString(R.string.settings_proxy_not_working)
        val connectionInvalidInputStr = getString(R.string.settings_proxy_invalid)

        proxyTestJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val builder = AMARadioApp.newHttpClientWithoutProxy()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)

                if (!Utils.setOkHttpProxy(builder, proxySettings)) {
                    connectionInvalidInputStr
                } else {
                    val okHttpClient = builder.build()
                    val request = Request.Builder().url(TEST_ADDRESS).build()
                    try {
                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                connectionSuccessStr
                            } else {
                                String.format(Locale.ROOT, connectionFailedStr, TEST_ADDRESS, response.message)
                            }
                        }
                    } catch (e: IOException) {
                        String.format(Locale.ROOT, connectionFailedStr, TEST_ADDRESS, e.message)
                    }
                }
            }
            textProxyTestResult.text = result
        }
    }

    companion object {
        private const val TEST_ADDRESS = "http://radio-browser.info"
    }
}
