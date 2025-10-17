package xzr.konabess.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import xzr.konabess.models.ExportHistoryItem;

public class ExportHistoryManager {
    private static final String PREFS_NAME = "ExportHistory";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY_ITEMS = 50;

    private final SharedPreferences prefs;

    public ExportHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Add new export to history
    public void addExport(String filename, String description, String filePath, String chipType) {
        List<ExportHistoryItem> history = getHistory();
        
        // Add new item
        ExportHistoryItem newItem = new ExportHistoryItem(
            System.currentTimeMillis(),
            filename,
            description,
            filePath,
            chipType
        );
        history.add(0, newItem); // Add to beginning
        
        // Keep only MAX_HISTORY_ITEMS
        if (history.size() > MAX_HISTORY_ITEMS) {
            history = history.subList(0, MAX_HISTORY_ITEMS);
        }
        
        saveHistory(history);
    }

    // Get all history items
    public List<ExportHistoryItem> getHistory() {
        List<ExportHistoryItem> history = new ArrayList<>();
        String jsonString = prefs.getString(KEY_HISTORY, "[]");
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                ExportHistoryItem item = ExportHistoryItem.fromJSON(json);
                
                // Only add if file still exists
                File file = new File(item.getFilePath());
                if (file.exists()) {
                    history.add(item);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return history;
    }

    // Save history to SharedPreferences
    private void saveHistory(List<ExportHistoryItem> history) {
        JSONArray jsonArray = new JSONArray();
        for (ExportHistoryItem item : history) {
            try {
                jsonArray.put(item.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply();
    }

    // Delete history item
    public void deleteItem(ExportHistoryItem item) {
        List<ExportHistoryItem> history = getHistory();
        history.removeIf(h -> h.getTimestamp() == item.getTimestamp());
        saveHistory(history);
        
        // Delete the file if it exists
        File file = new File(item.getFilePath());
        if (file.exists()) {
            file.delete();
        }
    }

    // Clear all history
    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    // Get history count
    public int getHistoryCount() {
        return getHistory().size();
    }
}
