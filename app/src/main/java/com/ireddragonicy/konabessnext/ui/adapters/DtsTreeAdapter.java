package com.ireddragonicy.konabessnext.ui.adapters;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ireddragonicy.konabessnext.R;
import com.ireddragonicy.konabessnext.model.dts.DtsNode;
import com.ireddragonicy.konabessnext.model.dts.DtsProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class DtsTreeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements StickyHeaderInterface {

    private static final int TYPE_NODE = 0;
    private static final int TYPE_PROPERTY = 1;

    private final List<TreeItem> visibleItems = new ArrayList<>();
    private DtsNode rootNode;
    private RecyclerView recyclerView;

    // Search State
    private String searchQuery = "";
    private final List<TreeItem> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;

    public DtsTreeAdapter() {
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    public void setRootNode(DtsNode root) {
        this.rootNode = root;
        rebuildVisibleList();
    }

    private void rebuildVisibleList() {
        visibleItems.clear();
        if (rootNode != null) {
            // Use iterative approach to prevent StackOverflow on deep trees
            Stack<NodeDepth> stack = new Stack<>();
            // Push root in reverse order effectively? No, standard preorder.
            // But we want to iterate properties then children.
            // To maintain order in a stack: push LastChild ... FirstChild, then Properties.

            // Actually, we can just linearize normally.
            // But doing it iteratively for "visible items" which depends on isExpanded.

            // Let's use a custom iterative function that mimics the recursive one.
            Stack<NodeDepth> traverseStack = new Stack<>();
            traverseStack.push(new NodeDepth(rootNode, 0));

            // The issue with strict stack processing is it does depth-first.
            // But we need to process "Node itself" -> "Properties" -> "Children".
            // If we push Node, pop it, add to list.
            // If expanded: push Children (reverse order), push Properties (reverse order).

            while (!traverseStack.isEmpty()) {
                NodeDepth wrapper = traverseStack.pop();
                DtsNode node = wrapper.node;
                int depth = wrapper.depth;

                visibleItems.add(new TreeItem(node, depth));

                if (node.isExpanded) {
                    // Push children in reverse so they come out in order
                    for (int i = node.children.size() - 1; i >= 0; i--) {
                        traverseStack.push(new NodeDepth(node.children.get(i), depth + 1));
                    }

                    // Push properties in reverse
                    for (int i = node.properties.size() - 1; i >= 0; i--) {
                        DtsProperty prop = node.properties.get(i);
                        // We can't push Property to NodeDepth stack easily if NodeDepth only takes
                        // DtsNode.
                        // We need a generic wrapper or handle properties differently.
                        // Let's handle properties directly here:
                        // But wait, the list order is Node -> Properties -> Children.
                        // If we process children later, we need to add properties NOW.
                        // So we don't need to stack properties recursively (they are leaf items in this
                        // view model - no children).
                        // BUT we need to add them AFTER the node, BEFORE the children.
                        // And since we pop children later, we should add properties to visibleItems
                        // IMMEDIATELY.
                    }

                    // Correct iterative logic:
                    // 1. Add current Node to visibleItems.
                    // 2. Add all its Properties to visibleItems immediately.
                    // 3. Push Children to stack (reverse order).

                    for (DtsProperty prop : node.properties) {
                        visibleItems.add(new TreeItem(prop, depth + 1));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    // Helper for stack
    private static class NodeDepth {
        DtsNode node;
        int depth;

        NodeDepth(DtsNode n, int d) {
            this.node = n;
            this.depth = d;
        }
    }

    // ============================================================================================
    // Search Logic
    // ============================================================================================

    public boolean search(String query) {
        this.searchQuery = query == null ? "" : query;
        this.searchResults.clear();
        this.currentSearchIndex = -1;

        if (this.searchQuery.isEmpty()) {
            for (TreeItem item : visibleItems) {
                item.isMatch = false;
            }
            notifyDataSetChanged();
            return false;
        }

        if (rootNode == null)
            return false;

        Stack<DtsNode> path = new Stack<>();
        expandForMatches(rootNode, query.toLowerCase(), path);

        rebuildVisibleList();

        for (TreeItem item : visibleItems) {
            boolean isMatch = false;
            if (item.item instanceof DtsNode) {
                if (((DtsNode) item.item).name.toLowerCase().contains(query.toLowerCase())) {
                    isMatch = true;
                }
            } else if (item.item instanceof DtsProperty) {
                DtsProperty prop = (DtsProperty) item.item;
                if (prop.name.toLowerCase().contains(query.toLowerCase()) ||
                        prop.getDisplayValue().toLowerCase().contains(query.toLowerCase())) {
                    isMatch = true;
                }
            }

            item.isMatch = isMatch;
            if (isMatch) {
                searchResults.add(item);
            }
        }

        if (!searchResults.isEmpty()) {
            currentSearchIndex = 0;
            scrollToResult(searchResults.get(0));
            notifyDataSetChanged();
            return true;
        } else {
            notifyDataSetChanged();
            return false;
        }
    }

    private boolean expandForMatches(DtsNode node, String query, Stack<DtsNode> path) {
        // This is still recursive. For huge trees, we should make this iterative too.
        // But for now, let's rely on stack depth being larger than typical tree depth
        // (usually < 50 for DTS).
        // The display recursion was the main culprit if expanding ALL nodes.

        // Actually, if we had a parsing error creating infinite loop, even iterative
        // would hang (infinite loop) vs crash (stack overflow).
        // But at least it won't crash immediately.

        path.push(node);
        boolean hasMatch = false;

        if (node.name.toLowerCase().contains(query))
            hasMatch = true;

        for (DtsProperty prop : node.properties) {
            if (prop.name.toLowerCase().contains(query) || prop.getDisplayValue().toLowerCase().contains(query)) {
                hasMatch = true;
            }
        }

        for (DtsNode child : node.children) {
            if (expandForMatches(child, query, path)) {
                hasMatch = true;
            }
        }

        if (hasMatch) {
            node.isExpanded = true;
        }

        path.pop();
        return hasMatch;
    }

    public boolean nextSearchResult() {
        if (searchResults.isEmpty())
            return false;
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        scrollToResult(searchResults.get(currentSearchIndex));
        notifyDataSetChanged();
        return true;
    }

    public boolean previousSearchResult() {
        if (searchResults.isEmpty())
            return false;
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size()) % searchResults.size();
        scrollToResult(searchResults.get(currentSearchIndex));
        notifyDataSetChanged();
        return true;
    }

    public void clearSearch() {
        search(null);
    }

    private void scrollToResult(TreeItem item) {
        int index = visibleItems.indexOf(item);
        if (index != -1 && recyclerView != null) {
            recyclerView.scrollToPosition(index);
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(index, 200);
        }
    }

    // ============================================================================================

    @Override
    public int getItemViewType(int position) {
        return visibleItems.get(position).item instanceof DtsNode ? TYPE_NODE : TYPE_PROPERTY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_NODE) {
            View v = inflater.inflate(R.layout.item_dts_visual_node, parent, false);
            return new NodeViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_dts_visual_property, parent, false);
            return new PropertyViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TreeItem item = visibleItems.get(position);
        if (holder instanceof NodeViewHolder) {
            ((NodeViewHolder) holder).bind((DtsNode) item.item, item.depth, item.isMatch);
        } else if (holder instanceof PropertyViewHolder) {
            ((PropertyViewHolder) holder).bind((DtsProperty) item.item, item.depth, item.isMatch);
        }
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    // ============================================================================================
    // Sticky Header Implementation
    // ============================================================================================

    @Override
    public int getHeaderPositionForItem(int itemPosition) {
        if (itemPosition >= visibleItems.size())
            return -1;

        // If current item is a Node, it is its own header (start of section)
        if (getItemViewType(itemPosition) == TYPE_NODE) {
            return itemPosition;
        }

        // Otherwise, search backwards for the first Node (parent)
        for (int i = itemPosition - 1; i >= 0; i--) {
            if (getItemViewType(i) == TYPE_NODE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getHeaderLayout(int headerPosition) {
        return R.layout.item_dts_visual_node;
    }

    @Override
    public void bindHeaderData(View headerView, int headerPosition) {
        if (headerPosition != -1 && headerPosition < visibleItems.size()) {
            TreeItem item = visibleItems.get(headerPosition);
            if (item.item instanceof DtsNode) {
                // Reuse NodeViewHolder logic but for a detached view
                // We create a temporary holder just for binding
                NodeViewHolder holder = new NodeViewHolder(headerView);
                // Bind with current state.
                // Note: Click listeners on sticky header usually don't work with simple
                // ItemDecoration drawing.
                // Ideally user clicks the "real" item if it's visible, or we need touch
                // interception.
                // For "Sticky Note" visual context, just binding data is enough.
                holder.bind((DtsNode) item.item, item.depth, item.isMatch);

                // Override text to show full path
                holder.nameText.setText(((DtsNode) item.item).getFullPath());

                // Force opaque background for sticky header so text doesn't bleed through
                // Use default window background or surface color
                headerView.setBackgroundColor(Color.parseColor("#FF202020")); // Dark grey/black for dark mode
                // Ideally resolving ?attr/colorSurface would be better but requires Context

                // Try resolving colorSurface
                android.util.TypedValue outValue = new android.util.TypedValue();
                headerView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface,
                        outValue, true);
                if (outValue.resourceId != 0) {
                    headerView.setBackgroundResource(outValue.resourceId);
                } else {
                    headerView.setBackgroundColor(outValue.data); // If it's a color value
                }

                // Ensure it's opaque if surface is transparent (rare)
                // Also, let's add a small elevation/shadow line?
                headerView.setElevation(8f);
            }
        }
    }

    @Override
    public boolean isHeader(int itemPosition) {
        return getItemViewType(itemPosition) == TYPE_NODE;
    }

    @Override
    public void onHeaderClicked(int headerPosition) {
        if (headerPosition != -1 && headerPosition < visibleItems.size()) {
            TreeItem item = visibleItems.get(headerPosition);
            if (item.item instanceof DtsNode) {
                DtsNode node = (DtsNode) item.item;
                node.isExpanded = !node.isExpanded;
                rebuildVisibleList();

                // If we collapsed it, we might be way down the list. Scroll to the header.
                if (!node.isExpanded) {
                    scrollToResult(item);
                }
            }
        }
    }

    // ============================================================================================
    // View Holders
    // ============================================================================================

    class NodeViewHolder extends RecyclerView.ViewHolder {
        private final View indentation;
        private final ImageView expandIcon;
        private final TextView nameText;

        public NodeViewHolder(@NonNull View itemView) {
            super(itemView);
            indentation = itemView.findViewById(R.id.node_indentation);
            expandIcon = itemView.findViewById(R.id.btn_expand);
            nameText = itemView.findViewById(R.id.node_name);

            itemView.setOnClickListener(v -> toggleExpand());
            expandIcon.setOnClickListener(v -> toggleExpand());
        }

        private void toggleExpand() {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            TreeItem item = visibleItems.get(pos);
            DtsNode node = (DtsNode) item.item;
            node.isExpanded = !node.isExpanded;

            rebuildVisibleList();
        }

        public void bind(DtsNode node, int depth, boolean isMatch) {
            indentation.getLayoutParams().width = depth * 40;
            indentation.requestLayout();

            if (isMatch && !searchQuery.isEmpty()) {
                nameText.setText(highlightText(node.name, searchQuery));
            } else {
                nameText.setText(node.name);
            }
            expandIcon.setRotation(node.isExpanded ? 0 : -90);

            if (currentSearchIndex != -1 && searchResults.size() > currentSearchIndex &&
                    searchResults.get(currentSearchIndex).item == node) {
                itemView.setBackgroundColor(Color.parseColor("#4Dffeb3b"));
            } else {
                android.util.TypedValue outValue = new android.util.TypedValue();
                itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue,
                        true);
                itemView.setBackgroundResource(outValue.resourceId);
            }
        }
    }

    class PropertyViewHolder extends RecyclerView.ViewHolder {
        private final View indentation;
        private final TextView keyText;
        private final EditText valueInput;
        private TextWatcher currentWatcher;

        public PropertyViewHolder(@NonNull View itemView) {
            super(itemView);
            indentation = itemView.findViewById(R.id.prop_indentation);
            keyText = itemView.findViewById(R.id.prop_key);
            valueInput = itemView.findViewById(R.id.prop_value);
        }

        public void bind(DtsProperty prop, int depth, boolean isMatch) {
            indentation.getLayoutParams().width = depth * 40;
            indentation.requestLayout();

            if (isMatch && !searchQuery.isEmpty()) {
                keyText.setText(highlightText(prop.name, searchQuery));
            } else {
                keyText.setText(prop.name);
            }

            if (currentWatcher != null) {
                valueInput.removeTextChangedListener(currentWatcher);
            }

            String displayVal = prop.getDisplayValue();
            valueInput.setText(displayVal);

            currentWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    prop.updateFromDisplayValue(s.toString());
                }
            };
            valueInput.addTextChangedListener(currentWatcher);

            if (currentSearchIndex != -1 && searchResults.size() > currentSearchIndex &&
                    searchResults.get(currentSearchIndex).item == prop) {
                itemView.setBackgroundColor(Color.parseColor("#4Dffeb3b"));
            } else {
                android.util.TypedValue outValue = new android.util.TypedValue();
                itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue,
                        true);
                itemView.setBackgroundResource(outValue.resourceId);
            }
        }
    }

    private SpannableString highlightText(String text, String query) {
        SpannableString spannable = new SpannableString(text);
        if (query == null || query.isEmpty())
            return spannable;

        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int start = lowerText.indexOf(lowerQuery);
        while (start != -1) {
            int end = start + lowerQuery.length();
            spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#ffeb3b")), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = lowerText.indexOf(lowerQuery, end);
        }
        return spannable;
    }

    static class TreeItem {
        Object item; // DtsNode or DtsProperty
        int depth;
        boolean isMatch = false;

        public TreeItem(Object item, int depth) {
            this.item = item;
            this.depth = depth;
        }
    }
}
