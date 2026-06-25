package com.ounben.amaradio

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class CountryCodeDictionary private constructor() {
    @Serializable
    private class Country {
        val name: String? = null
        val code: String? = null
    }

    private val codeToCountry = HashMap<String, String>()
    private val displayCountryCache = HashMap<String, String>()

    fun load(context: Context) {
        val resources = context.resources
        val inputStream = resources.openRawResource(R.raw.countries)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonContent = reader.use { it.readText() }

        val countries = Json.decodeFromString<List<Country>>(jsonContent)

        for (country in countries) {
            val code = country.code
            val name = country.name
            if (code != null && name != null) {
                codeToCountry[code.lowercase(Locale.ENGLISH)] = name
            }
        }
    }

    fun getCountryByCode(code: String): String? {
        val lowerCode = code.lowercase(Locale.ENGLISH)
        displayCountryCache[lowerCode]?.let { return it }

        val locale = try {
            Locale.Builder().setRegion(code).build()
        } catch (e: Exception) {
            Locale("", code)
        }
        val displayCountry = locale.getDisplayCountry(Locale.getDefault())
        val result = if (displayCountry.isNotEmpty() && !displayCountry.equals(code, ignoreCase = true)) {
            displayCountry
        } else {
            codeToCountry[lowerCode]
        }
        
        result?.let { displayCountryCache[lowerCode] = it }
        return result
    }

    companion object {
        @JvmStatic
        val instance = CountryCodeDictionary()
    }
}
