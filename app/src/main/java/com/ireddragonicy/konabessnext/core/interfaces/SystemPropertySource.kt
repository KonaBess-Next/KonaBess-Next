package com.ireddragonicy.konabessnext.core.interfaces

interface SystemPropertySource {
    fun get(key: String, defaultValue: String = ""): String
}
