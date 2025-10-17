package xzr.konabess.models;

import org.json.JSONException;
import org.json.JSONObject;

public class ExportHistoryItem {
    private long timestamp;
    private String filename;
    private String description;
    private String filePath;
    private String chipType;

    public ExportHistoryItem(long timestamp, String filename, String description, String filePath, String chipType) {
        this.timestamp = timestamp;
        this.filename = filename;
        this.description = description;
        this.filePath = filePath;
        this.chipType = chipType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFilename() {
        return filename;
    }

    public String getDescription() {
        return description;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getChipType() {
        return chipType;
    }

    // Convert to JSON for storage
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("filename", filename);
        json.put("description", description);
        json.put("filePath", filePath);
        json.put("chipType", chipType);
        return json;
    }

    // Create from JSON
    public static ExportHistoryItem fromJSON(JSONObject json) throws JSONException {
        return new ExportHistoryItem(
            json.getLong("timestamp"),
            json.getString("filename"),
            json.getString("description"),
            json.getString("filePath"),
            json.optString("chipType", "Unknown")
        );
    }
}
