package com.ireddragonicy.konabessnext.core.impl

import com.ireddragonicy.konabessnext.core.interfaces.SystemPropertySource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSystemPropertySource @Inject constructor() : SystemPropertySource {
    override fun get(key: String, defaultValue: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as String
        } catch (e: Exception) {
            defaultValue
        }
    }
}
