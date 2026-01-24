package com.ireddragonicy.konabessnext.model

import org.json.JSONException
import org.json.JSONObject

data class ExportHistoryItem(
    val timestamp: Long,
    var filename: String,
    var description: String,
    var filePath: String,
    val chipType: String
) {
    // Convert to JSON for storage
    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("timestamp", timestamp)
        json.put("filename", filename)
        json.put("description", description)
        json.put("filePath", filePath)
        json.put("chipType", chipType)
        return json
    }

    companion object {
        // Create from JSON
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(json: JSONObject): ExportHistoryItem {
            return ExportHistoryItem(
                json.getLong("timestamp"),
                json.getString("filename"),
                json.getString("description"),
                json.getString("filePath"),
                json.optString("chipType", "Unknown")
            )
        }
    }
}
