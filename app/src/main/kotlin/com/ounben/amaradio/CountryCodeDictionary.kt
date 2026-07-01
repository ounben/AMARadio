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

    fun load(context: Context) {
        val resources = context.resources
        val inputStream = resources.openRawResource(R.raw.countries)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonContent = reader.use { it.readText() }

        val countries = try {
            Json.decodeFromString<List<Country>>(jsonContent)
        } catch (e: Exception) {
            emptyList()
        }

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

        val locale = try {
            Locale.Builder().setRegion(code).build()
        } catch (_: Exception) {
            Locale.Builder().setLanguage("").setRegion(code).build()
        }
        
        val displayCountry = locale.getDisplayCountry(Locale.getDefault())
        return if (displayCountry.isNotEmpty() && !displayCountry.equals(code, ignoreCase = true)) {
            displayCountry
        } else {
            codeToCountry[lowerCode]
        }
    }

    companion object {
        @JvmStatic
        val instance = CountryCodeDictionary()
    }
}
