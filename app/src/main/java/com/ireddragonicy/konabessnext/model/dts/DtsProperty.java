package com.ireddragonicy.konabessnext.model.dts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a single property line in a DTS file.
 * Example: property = <value>;
 */
public class DtsProperty {
    public String name;
    public String originalValue;
    public boolean isHexArray;

    public DtsProperty(String name, String originalValue) {
        this.name = name != null ? name.trim() : "";
        this.originalValue = originalValue != null ? originalValue.trim() : "";
        this.isHexArray = detectHexArray(this.originalValue);
    }

    private boolean detectHexArray(String val) {
        // Check if enclosed in < ... >
        if (val == null)
            return false;
        String v = val.trim();
        if (!v.startsWith("<") || !v.endsWith(">"))
            return false;

        // It must contain at least one 0x number to be worth converting
        // But strictly it should be a list of numbers.
        // We assume anything inside < > is a potential number list in DTS.
        // A standard DTS array is like <0x1 0x2 123>
        return true;
    }

    public String getDisplayValue() {
        if (isHexArray) {
            try {
                // Strip < >
                String inner = originalValue.trim();
                inner = inner.substring(1, inner.length() - 1).trim();

                String[] tokens = inner.split("\\s+");
                StringBuilder sb = new StringBuilder();

                for (String token : tokens) {
                    if (token.isEmpty())
                        continue;

                    if (token.toLowerCase().startsWith("0x")) {
                        try {
                            String hex = token.substring(2);
                            // Use Long.parseUnsignedLong to handle 0xffffffff (bigger than signed long max)
                            long decoded = Long.parseUnsignedLong(hex, 16);
                            sb.append(Long.toUnsignedString(decoded)).append(" ");
                        } catch (NumberFormatException e) {
                            // Failed to parse, keep original
                            sb.append(token).append(" ");
                        }
                    } else {
                        // Keep decimal or other tokens as is
                        sb.append(token).append(" ");
                    }
                }
                return sb.toString().trim();
            } catch (Exception e) {
                return originalValue;
            }
        }
        return originalValue;
    }

    public void updateFromDisplayValue(String displayValue) {
        if (displayValue == null)
            displayValue = "";

        if (isHexArray) {
            // Assume the user is inputting a space-separated list of decimal numbers (or
            // hex if they want)
            // We want to convert everything back to hex format <0x...> if it looks like a
            // number
            // BUT careful: if the user typed 0x123 explicitly, we should keep it?
            // Or if they typed "label", strict conversion might break.

            // For this specific feature request: "Display decimal... convert back to hex"

            String[] tokens = displayValue.trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            sb.append("<");

            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.isEmpty())
                    continue;

                // If user typed a decimal number, convert to 0x...
                // If user typed 0x..., keep it.
                // If not a number, keep it.

                if (token.matches("^-?\\d+$")) {
                    try {
                        long val = Long.parseLong(token);
                        // Use hex format
                        sb.append(String.format("0x%x", val));
                    } catch (NumberFormatException e) {
                        try {
                            // Try unsigned
                            long val = Long.parseUnsignedLong(token);
                            sb.append(String.format("0x%x", val));
                        } catch (Exception ex) {
                            sb.append(token);
                        }
                    }
                } else {
                    sb.append(token);
                }

                if (i < tokens.length - 1) {
                    sb.append(" ");
                }
            }
            sb.append(">");
            this.originalValue = sb.toString();
        } else {
            this.originalValue = displayValue;
        }
    }
}
