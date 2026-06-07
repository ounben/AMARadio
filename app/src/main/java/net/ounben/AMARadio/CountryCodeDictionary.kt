package net.ounben.AMARadio

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class CountryCodeDictionary private constructor() {
    private class Country {
        val name: String? = null
        val code: String? = null
    }

    private val codeToCountry = HashMap<String, String>()

    fun load(context: Context) {
        val resources = context.resources
        val inputStream = resources.openRawResource(R.raw.countries)
        val reader = BufferedReader(InputStreamReader(inputStream))

        val gson = Gson()
        val collectionType = object : TypeToken<Collection<Country>>() {}.type
        val countries = gson.fromJson<Collection<Country>>(reader, collectionType)

        for (country in countries) {
            val code = country.code
            val name = country.name
            if (code != null && name != null) {
                codeToCountry[code.lowercase(Locale.ENGLISH)] = name
            }
        }
    }

    fun getCountryByCode(code: String): String? {
        return codeToCountry[code.lowercase(Locale.ENGLISH)]
    }

    companion object {
        @JvmStatic
        val instance = CountryCodeDictionary()
    }
}
