package com.ounben.amaradio

import okhttp3.OkHttpClient

object HttpClient {
    @JvmStatic
    val instance = OkHttpClient()
}
