package com.ukralarm.app.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AlertApi {
    private const val API_URL = "https://vadimklimenko.com/map/statuses.json"
    private var cachedResponse: JSONObject? = null
    private var lastFetchTime: Long = 0
    private const val CACHE_DURATION_MS = 5000L

    fun isAlertActive(regionName: String, districtName: String = ""): Boolean {
        return try {
            val data = fetchData()
            val states = data.optJSONObject("states") ?: return false
            val regionData = states.optJSONObject(regionName) ?: return false
            
            if (districtName.isNotEmpty()) {
                val districts = regionData.optJSONObject("districts")
                if (districts != null) {
                    val districtData = districts.optJSONObject(districtName) 
                        ?: districts.optJSONObject("$districtName район")
                        ?: districts.optJSONObject(districtName.replace("територіальна громада", "ТГ"))
                        
                    if (districtData != null) {
                        return districtData.optBoolean("enabled", false)
                    } else {
                        // Fallback: search keys containing the district name
                        val iter = districts.keys()
                        while (iter.hasNext()) {
                            val key = iter.next()
                            if (key.contains(districtName, ignoreCase = true)) {
                                return districts.optJSONObject(key)?.optBoolean("enabled", false) ?: false
                            }
                        }
                    }
                }
            }
            regionData.optBoolean("enabled", false)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAllStates(): Map<String, Boolean> {
        return try {
            val data = fetchData()
            val states = data.optJSONObject("states") ?: return emptyMap()
            val result = mutableMapOf<String, Boolean>()
            val keys = states.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val regionData = states.optJSONObject(key)
                if (regionData != null) {
                    result[key] = regionData.optBoolean("enabled", false)
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    @Synchronized
    private fun fetchData(): JSONObject {
        val now = System.currentTimeMillis()
        if (cachedResponse != null && now - lastFetchTime < CACHE_DURATION_MS) {
            return cachedResponse!!
        }

        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                cachedResponse = json
                lastFetchTime = now
                return json
            }
        } finally {
            connection.disconnect()
        }

        return cachedResponse ?: JSONObject()
    }
}
