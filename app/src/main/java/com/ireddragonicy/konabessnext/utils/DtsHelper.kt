package com.ireddragonicy.konabessnext.utils

object DtsHelper {
    class IntLine(val name: String, val value: Long)
    class HexLine(val name: String, val value: String)

    @JvmStatic
    fun shouldUseHex(line: String): Boolean {
        return line.contains("qcom,acd-level")
    }

    /**
     * Universal extractor for "property = <value>;" lines.
     * Handles decimal, hex (0x), and simple arrays.
     */
    @JvmStatic
    fun extractLongValue(line: String): Long {
        val start = line.indexOf('<')
        val end = line.indexOf('>', start + 1)
        if (start == -1 || end == -1) return -1L

        val inner = line.substring(start + 1, end).trim()
        if (inner.isEmpty()) return -1L

        val parts = inner.split(Regex("\\s+"))
        return try {
            if (parts.size == 1) {
                // Single cell: <0x23c34600>
                val v = parts[0]
                if (v.startsWith("0x", ignoreCase = true)) java.lang.Long.decode(v)
                else v.toLong()
            } else {
                // Multi-cell: <0x0 0x23c34600> → combine big-endian 32-bit cells
                var result = 0L
                for (part in parts) {
                    val cellVal = if (part.startsWith("0x", ignoreCase = true))
                        java.lang.Long.decode(part)
                    else
                        part.toLong()
                    result = (result shl 32) or (cellVal and 0xFFFFFFFFL)
                }
                result
            }
        } catch (e: Exception) { -1L }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_int_line_hz(line: String): IntLine {
        return decode_int_line(line)
    }

    //To handle dtc bug
    @JvmStatic
    @Throws(Exception::class)
    fun decode_stringed_int(input: String): Int {
        var processed = input.replace("\"", "")
            .replace(";", "")
            .replace("\\a", "\u0007")
            .replace("\\b", "\b")
            .replace("\\f", "\u000c")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\v", "\u000b") 
            .replace("\\\\", "\\")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .trim { it <= ' ' }
        val chars = processed.toCharArray()
        if (chars.size != 3) throw Exception()
        var ret = 0
        for (i in 1..chars.size) {
            ret += (chars[chars.size - i].code * Math.pow(256.0, i.toDouble())).toInt()
        }
        return ret
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_int_line(line: String): IntLine {
        val trimmedLine = line.trim()
        val eqIndex = trimmedLine.indexOf('=')
        
        if (eqIndex == -1) throw Exception("Invalid line format")
        
        val name = trimmedLine.substring(0, eqIndex).trim()
        val value = extractLongValue(line)
        
        // Validation similar to original extraction to throw exceptions if really needed,
        // but extractLongValue is safe. The original code threw exceptions.
        // If value is -1L, it might be an error or just -1. 
        // For compatibility with original throw semantics:
        
        if (value == -1L && !line.contains("-1")) {
             // Try fallback for stringed int if extractLongValue failed but it had content
             val valuePart = trimmedLine.substring(eqIndex + 1)
             if (valuePart.contains("\"")) {
                 return IntLine(name, decode_stringed_int(valuePart).toLong())
             }
        }
        
        return IntLine(name, value)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_hex_line(line: String): HexLine {
        val trimmedLine = line.trim()
        val eqIndex = trimmedLine.indexOf('=')
        
        if (eqIndex == -1) throw Exception("Invalid line format")
        
        val name = trimmedLine.substring(0, eqIndex).trim()
        val start = trimmedLine.indexOf('<', eqIndex)
        val end = trimmedLine.indexOf('>', start)
        
        val value = if (start != -1 && end != -1) {
            trimmedLine.substring(start + 1, end).trim()
        } else {
             var v = trimmedLine.substring(eqIndex + 1)
             v = v.replace(";", "").trim()
             v
        }

        return HexLine(name, value)
    }

    @JvmStatic
    fun encodeIntOrHexLine(name: String, value: String): String {
        return "$name = <$value>;"
    }
}