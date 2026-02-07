package com.ireddragonicy.konabessnext.core.impl

import android.content.Context
import com.ireddragonicy.konabessnext.core.interfaces.AssetDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAssetDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AssetDataSource {
    override fun open(fileName: String): InputStream {
        return context.assets.open(fileName)
    }

    override fun list(path: String): Array<String>? {
        return context.assets.list(path)
    }
}
