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
        val end = line.indexOf('>')
        if (start != -1 && end != -1 && end > start) {
            val inner = line.substring(start + 1, end).trim()
            // Handle lists like <0x0 587000000> -> take last
            val valueStr = if (inner.contains(" ")) inner.substringAfterLast(" ") else inner
            
            return try {
                if (valueStr.startsWith("0x")) {
                    // Use parseUnsignedLong or decode to handle full 32-bit uints
                    java.lang.Long.decode(valueStr) 
                } else {
                    valueStr.toLong()
                }
            } catch (e: Exception) { -1L }
        }
        return -1L
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