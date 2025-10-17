package xzr.konabess.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

import xzr.konabess.KonaBessCore;
import xzr.konabess.R;

public class ChipsetSelectorAdapter extends RecyclerView.Adapter<ChipsetSelectorAdapter.ViewHolder> {

    public interface OnChipsetSelectedListener {
        void onChipsetSelected(KonaBessCore.Dtb dtb);
    }

    private final List<KonaBessCore.Dtb> dtbList;
    private final Activity activity;
    private final int recommendedIndex;
    private final Integer currentlySelectedId; // Can be null
    private final OnChipsetSelectedListener listener;

    // Constructor for initial device preparation (no currently selected)
    public ChipsetSelectorAdapter(List<KonaBessCore.Dtb> dtbList, Activity activity, 
                                  int recommendedIndex, OnChipsetSelectedListener listener) {
        this(dtbList, activity, recommendedIndex, null, listener);
    }

    // Constructor for chipset switching (with currently selected)
    public ChipsetSelectorAdapter(List<KonaBessCore.Dtb> dtbList, Activity activity, 
                                  int recommendedIndex, Integer currentlySelectedId,
                                  OnChipsetSelectedListener listener) {
        this.dtbList = dtbList;
        this.activity = activity;
        this.recommendedIndex = recommendedIndex;
        this.currentlySelectedId = currentlySelectedId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chipset_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KonaBessCore.Dtb dtb = dtbList.get(position);
        
        // Set chipset name
        String chipsetName = dtb.id + " " + xzr.konabess.ChipInfo.name2chipdesc(dtb.type, activity);
        holder.chipsetName.setText(chipsetName);
        
        // Set subtitle
        holder.chipsetSubtitle.setText("DTB Index: " + dtb.id);
        
        // Show recommended badge if this is the recommended chipset
        if (dtb.id == recommendedIndex) {
            holder.recommendedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.recommendedBadge.setVisibility(View.GONE);
        }
        
        // Show currently selected badge if this is the currently selected chipset
        if (currentlySelectedId != null && dtb.id == currentlySelectedId) {
            holder.selectedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.selectedBadge.setVisibility(View.GONE);
        }
        
        // Set click listener
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChipsetSelected(dtb);
            }
        });
        
        // Add subtle elevation on press
        holder.cardView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    holder.cardView.setCardElevation(8f);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    holder.cardView.setCardElevation(0f);
                    break;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return dtbList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView chipsetIcon;
        MaterialTextView chipsetName;
        MaterialTextView chipsetSubtitle;
        Chip recommendedBadge;
        Chip selectedBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            chipsetIcon = itemView.findViewById(R.id.chipset_icon);
            chipsetName = itemView.findViewById(R.id.chipset_name);
            chipsetSubtitle = itemView.findViewById(R.id.chipset_subtitle);
            recommendedBadge = itemView.findViewById(R.id.recommended_badge);
            selectedBadge = itemView.findViewById(R.id.selected_badge);
        }
    }
}
