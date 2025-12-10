package com.ireddragonicy.konabessnext.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import com.ireddragonicy.konabessnext.R;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    public static class SettingItem {
        public int iconResId;
        public String title;
        public String description;
        public String value;
        public boolean isSwitch;
        public boolean isChecked;

        public SettingItem(int iconResId, String title, String description, String value) {
            this.iconResId = iconResId;
            this.title = title;
            this.description = description;
            this.value = value;
            this.isSwitch = false;
        }

        // Constructor for Switch Item
        public SettingItem(int iconResId, String title, String description, boolean isChecked) {
            this.iconResId = iconResId;
            this.title = title;
            this.description = description;
            this.value = "";
            this.isSwitch = true;
            this.isChecked = isChecked;
        }
    }

    private List<SettingItem> items;
    private Context context;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
        // Add support for switch toggles if needed, but usually click is enough
    }

    public SettingsAdapter(List<SettingItem> items, Context context) {
        this.items = items;
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.setting_item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingItem item = items.get(position);

        holder.icon.setImageResource(item.iconResId);
        holder.title.setText(item.title);
        holder.description.setText(item.description);

        if (item.isSwitch) {
            holder.value.setVisibility(View.GONE);
            holder.chevron.setVisibility(View.GONE);
            holder.switchWidget.setVisibility(View.VISIBLE);
            holder.switchWidget.setChecked(item.isChecked);

            // Handle switch clicks strictly
            holder.switchWidget.setOnClickListener(v -> {
                if (clickListener != null) {
                    item.isChecked = holder.switchWidget.isChecked(); // Update model
                    clickListener.onItemClick(position);
                }
            });

            // Also handle card click to toggle switch
            holder.card.setOnClickListener(v -> {
                holder.switchWidget.toggle();
                if (clickListener != null) {
                    item.isChecked = holder.switchWidget.isChecked(); // Update model
                    clickListener.onItemClick(position);
                }
            });

        } else {
            holder.value.setVisibility(View.VISIBLE);
            holder.value.setText(item.value);
            holder.chevron.setVisibility(View.VISIBLE);
            holder.switchWidget.setVisibility(View.GONE);

            holder.card.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(position);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItem(int position, String newValue) {
        if (position >= 0 && position < items.size()) {
            items.get(position).value = newValue;
            notifyItemChanged(position);
        }
    }

    public void updateSwitch(int position, boolean isChecked) {
        if (position >= 0 && position < items.size()) {
            items.get(position).isChecked = isChecked;
            notifyItemChanged(position);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView icon;
        TextView title;
        TextView description;
        TextView value;
        ImageView chevron;
        com.google.android.material.materialswitch.MaterialSwitch switchWidget;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
            value = itemView.findViewById(R.id.value);
            chevron = itemView.findViewById(R.id.chevron);
            switchWidget = itemView.findViewById(R.id.switch_widget);
        }
    }
}
