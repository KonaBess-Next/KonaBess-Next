package xzr.konabess.adapters;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import xzr.konabess.MainActivity;
import xzr.konabess.R;
import xzr.konabess.TableIO;
import xzr.konabess.models.ExportHistoryItem;
import xzr.konabess.utils.ExportHistoryManager;

public class ExportHistoryAdapter extends RecyclerView.Adapter<ExportHistoryAdapter.ViewHolder> {

    private final List<ExportHistoryItem> historyItems;
    private final Activity activity;
    private final ExportHistoryManager historyManager;
    private final OnHistoryChangeListener listener;

    public interface OnHistoryChangeListener {
        void onHistoryChanged();
    }

    public ExportHistoryAdapter(List<ExportHistoryItem> historyItems, Activity activity, 
                                ExportHistoryManager historyManager, OnHistoryChangeListener listener) {
        this.historyItems = historyItems;
        this.activity = activity;
        this.historyManager = historyManager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_export_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExportHistoryItem item = historyItems.get(position);
        
        holder.filename.setText(item.getFilename());
        holder.description.setText(item.getDescription().isEmpty() ? 
                activity.getString(R.string.no_description) : item.getDescription());
        holder.chipType.setText(item.getChipType());
        
        // Format timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(item.getTimestamp())));
        
        // Add Material You entrance animation
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(50f);
        holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(position * 50L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        
        // Apply button
        holder.btnApply.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.apply)
                    .setMessage(activity.getString(R.string.confirm_apply_config))
                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                        applyConfig(item);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        
        // Share button
        holder.btnShare.setOnClickListener(v -> shareConfig(item));
        
        // Delete button
        holder.btnDelete.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.confirm_delete_history)
                    .setMessage(R.string.confirm_delete_history_msg)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        historyManager.deleteItem(item);
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            historyItems.remove(pos);
                            notifyItemRemoved(pos);
                            Toast.makeText(activity, R.string.history_item_deleted, Toast.LENGTH_SHORT).show();
                            if (listener != null) {
                                listener.onHistoryChanged();
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        
        // Display file path
        holder.filePath.setText(item.getFilePath());
        
        // Copy path button
        holder.btnCopyPath.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("File Path", item.getFilePath());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(activity, R.string.path_copied, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    private void applyConfig(ExportHistoryItem item) {
        File file = new File(item.getFilePath());
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Read file content
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(file));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            
            // Apply via TableIO
            TableIO.importFromData(activity, content.toString());
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareConfig(ExportHistoryItem item) {
        File file = new File(item.getFilePath());
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            android.net.Uri uri = FileProvider.getUriForFile(activity, 
                    activity.getPackageName() + ".fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getFilename());
            shareIntent.putExtra(Intent.EXTRA_TEXT, 
                    "KonaBess GPU Config: " + item.getDescription());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            activity.startActivity(Intent.createChooser(shareIntent, 
                    activity.getString(R.string.share_config)));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView filename;
        TextView timestamp;
        TextView description;
        TextView filePath;
        Chip chipType;
        MaterialButton btnApply;
        MaterialButton btnShare;
        MaterialButton btnDelete;
        MaterialButton btnCopyPath;

        ViewHolder(View view) {
            super(view);
            filename = view.findViewById(R.id.history_filename);
            timestamp = view.findViewById(R.id.history_timestamp);
            description = view.findViewById(R.id.history_description);
            filePath = view.findViewById(R.id.history_file_path);
            chipType = view.findViewById(R.id.history_chip_type);
            btnApply = view.findViewById(R.id.history_btn_apply);
            btnShare = view.findViewById(R.id.history_btn_share);
            btnDelete = view.findViewById(R.id.history_btn_delete);
            btnCopyPath = view.findViewById(R.id.history_btn_copy_path);
        }
    }
}
