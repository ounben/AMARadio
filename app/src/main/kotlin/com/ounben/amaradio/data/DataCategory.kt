package com.ounben.amaradio.data

import android.graphics.drawable.Drawable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
class DataCategory : Comparable<DataCategory> {
    @SerialName("name") var Name: String = ""
    @SerialName("stationcount") var UsedCount: Int = 0
    
    @Transient var Label: String? = null
    @Transient var Icon: Drawable? = null

    val sortField: String
        get() = Label ?: Name

    override fun compareTo(other: DataCategory): Int {
        return sortField.compareTo(other.sortField, ignoreCase = true)
    }

    companion object {
        private val jsonConfig = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun DecodeJson(result: String?): Array<DataCategory> {
            if (result == null) return emptyArray()
            val trimmedResult = result.trim()
            if (trimmedResult.startsWith("[")) {
                return try {
                    jsonConfig.decodeFromString<List<DataCategory>>(trimmedResult).toTypedArray()
                } catch (e: Exception) {
                    android.util.Log.e("DataCategory", "DecodeJson error", e)
                    emptyArray()
                }
            } else if (trimmedResult.startsWith("<html", ignoreCase = true) || trimmedResult.startsWith("<!DOCTYPE", ignoreCase = true)) {
                android.util.Log.w("DataCategory", "DecodeJson: Received HTML instead of JSON.")
            }
            return emptyArray()
        }
    }
}
