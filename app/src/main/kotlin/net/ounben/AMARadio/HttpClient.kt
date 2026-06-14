package net.ounben.AMARadio

import okhttp3.OkHttpClient

object HttpClient {
    @JvmStatic
    val instance = OkHttpClient()
}
