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
        var line = line
        val intLine = intLine()
        line = line.trim { it <= ' ' }
        var i: Int = 0
        while (i < line.length) {
            if (line.startsWith("=", i)) {
                break
            }
            i++
        }
        if (i == line.length) throw Exception()
        intLine.name = line.substring(0, i).trim { it <= ' ' }

        var value = line.substring(i + 1)
        value = value.replace("<0x0 ", "")
            .replace(">", "")
            .replace(";", "")

        if (value.contains("0x")) {
            value = value.replace("0x", "").trim { it <= ' ' }
            intLine.value = value.toLong(16)
        } else {
            value = value.trim { it <= ' ' }
            intLine.value = value.toLong()
        }

        return intLine
    }

    //To handle dtc bug
    @JvmStatic
    @Throws(Exception::class)
    fun decode_stringed_int(input: String): Int {
        var input = input
        input = input.replace("\"", "")
            .replace(";", "")
            .replace("\\a", "\u0007")
            .replace("\\b", "\b")
            .replace("\\f", "\u000c")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\v", "\u000b") // \11 is \v vertical tab
            .replace("\\\\", "\\")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .trim { it <= ' ' }
        val chars = input.toCharArray()
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
        var line = line
        val intLine = intLine()
        line = line.trim { it <= ' ' }
        var i: Int = 0
        while (i < line.length) {
            if (line.startsWith("=", i)) {
                break
            }
            i++
        }
        if (i == line.length) throw Exception()
        intLine.name = line.substring(0, i).trim { it <= ' ' }

        var value = line.substring(i + 1)
        if (value.contains("\"")) {
            intLine.value = decode_stringed_int(value).toLong()
            return intLine
        }

        value = value.replace("<", "")
            .replace(">", "")
            .replace(";", "")

        if (value.contains("0x")) {
            value = value.replace("0x", "").trim { it <= ' ' }
            intLine.value = value.toLong(16)
        } else {
            value = value.trim { it <= ' ' }
            intLine.value = value.toLong()
        }

        return intLine
    }

    @JvmStatic
    @Throws(Exception::class)
    fun decode_hex_line(line: String): hexLine {
        var line = line
        val hexLine = hexLine()
        line = line.trim { it <= ' ' }
        var i: Int = 0
        while (i < line.length) {
            if (line.startsWith("=", i)) {
                break
            }
            i++
        }
        if (i == line.length) throw Exception()
        hexLine.name = line.substring(0, i).trim { it <= ' ' }

        var value = line.substring(i + 1)
        value = value.replace("<", "")
            .replace(">", "")
            .replace(";", "").trim { it <= ' ' }

        hexLine.value = value

        return hexLine
    }

    @JvmStatic
    fun encodeIntOrHexLine(name: String, value: String): String {
        return "$name = <$value>;"
    }
}
