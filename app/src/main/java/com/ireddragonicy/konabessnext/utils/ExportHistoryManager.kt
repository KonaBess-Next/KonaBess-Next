package com.ireddragonicy.konabessnext.utils

import android.content.Context
import android.content.SharedPreferences
import com.ireddragonicy.konabessnext.model.ExportHistoryItem
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.Collections

class ExportHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ExportHistory"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_ITEMS = 50
    }

    // Add new export to history
    fun addExport(filename: String, description: String, filePath: String, chipType: String) {
        var history = getHistory()

        // Add new item
        val newItem = ExportHistoryItem(
            System.currentTimeMillis(),
            filename,
            description,
            filePath,
            chipType
        )
        history.add(0, newItem) // Add to beginning

        // Keep only MAX_HISTORY_ITEMS
        if (history.size > MAX_HISTORY_ITEMS) {
            history = ArrayList(history.subList(0, MAX_HISTORY_ITEMS))
        }

        saveHistory(history)
    }

    // Get all history items
    fun getHistory(): MutableList<ExportHistoryItem> {
        val history = ArrayList<ExportHistoryItem>()
        val jsonString = prefs.getString(KEY_HISTORY, "[]")

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val item = ExportHistoryItem.fromJSON(json)

                // Only add if file still exists
                val file = File(item.filePath)
                if (file.exists()) {
                    history.add(item)
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return history
    }

    // Save history to SharedPreferences
    private fun saveHistory(history: List<ExportHistoryItem>) {
        val jsonArray = JSONArray()
        for (item in history) {
            try {
                jsonArray.put(item.toJSON())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    // Delete history item
    fun deleteItem(item: ExportHistoryItem) {
        val history = getHistory()
        history.removeIf { h -> h.timestamp == item.timestamp }
        saveHistory(history)

        // Delete the file if it exists
        val file = File(item.filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    // Update existing history item (for rename)
    fun updateItem(updatedItem: ExportHistoryItem) {
        val history = getHistory()
        for (i in history.indices) {
            if (history[i].timestamp == updatedItem.timestamp) {
                history[i] = updatedItem
                break
            }
        }
        saveHistory(history)
    }

    // Clear all history
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // Get history count
    fun getHistoryCount(): Int {
        return getHistory().size
    }
}
