package com.ireddragonicy.konabessnext.ui.adapters;

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

public class ActionCardAdapter extends RecyclerView.Adapter<ActionCardAdapter.ViewHolder> {

    public static class ActionItem {
        public int iconResId;
        public String title;
        public String description;
        public boolean enabled;

        public ActionItem(int iconResId, String title, String description) {
            this(iconResId, title, description, true);
        }

        public ActionItem(int iconResId, String title, String description, boolean enabled) {
            this.iconResId = iconResId;
            this.title = title;
            this.description = description;
            this.enabled = enabled;
        }
    }

    private final List<ActionItem> items;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public ActionCardAdapter(List<ActionItem> items) {
        this.items = items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.action_item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActionItem item = items.get(position);
        holder.icon.setImageResource(item.iconResId);
        holder.title.setText(item.title);
        holder.description.setText(item.description);
        holder.card.setEnabled(item.enabled);
        holder.card.setClickable(item.enabled);
        holder.card.setFocusable(item.enabled);
        
        // Simple disabled visual state
        holder.card.setAlpha(item.enabled ? 1f : 0.5f);

        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener == null) {
                    return;
                }
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                ActionItem actionItem = items.get(adapterPosition);
                if (actionItem.enabled) {
                    clickListener.onItemClick(adapterPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView icon;
        TextView title;
        TextView description;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
        }
    }
}




