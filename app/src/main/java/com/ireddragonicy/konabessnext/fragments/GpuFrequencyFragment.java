package com.ireddragonicy.konabessnext.fragments;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.ireddragonicy.konabessnext.GpuTableEditor;
import com.ireddragonicy.konabessnext.KonaBessCore;
import com.ireddragonicy.konabessnext.MainActivity;
import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.adapters.ChipsetSelectorAdapter;

public class GpuFrequencyFragment extends Fragment {
    private LinearLayout contentContainer;
    private MainActivity.DevicePreparationListener preparationListener;
    private boolean needsReload = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        contentContainer = new LinearLayout(requireContext());
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(16, 16, 16, 16);

        loadContent();
        return contentContainer;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        preparationListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadIfNeeded();
    }

    public void markDataDirty() {
        needsReload = true;
        if (!isAdded()) {
            return;
        }
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            reloadIfNeeded();
        }
    }

    private void loadContent() {
        if (!isAdded()) {
            return;
        }

        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) {
            return;
        }

        if (KonaBessCore.isPrepared()) {
            showGpuEditor(activity);
            return;
        }

        if (activity.isDevicePreparationRunning()) {
            showLoadingState();
            attachPreparationListener(activity);
        } else {
            showPromptState(activity);
        }
    }

    private void showGpuEditor(MainActivity activity) {
        if (!isAdded()) {
            return;
        }
        contentContainer.removeAllViews();
        contentContainer.setGravity(Gravity.NO_GRAVITY);
        new GpuTableEditor.gpuTableLogic(activity, contentContainer).start();
    }

    private void showPromptState(MainActivity activity) {
        contentContainer.removeAllViews();
        contentContainer.setGravity(Gravity.CENTER);

        // Create a modern Material You card
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(12);
        card.setRadius(28);
        int cardPadding = 56;
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding);

        // Get theme color for card background
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        card.setCardBackgroundColor(typedValue.data);

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);

        // Add icon
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_developer_board);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, typedValue, true);
        icon.setColorFilter(typedValue.data);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                140, 140);
        iconParams.setMargins(0, 0, 0, 40);
        cardContent.addView(icon, iconParams);

        // Add title
        TextView title = new TextView(requireContext());
        title.setText("Detect Chipset");
        title.setTextSize(26);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        title.setTextColor(typedValue.data);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, 20);
        cardContent.addView(title, titleParams);

        // Add description message
        TextView message = new TextView(requireContext());
        message.setText(R.string.gpu_prep_prompt);
        message.setTextSize(15);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setAlpha(0.85f);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        message.setTextColor(typedValue.data);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, 40);
        cardContent.addView(message, messageParams);

        // Add Material You button
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(R.string.gpu_prep_start);
        button.setCornerRadius(32);
        button.setElevation(6);
        int buttonPadding = 24;
        button.setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding);
        button.setTextSize(16);
        button.setOnClickListener(v -> startPreparation(activity));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL;
        cardContent.addView(button, buttonParams);

        card.addView(cardContent);

        // Add card to container with margin
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(40, 0, 40, 0);
        contentContainer.addView(card, cardParams);
    }

    private void showLoadingState() {
        contentContainer.removeAllViews();
        contentContainer.setGravity(Gravity.CENTER);

        ProgressBar progressBar = new ProgressBar(requireContext());
        TextView message = new TextView(requireContext());
        message.setText(R.string.gpu_prep_loading);
        message.setPadding(0, 24, 0, 0);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        contentContainer.addView(progressBar);
        contentContainer.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showErrorState(MainActivity activity) {
        contentContainer.removeAllViews();
        contentContainer.setGravity(Gravity.CENTER);

        // Create a modern Material You error card
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(12);
        card.setRadius(28);
        int cardPadding = 56;
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding);

        // Get theme color for error card background
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorErrorContainer, typedValue, true);
        card.setCardBackgroundColor(typedValue.data);

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);

        // Add error icon
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(android.R.drawable.ic_dialog_alert);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorError, typedValue, true);
        icon.setColorFilter(typedValue.data);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                140, 140);
        iconParams.setMargins(0, 0, 0, 40);
        cardContent.addView(icon, iconParams);

        // Add title
        TextView title = new TextView(requireContext());
        title.setText("Detection Failed");
        title.setTextSize(26);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true);
        title.setTextColor(typedValue.data);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, 20);
        cardContent.addView(title, titleParams);

        // Add error message
        TextView message = new TextView(requireContext());
        message.setText(R.string.gpu_prep_failed);
        message.setTextSize(15);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setAlpha(0.85f);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnErrorContainer, typedValue, true);
        message.setTextColor(typedValue.data);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, 40);
        cardContent.addView(message, messageParams);

        // Add retry button
        MaterialButton retryButton = new MaterialButton(requireContext());
        retryButton.setText(R.string.gpu_prep_retry);
        retryButton.setCornerRadius(32);
        retryButton.setElevation(6);
        int buttonPadding = 24;
        retryButton.setPadding(buttonPadding * 2, buttonPadding, buttonPadding * 2, buttonPadding);
        retryButton.setTextSize(16);
        retryButton.setOnClickListener(v -> startPreparation(activity));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL;
        cardContent.addView(retryButton, buttonParams);

        card.addView(cardContent);

        // Add card to container with margin
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(40, 0, 40, 0);
        contentContainer.addView(card, cardParams);
    }

    private void startPreparation(MainActivity activity) {
        showLoadingState();
        attachPreparationListener(activity);
    }

    private void attachPreparationListener(MainActivity activity) {
        if (preparationListener != null) {
            return;
        }

        preparationListener = new MainActivity.DevicePreparationListener() {
            @Override
            public void onPrepared() {
                preparationListener = null;
                if (!isAdded()) {
                    return;
                }
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    showGpuEditor(activity);
                }
            }

            @Override
            public void onFailed() {
                preparationListener = null;
                if (!isAdded()) {
                    return;
                }
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    showErrorState(activity);
                }
            }

            @Override
            public void onSelectionRequired(int recommendedIndex) {
                // Keep listener attached? No, we will re-attach or use a callback in adapter?
                // Actually Selection State is intermediate. Listener likely needs to stay
                // attached
                // IF we expect "onPrepared" to fire later.
                // The new flow: onSelectionRequired -> show UI -> User Click ->
                // KonaBessCore.chooseTarget -> MainActivity.notifyPreparationSuccess ->
                // onPrepared.
                // So YES, we want onPrepared to fire.
                // But MainActivity.notifyPreparationSuccess iterates listeners and clears them.
                // Wait.
                // If I clear listener in onSelectionRequired, I won't get onPrepared.
                // But `MainActivity` clears listeners before calling callbacks?
                // Step 441 changes:
                // notifyPreparationSelection DOES NOT CLEAR listeners.
                // notifyPreparationSuccess DOES CLEAR.
                // So listener needs to persist.

                // So do NOT set preparationListener = null here.

                if (!isAdded()) {
                    return;
                }
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    showSelectionState(activity, recommendedIndex);
                }
            }
        };

        activity.ensureDevicePrepared(preparationListener);
    }

    private void showSelectionState(MainActivity activity, int recommendedIndex) {
        contentContainer.removeAllViews();
        contentContainer.setGravity(Gravity.CENTER);

        // Create a modern Material You card
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(12);
        card.setRadius(28);
        int cardPadding = 56;
        card.setContentPadding(cardPadding, cardPadding, cardPadding, cardPadding);

        // Get theme color for card background
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        card.setCardBackgroundColor(typedValue.data);

        LinearLayout cardContent = new LinearLayout(requireContext());
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);

        // Add icon
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_developer_board);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, typedValue, true);
        icon.setColorFilter(typedValue.data);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                140, 140);
        iconParams.setMargins(0, 0, 0, 40);
        cardContent.addView(icon, iconParams);

        // Add title
        TextView title = new TextView(requireContext());
        title.setText(R.string.title_select_chipset);
        title.setTextSize(24);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        title.setTextColor(typedValue.data);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, 20);
        cardContent.addView(title, titleParams);

        // Add description message
        TextView message = new TextView(requireContext());
        message.setText("Multiple chipsets found. Please select one.");
        message.setTextSize(14);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setAlpha(0.85f);
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        message.setTextColor(typedValue.data);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, 40);
        cardContent.addView(message, messageParams);

        // Add RecyclerView for list
        RecyclerView recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        ChipsetSelectorAdapter adapter = new ChipsetSelectorAdapter(
                KonaBessCore.dtbs,
                activity,
                recommendedIndex,
                dtb -> {
                    KonaBessCore.chooseTarget(dtb, activity);
                    activity.notifyPreparationSuccess();
                });
        recyclerView.setAdapter(adapter);

        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // Limit height if needed? Or let it expand content container is vertical.
        // But Card is inside contentContainer.
        cardContent.addView(recyclerView, recyclerParams);

        card.addView(cardContent);

        // Add card to container with margin
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(40, 0, 40, 0);
        contentContainer.addView(card, cardParams);
    }

    private void reloadIfNeeded() {
        if (!needsReload) {
            return;
        }
        if (contentContainer == null) {
            return;
        }
        needsReload = false;
        loadContent();
    }
}
