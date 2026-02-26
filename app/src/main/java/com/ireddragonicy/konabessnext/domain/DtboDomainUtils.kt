package com.ireddragonicy.konabessnext.domain

import com.ireddragonicy.konabessnext.model.dts.DtsNode

object DtboDomainUtils {
    fun isFragmentNode(node: DtsNode): Boolean = node.name.startsWith("fragment@")

    fun parseFragmentIndex(name: String): Int {
        val suffix = name.substringAfter("fragment@", "")
        return suffix.toIntOrNull() ?: suffix.toIntOrNull(16) ?: -1
    }

    fun extractSingleLong(rawValue: String): Long {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            val parts = inner.split(Regex("\\s+"))
            return try {
                if (parts.size == 1) {
                    val v = parts[0].trim()
                    if (v.startsWith("0x", ignoreCase = true)) java.lang.Long.decode(v)
                    else v.toLongOrNull() ?: 0L
                } else {
                    var result = 0L
                    for (part in parts) {
                        val cell = part.trim()
                        val cellVal = if (cell.startsWith("0x", ignoreCase = true))
                            java.lang.Long.decode(cell) else cell.toLong()
                        result = (result shl 32) or (cellVal and 0xFFFFFFFFL)
                    }
                    result
                }
            } catch (_: Exception) { 0L }
        }
        return try {
            if (trimmed.startsWith("0x", ignoreCase = true)) java.lang.Long.decode(trimmed)
            else trimmed.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun extractSingleInt(rawValue: String): Int = extractSingleLong(rawValue).toInt()

    fun updateNodePropertyHexSafe(node: DtsNode, propertyName: String, newValue: String): Boolean {
        val existingProp = node.getProperty(propertyName)
        if (existingProp == null) {
            val numeric = newValue.trim().toLongOrNull()
            val formatted = if (numeric != null) "<0x${numeric.toString(16)}>" else newValue
            node.setProperty(propertyName, formatted)
            return true
        }

        if (existingProp.isHexArray) {
            existingProp.updateFromDisplayValue(newValue)
        } else {
            val original = existingProp.originalValue.trim()
            val open = original.indexOf('<')
            val close = original.indexOf('>', open + 1)
            if (open != -1 && close != -1) {
                val currentCellToken = original.substring(open + 1, close).trim().split(Regex("\\s+")).firstOrNull()
                val numeric = newValue.trim().toLongOrNull()
                val formatted = if (numeric != null && currentCellToken?.startsWith("0x", ignoreCase = true) == true) {
                    "0x${numeric.toString(16)}"
                } else {
                    newValue.trim()
                }
                existingProp.originalValue = original.substring(0, open + 1) + formatted + original.substring(close)
            } else {
                existingProp.originalValue = newValue
            }
        }
        return true
    }
}
