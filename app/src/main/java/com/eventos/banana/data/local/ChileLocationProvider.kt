package com.eventos.banana.data.location

import android.content.Context
import org.json.JSONObject

object ChileLocationProvider {

    private var cachedData: Map<String, List<String>>? = null

    fun getRegionsWithCommunes(context: Context): Map<String, List<String>> {
        if (cachedData != null) return cachedData!!

        val json = context.assets
            .open("chile_locations.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonObject = JSONObject(json)
        val result = mutableMapOf<String, List<String>>()

        jsonObject.keys().forEach { region ->
            val communesJson = jsonObject.getJSONArray(region)
            val communes = mutableListOf<String>()
            for (i in 0 until communesJson.length()) {
                communes.add(communesJson.getString(i))
            }
            result[region] = communes
        }

        cachedData = result
        return result
    }
}
