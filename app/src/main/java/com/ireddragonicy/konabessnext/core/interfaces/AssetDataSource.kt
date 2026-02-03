package com.ireddragonicy.konabessnext.core.interfaces

import java.io.InputStream

interface AssetDataSource {
    fun open(fileName: String): InputStream
    fun list(path: String): Array<String>?
}
