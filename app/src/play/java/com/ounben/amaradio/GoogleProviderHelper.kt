package com.ounben.amaradio

import android.content.Context
import android.util.Log
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

object GoogleProviderHelper {
    @JvmStatic
    fun use(ctx: Context) {
        try {
            Log.i("HLP", "Try to install google helper for higher TLS support..")
            ProviderInstaller.installIfNeeded(ctx)
            Log.i("HLP", "Google helper was installed OK.")
        } catch (e: GooglePlayServicesRepairableException) {
            Log.e("HLP", "Google helper was not installed because services not repairable!")
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.e("HLP", "Google helper was not installed because services not available!")
            e.printStackTrace()
        }
    }
}
