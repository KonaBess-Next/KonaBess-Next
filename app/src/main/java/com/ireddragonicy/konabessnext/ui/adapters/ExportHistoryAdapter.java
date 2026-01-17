package com.ireddragonicy.konabessnext.ui.adapters;

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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.ireddragonicy.konabessnext.ui.MainActivity;
import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.core.TableIO;
import com.ireddragonicy.konabessnext.model.ExportHistoryItem;
import com.ireddragonicy.konabessnext.utils.ExportHistoryManager;
import com.ireddragonicy.konabessnext.utils.RootHelper;

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
        holder.description.setText(
                item.getDescription().isEmpty() ? activity.getString(R.string.no_description) : item.getDescription());
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
                        int pos = holder.getBindingAdapterPosition();
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
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("File Path", item.getFilePath());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(activity, R.string.path_copied, Toast.LENGTH_SHORT).show();
        });

        // Rename button
        holder.btnRename.setOnClickListener(v -> showRenameDialog(item, holder));
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
            // Robust Sharing: Copy to internal cache first to ensure FileProvider works
            // flawlessly
            // regardless of the source location (Scoped Storage workaround)
            File cachePath = new File(activity.getCacheDir(), "shared_exports");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }
            // Use original filename to preserve extension for receiving app
            File newFile = new File(cachePath, file.getName());

            // Use centralized root copy utility
            if (!RootHelper.copyFile(file.getAbsolutePath(), newFile.getAbsolutePath(), "666")) {
                Toast.makeText(activity, "Failed to copy file with Root.", Toast.LENGTH_SHORT).show();
                return;
            }

            android.net.Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", newFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);

            // Determine MIME type based on extension
            String mimeType = "*/*";
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".txt") || filename.endsWith(".dts")) {
                mimeType = "text/plain";
            } else if (filename.endsWith(".img") || filename.endsWith(".bin")) {
                mimeType = "application/octet-stream";
            }

            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getFilename());
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "KonaBess GPU Config: " + item.getDescription());

            // Critical for Android 10+: Add ClipData to ensure permissions are granted
            shareIntent.setClipData(android.content.ClipData.newRawUri(null, uri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(shareIntent,
                    activity.getString(R.string.share_config)));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, R.string.error_occur, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(ExportHistoryItem item, ViewHolder holder) {
        // Create dialog with two text inputs (filename + description)
        android.widget.LinearLayout dialogLayout = new android.widget.LinearLayout(activity);
        dialogLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);

        // Filename input
        TextInputLayout filenameLayout = new TextInputLayout(activity);
        filenameLayout.setHint(activity.getString(R.string.enter_new_filename));
        TextInputEditText filenameEdit = new TextInputEditText(activity);

        // Get current filename without extension
        String currentName = item.getFilename();
        String baseName = currentName;
        String extension = "";
        int dotIndex = currentName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = currentName.substring(0, dotIndex);
            extension = currentName.substring(dotIndex);
        }
        filenameEdit.setText(baseName);
        filenameLayout.addView(filenameEdit);
        dialogLayout.addView(filenameLayout);

        // Description input
        TextInputLayout descLayout = new TextInputLayout(activity);
        descLayout.setHint(activity.getString(R.string.edit_description));
        TextInputEditText descEdit = new TextInputEditText(activity);
        descEdit.setText(item.getDescription());
        descLayout.addView(descEdit);
        android.widget.LinearLayout.LayoutParams descParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = 24;
        descLayout.setLayoutParams(descParams);
        dialogLayout.addView(descLayout);

        final String finalExtension = extension;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.edit_export)
                .setView(dialogLayout)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String newName = filenameEdit.getText().toString().trim();
                    String newDesc = descEdit.getText().toString().trim();

                    if (!newName.isEmpty()) {
                        String newFilename = newName + finalExtension;
                        renameFile(item, newFilename, newDesc, holder);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renameFile(ExportHistoryItem item, String newFilename, String newDescription, ViewHolder holder) {
        File oldFile = new File(item.getFilePath());
        if (!oldFile.exists()) {
            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        File newFile = new File(oldFile.getParent(), newFilename);

        if (oldFile.renameTo(newFile)) {
            // Update history item
            item.setFilename(newFilename);
            item.setFilePath(newFile.getAbsolutePath());
            item.setDescription(newDescription);
            historyManager.updateItem(item);

            // Update UI
            holder.filename.setText(newFilename);
            holder.filePath.setText(newFile.getAbsolutePath());
            holder.description
                    .setText(newDescription.isEmpty() ? activity.getString(R.string.no_description) : newDescription);

            Toast.makeText(activity, R.string.changes_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, R.string.rename_failed, Toast.LENGTH_SHORT).show();
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
        MaterialButton btnRename;

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
            btnRename = view.findViewById(R.id.history_btn_rename);
        }
    }
}
