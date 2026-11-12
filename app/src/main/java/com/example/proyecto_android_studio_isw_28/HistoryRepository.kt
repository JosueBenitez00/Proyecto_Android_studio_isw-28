package com.example.proyecto_android_studio_isw_28

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "calculator_history")

data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HistoryRepository(private val context: Context) {

    private val KEY = stringPreferencesKey("history_json")
    private val MAX_ENTRIES = 100

    val historyFlow: Flow<List<HistoryEntry>> = context.dataStore.data.map { prefs ->
        parseJson(prefs[KEY] ?: "[]")
    }

    suspend fun addEntry(expression: String, result: String) {
        context.dataStore.edit { prefs ->
            val current = parseJson(prefs[KEY] ?: "[]").toMutableList()
            if (current.firstOrNull()?.expression != expression ||
                current.firstOrNull()?.result != result
            ) {
                current.add(0, HistoryEntry(expression, result))
            }
            prefs[KEY] = toJson(current.take(MAX_ENTRIES))
        }
    }

    suspend fun removeEntry(timestamp: Long) {
        context.dataStore.edit { prefs ->
            val current = parseJson(prefs[KEY] ?: "[]")
                .filterNot { it.timestamp == timestamp }
            prefs[KEY] = toJson(current)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY) }
    }

    private fun parseJson(json: String): List<HistoryEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            HistoryEntry(
                expression = obj.getString("expression"),
                result = obj.getString("result"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun toJson(entries: List<HistoryEntry>): String {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("expression", it.expression)
                put("result", it.result)
                put("timestamp", it.timestamp)
            })
        }
        return arr.toString()
    }
}