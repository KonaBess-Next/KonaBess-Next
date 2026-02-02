package com.ireddragonicy.konabessnext.model.dts

import java.lang.StringBuilder
import java.util.regex.Pattern

/**
 * Represents a single property line in a DTS file.
 * Example: property = <value>;
 */
class DtsProperty(name: String?, originalValue: String?) {
    @JvmField
    var name: String = name?.trim() ?: ""
    
    @JvmField
    var originalValue: String = originalValue?.trim() ?: ""
    
    @JvmField
    var isHexArray: Boolean = detectHexArray(this.originalValue)

    private fun detectHexArray(value: String): Boolean {
        // Check if enclosed in < ... >
        if (value.isEmpty()) return false
        val v = value.trim()
        if (!v.startsWith("<") || !v.endsWith(">")) return false

        // It must contain at least one 0x number to be worth converting
        // But strictly it should be a list of numbers.
        // We assume anything inside < > is a potential number list in DTS.
        // A standard DTS array is like <0x1 0x2 123>
        return true
    }

    fun getDisplayValue(): String {
        if (isHexArray) {
            try {
                // Strip < >
                var inner = originalValue.trim()
                if (inner.length > 2) {
                    inner = inner.substring(1, inner.length - 1).trim()
                } else {
                    return ""
                }

                val tokens = inner.split("\\s+".toRegex())
                val sb = StringBuilder()

                for (token in tokens) {
                    if (token.isEmpty()) continue

                    if (token.startsWith("0x", ignoreCase = true)) {
                        try {
                            val hex = token.substring(2)
                            // Use Long.parseUnsignedLong to handle 0xffffffff (bigger than signed long max)
                            val decoded = java.lang.Long.parseUnsignedLong(hex, 16)
                            sb.append(java.lang.Long.toUnsignedString(decoded)).append(" ")
                        } catch (e: NumberFormatException) {
                            // Failed to parse, keep original
                            sb.append(token).append(" ")
                        }
                    } else {
                        // Keep decimal or other tokens as is
                        sb.append(token).append(" ")
                    }
                }
                return sb.toString().trim()
            } catch (e: Exception) {
                return originalValue
            }
        }
        return originalValue
    }

    fun updateFromDisplayValue(displayValue: String?) {
        var dVal = displayValue ?: ""

        if (isHexArray) {
            // Assume the user is inputting a space-separated list of decimal numbers (or hex if they want)
            // We want to convert everything back to hex format <0x...> if it looks like a number
            
            val tokens = dVal.trim().split("\\s+".toRegex())
            val sb = StringBuilder()
            sb.append("<")

            for (i in tokens.indices) {
                val token = tokens[i]
                if (token.isEmpty()) continue

                if (token.matches(Regex("^-?\\d+$"))) {
                    try {
                        val value = token.toLong()
                        // Use hex format
                        sb.append(String.format("0x%x", value))
                    } catch (e: NumberFormatException) {
                        try {
                            // Try unsigned
                            val value = java.lang.Long.parseUnsignedLong(token)
                            sb.append(String.format("0x%x", value))
                        } catch (ex: Exception) {
                            sb.append(token)
                        }
                    }
                } else {
                    sb.append(token)
                }

                if (i < tokens.size - 1) {
                    sb.append(" ")
                }
            }
            sb.append(">")
            this.originalValue = sb.toString()
        } else {
            this.originalValue = dVal
        }
    }
}
