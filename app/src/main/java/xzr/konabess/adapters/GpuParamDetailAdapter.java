package xzr.konabess.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import xzr.konabess.R;

public class GpuParamDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_BACK = 0;
    private static final int VIEW_TYPE_STATS_GROUP = 1;
    private static final int VIEW_TYPE_SINGLE_PARAM = 2;

    public static class ParamDetailItem {
        public String title;
        public String value;
        public String paramName; // DTS parameter name
        public int iconRes;
        public boolean isBackButton;
        public boolean isStatsGroup;
        public List<StatItem> statItems; // For grouped stats

        public ParamDetailItem(String title, String value, String paramName, int iconRes) {
            this.title = title;
            this.value = value;
            this.paramName = paramName;
            this.iconRes = iconRes;
            this.isBackButton = false;
            this.isStatsGroup = false;
        }

        public ParamDetailItem(String title, int iconRes, boolean isBackButton) {
            this.title = title;
            this.value = "";
            this.paramName = "";
            this.iconRes = iconRes;
            this.isBackButton = isBackButton;
            this.isStatsGroup = false;
        }

        // Constructor for stats group
        public ParamDetailItem(List<StatItem> statItems) {
            this.statItems = statItems;
            this.isStatsGroup = true;
            this.isBackButton = false;
        }
    }

    public static class StatItem {
        public String label;
        public String value;
        public String paramName;
        public int iconRes;
        public int position; // Original position in data

        public StatItem(String label, String value, String paramName, int iconRes, int position) {
            this.label = label;
            this.value = value;
            this.paramName = paramName;
            this.iconRes = iconRes;
            this.position = position;
        }
    }

    private List<ParamDetailItem> items;
    private Context context;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public GpuParamDetailAdapter(List<ParamDetailItem> items, Context context) {
        this.items = items;
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        ParamDetailItem item = items.get(position);
        if (item.isBackButton) {
            return VIEW_TYPE_BACK;
        } else if (item.isStatsGroup) {
            return VIEW_TYPE_STATS_GROUP;
        } else {
            return VIEW_TYPE_SINGLE_PARAM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_STATS_GROUP) {
            View view = LayoutInflater.from(context).inflate(R.layout.gpu_param_modern_group, parent, false);
            return new StatsGroupViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.gpu_param_detail_card, parent, false);
            return new ParamViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        ParamDetailItem item = items.get(position);

        if (holder instanceof StatsGroupViewHolder) {
            bindStatsGroup((StatsGroupViewHolder) holder, item);
        } else if (holder instanceof ParamViewHolder) {
            bindParam((ParamViewHolder) holder, item, position);
        }
    }

    private void bindStatsGroup(StatsGroupViewHolder holder, ParamDetailItem item) {
        // Clear previous stats
        holder.statsRow.removeAllViews();

        // Add each stat card
        for (final StatItem stat : item.statItems) {
            View statCard = LayoutInflater.from(context).inflate(R.layout.gpu_param_stat_card, holder.statsRow, false);
            
            ImageView icon = statCard.findViewById(R.id.stat_icon);
            TextView label = statCard.findViewById(R.id.stat_label);
            TextView value = statCard.findViewById(R.id.stat_value);
            MaterialCardView card = statCard.findViewById(R.id.stat_card);

            icon.setImageResource(stat.iconRes);
            label.setText(stat.label);
            value.setText(stat.value);

            // Apply primary color to icon
            int primary = MaterialColors.getColor(card,
                    com.google.android.material.R.attr.colorPrimary);
            icon.setColorFilter(primary);

            // Set click listener
            statCard.findViewById(R.id.stat_content).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickListener != null) {
                        clickListener.onItemClick(stat.position);
                    }
                }
            });

            holder.statsRow.addView(statCard);
        }

        // Hide back button in this view (it's shown separately)
        holder.backButtonCard.setVisibility(View.GONE);
    }

    private void bindParam(ParamViewHolder holder, ParamDetailItem item, final int position) {
        holder.title.setText(item.title);
        holder.value.setText(item.value);
        holder.icon.setImageResource(item.iconRes);

        if (item.isBackButton) {
            // Style back button differently
            int surfaceVariant = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorSurfaceVariant);
            int onSurfaceVariant = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);

            holder.card.setCardBackgroundColor(surfaceVariant);
            holder.title.setTextColor(onSurfaceVariant);
            holder.value.setVisibility(View.GONE);
            holder.chevron.setVisibility(View.GONE);
            holder.icon.setColorFilter(onSurfaceVariant);
        } else {
            // Style regular param items
            int surface = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorSurface);
            int onSurface = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorOnSurface);
            int onSurfaceVariant = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            int primary = MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorPrimary);

            holder.card.setCardBackgroundColor(surface);
            holder.title.setTextColor(onSurface);
            holder.value.setTextColor(onSurfaceVariant);
            holder.value.setVisibility(View.VISIBLE);
            holder.chevron.setVisibility(View.VISIBLE);
            holder.chevron.setColorFilter(onSurfaceVariant);
            holder.icon.setColorFilter(primary);
        }

        holder.cardContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    clickListener.onItemClick(position);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ParamViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        View cardContent;
        ImageView icon;
        TextView title;
        TextView value;
        ImageView chevron;

        ParamViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            cardContent = itemView.findViewById(R.id.card_content);
            icon = itemView.findViewById(R.id.param_icon);
            title = itemView.findViewById(R.id.param_title);
            value = itemView.findViewById(R.id.param_value);
            chevron = itemView.findViewById(R.id.chevron_icon);
        }
    }

    static class StatsGroupViewHolder extends RecyclerView.ViewHolder {
        LinearLayout statsRow;
        MaterialCardView backButtonCard;

        StatsGroupViewHolder(View itemView) {
            super(itemView);
            statsRow = itemView.findViewById(R.id.stats_row);
            backButtonCard = itemView.findViewById(R.id.back_button_card);
        }
    }

    public static int getIconForParam(String paramName) {
        if (paramName == null) {
            return R.drawable.ic_back;
        }

        if (paramName.contains("gpu-freq") || paramName.contains("frequency")) {
            return R.drawable.ic_frequency;
        } else if (paramName.contains("level") || paramName.contains("cx-level")) {
            return R.drawable.ic_voltage;
        } else if (paramName.contains("bus")) {
            return R.drawable.ic_bus;
        } else if (paramName.contains("acd")) {
            return R.drawable.ic_acd;
        } else if (paramName.contains("power") || paramName.contains("pwr")) {
            return R.drawable.ic_power;
        } else {
            return R.drawable.ic_settings;
        }
    }
}
