package com.ireddragonicy.konabessnext.utils;

import com.ireddragonicy.konabessnext.model.dts.DtsNode;
import com.ireddragonicy.konabessnext.model.dts.DtsProperty;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DtsTreeHelper {

    /**
     * Parses a raw DTS string into a root DtsNode.
     * This parser assumes a roughly standard DTS format found in KonaBess
     * (decompiled DTBs).
     * It handles:
     * - node_name {
     * - property = value;
     * - nested nodes
     * - Root node is usually implicit or "/"
     */
    public static DtsNode parse(String rawDts) {
        // Create an invisible root holder
        DtsNode root = new DtsNode("root");
        root.isExpanded = true; // Root must always be expanded
        if (rawDts == null || rawDts.isEmpty())
            return root;

        // Strip comments first to avoid parsing errors
        // Remove // comments
        rawDts = rawDts.replaceAll("//.*", "");
        // Remove /* */ comments (lazy match)
        rawDts = rawDts.replaceAll("/\\*.*?\\*/", "");

        // Simplify newlines for scanning
        String[] lines = rawDts.split("\\r?\\n");

        Stack<DtsNode> stack = new Stack<>();
        stack.push(root);

        // Simple line-based state machine might fail on multi-line properties or braces
        // on new lines
        // A character scanner is more robust but more complex.
        // Given the typical output of KonaBess's disassembler (dtc), it's usually
        // well-formatted.
        // Let's try a robust line processor but handle braces carefully.

        // Actually, a full char scanner is safer for { } nesting.

        char[] chars = rawDts.toCharArray();
        StringBuilder buffer = new StringBuilder();

        // We need to capture:
        // 1. Node start: "name {"
        // 2. Node end: "};"
        // 3. Property: "name = value;" or just "name;" (boolean prop)

        // BUT, properties can contain ';' inside strings? Unlikely in DTS usually, but
        // possible.
        // Properties can be multi-line? e.g. < \n 0x1 \n ... >

        // Strategy: Tokenize by key characters { } ;

        int i = 0;
        int len = chars.length;

        while (i < len) {
            char c = chars[i];

            if (c == '{') {
                // Start of a node. Content in buffer is the node label (e.g. "fragment@0")
                String label = buffer.toString().trim();
                // Sometimes label might have "label: node_name", we take it all.

                DtsNode newNode = new DtsNode(label);
                if (!stack.isEmpty()) {
                    stack.peek().addChild(newNode);
                }
                stack.push(newNode);
                buffer.setLength(0); // clear buffer
                i++;
            } else if (c == '}') {
                // End of node
                // Check for trailing ';' which usually follows '}' in DTS: "};"
                // But sometimes "}" alone? Standard DTS is "};"
                if (!stack.isEmpty() && stack.size() > 1) { // Don't pop the absolute root if we can avoid it, though
                                                            // logic dictates we pushed it.
                    stack.pop();
                }

                // Peek next to consume ';'
                int next = i + 1;
                while (next < len && Character.isWhitespace(chars[next]))
                    next++;
                if (next < len && chars[next] == ';') {
                    i = next; // Skip the ;
                }
                buffer.setLength(0); // clear buffer (usually whitespace)
                i++;
            } else if (c == ';') {
                // End of a property statement
                String statement = buffer.toString().trim();
                if (!statement.isEmpty()) {
                    // Two types: "prop = val" or "prop"
                    int eqIndex = statement.indexOf('=');
                    if (eqIndex != -1) {
                        String key = statement.substring(0, eqIndex).trim();
                        String val = statement.substring(eqIndex + 1).trim();
                        if (!stack.isEmpty()) {
                            stack.peek().addProperty(new DtsProperty(key, val));
                        }
                    } else {
                        // Boolean property
                        if (!stack.isEmpty()) {
                            stack.peek().addProperty(new DtsProperty(statement, ""));
                        }
                    }
                }
                buffer.setLength(0);
                i++;
            } else {
                buffer.append(c);
                i++;
            }
        }

        // If the stack has items (except root), strict parsing failed or file
        // incomplete.
        // We'll just return what we have.

        // The "invisible root" might have 1 child if the file starts with "/ { ... };"
        // Or it might have multiple if it's a fragment file.
        // We return the holder root.

        return root;
    }

    /**
     * Generates DTS string from the node tree.
     */
    public static String generate(DtsNode root) {
        StringBuilder sb = new StringBuilder();
        // Root usually has children which are the top-level nodes of the file.
        // If root itself is "root" (our dummy), we iterate its children.

        for (DtsNode child : root.children) {
            generateNode(sb, child, "");
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private static void generateNode(StringBuilder sb, DtsNode node, String indent) {
        // Node Header: name {
        sb.append(indent).append(node.name).append(" {\n");

        String childIndent = indent + "\t";

        // Properties
        for (DtsProperty prop : node.properties) {
            sb.append(childIndent).append(prop.name);
            if (prop.originalValue != null && !prop.originalValue.isEmpty()) {
                sb.append(" = ").append(prop.originalValue);
            }
            sb.append(";\n");
        }

        // Children
        for (DtsNode child : node.children) {
            generateNode(sb, child, childIndent);
        }

        // Node Footer: };
        sb.append(indent).append("};\n");
    }
}
