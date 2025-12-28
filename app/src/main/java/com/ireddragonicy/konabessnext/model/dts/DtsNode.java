package com.ireddragonicy.konabessnext.model.dts;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Node in the DTS tree.
 * Example: node_name { ... };
 */
public class DtsNode {
    public String name;
    public List<DtsNode> children;
    public List<DtsProperty> properties;
    public boolean isExpanded;

    public DtsNode parent;

    public DtsNode(String name) {
        this.name = name != null ? name.trim() : "root";
        this.children = new ArrayList<>();
        this.properties = new ArrayList<>();
        this.isExpanded = false; // Default collapsed
    }

    public void addChild(DtsNode child) {
        child.parent = this;
        children.add(child);
    }

    public void addProperty(DtsProperty prop) {
        properties.add(prop);
    }

    public String getFullPath() {
        if (parent == null || parent.name.equals("root")) {
            return name;
        }
        String parentPath = parent.getFullPath();
        if (parentPath.equals("/")) {
            return "/" + name;
        }
        return parentPath + "/" + name;
    }
}
