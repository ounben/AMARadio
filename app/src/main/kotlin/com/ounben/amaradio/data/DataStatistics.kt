package com.ounben.amaradio.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class DataStatistics {
    var Name: String = ""
    var Value: String = ""

    companion object {
        private val jsonConfig = Json { ignoreUnknownKeys = true }

        @JvmStatic
        fun DecodeJson(result: String?): Array<DataStatistics> {
            if (result.isNullOrBlank()) return emptyArray()
            val aList = ArrayList<DataStatistics>()
            try {
                val element = jsonConfig.parseToJsonElement(result)
                val jsonObject = element.jsonObject
                for (key in jsonObject.keys) {
                    val aData = DataStatistics()
                    aData.Name = key
                    aData.Value = jsonObject[key]?.jsonPrimitive?.content ?: ""
                    aList.add(aData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return aList.toTypedArray()
        }
    }
}
