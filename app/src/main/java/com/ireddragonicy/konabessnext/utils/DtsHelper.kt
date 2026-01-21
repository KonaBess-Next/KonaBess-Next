package com.ireddragonicy.konabessnext.utils

object DtsHelper {
    @JvmStatic
    fun shouldUseHex(line: String): Boolean {
        return line.contains("qcom,acd-level")
    }

    class intLine {
        @JvmField var name: String? = null
        @JvmField var value: Long = 0
    }

    class hexLine {
        @JvmField var name: String? = null
        @JvmField var value: String? = null
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_int_line_hz(line: String): intLine {
        // Simple delegator for now, logic is similar
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
    fun decode_int_line(line: String): intLine {
        val trimmedLine = line.trim()
        val eqIndex = trimmedLine.indexOf('=')
        
        if (eqIndex == -1) throw Exception("Invalid line format")
        
        val intLine = intLine()
        intLine.name = trimmedLine.substring(0, eqIndex).trim()

        // Fast extraction between < and >
        val start = trimmedLine.indexOf('<', eqIndex)
        val end = trimmedLine.indexOf('>', start)
        
        if (start != -1 && end != -1) {
            var valStr = trimmedLine.substring(start + 1, end).trim()
            
            // Handle multiple values like <0x0 587000000>
            val spaceIndex = valStr.lastIndexOf(' ')
            if (spaceIndex != -1) {
                valStr = valStr.substring(spaceIndex + 1).trim()
            }

            if (valStr.startsWith("0x")) {
                intLine.value = valStr.substring(2).toLong(16)
            } else {
                intLine.value = valStr.toLong()
            }
        } else {
            // Fallback/Legacy handling for quoted values
            val valuePart = trimmedLine.substring(eqIndex + 1)
            if (valuePart.contains("\"")) {
                intLine.value = decode_stringed_int(valuePart).toLong()
            } else {
                throw Exception("Value not found in brackets")
            }
        }

        return intLine
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_hex_line(line: String): hexLine {
        val trimmedLine = line.trim()
        val eqIndex = trimmedLine.indexOf('=')
        
        if (eqIndex == -1) throw Exception("Invalid line format")
        
        val hexLine = hexLine()
        hexLine.name = trimmedLine.substring(0, eqIndex).trim()

        val start = trimmedLine.indexOf('<', eqIndex)
        val end = trimmedLine.indexOf('>', start)
        
        if (start != -1 && end != -1) {
            hexLine.value = trimmedLine.substring(start + 1, end).trim()
        } else {
             // Fallback
             var value = trimmedLine.substring(eqIndex + 1)
             value = value.replace(";", "").trim()
             hexLine.value = value
        }

        return hexLine
    }

    @JvmStatic
    fun encodeIntOrHexLine(name: String, value: String): String {
        return "$name = <$value>;"
    }
}