package com.ireddragonicy.konabessnext.editor.highlight;

/**
 * Color scheme for the DTS code editor.
 * Dark theme optimized for readability.
 */
public class EditorColorScheme {
    // Syntax colors
    public int textColor = 0xFFE0E0E0; // Default text - light gray
    public int commentColor = 0xFF6A9955; // Comments - muted green
    public int keywordColor = 0xFFBB86FC; // Keywords - purple
    public int stringColor = 0xFFCE9178; // Strings - orange/brown
    public int numberColor = 0xFFB5CEA8; // Numbers - light green
    public int propertyColor = 0xFF9CDCFE; // Property names - light blue
    public int preprocessorColor = 0xFFC586C0; // Preprocessor - magenta/pink
    public int bracketColor = 0xFFFFD700; // Brackets - gold
    public int nodeColor = 0xFF4EC9B0; // Node names - teal
    public int operatorColor = 0xFFD4D4D4; // Operators - white/gray
    public int phandleColor = 0xFF4FC1FF; // Phandle references - bright blue

    // UI colors
    public int lineNumberColor = 0xFF6A6A6A;
    public int lineNumberBgColor = 0xFF1A1A1A;
    public int cursorColor = 0xFF448AFF;
    public int selectionColor = 0x50448AFF;
    public int currentLineColor = 0x15FFFFFF;

    private static EditorColorScheme instance;

    public static EditorColorScheme getDefault() {
        if (instance == null) {
            instance = new EditorColorScheme();
        }
        return instance;
    }
}
