package com.ireddragonicy.konabessnext.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath

/**
 * Converts Compose [ImageVector] to Android [Bitmap] for direct Canvas drawing.
 * Pre-caches the extracted Android [Path]s per unique vector name for max perf.
 */
object DtsCanvasIcons {

    private val pathCache = HashMap<String, List<Path>>()
    private val bitmapCache = HashMap<String, Bitmap>()

    /**
     * Render an [ImageVector] to an Android [Bitmap] at the given pixel size and tint color.
     * Result is cached by [ImageVector.name] + color combination.
     */
    fun getBitmap(icon: ImageVector, sizePx: Int, tintColor: Int): Bitmap {
        val cacheKey = "${icon.name}:$tintColor:$sizePx"
        bitmapCache[cacheKey]?.let { return it }

        val paths = extractPaths(icon)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tintColor
        }

        // Material icons are defined in a 24x24 viewport
        val scale = sizePx / icon.defaultWidth.value
        canvas.scale(scale, scale)

        for (path in paths) {
            canvas.drawPath(path, paint)
        }

        bitmapCache[cacheKey] = bitmap
        return bitmap
    }

    /**
     * Extract Android [Path] objects from an [ImageVector]'s root group tree.
     */
    private fun extractPaths(icon: ImageVector): List<Path> {
        val key = icon.name
        pathCache[key]?.let { return it }

        val paths = mutableListOf<Path>()
        extractFromGroup(icon.root, paths)
        pathCache[key] = paths
        return paths
    }

    private fun extractFromGroup(group: VectorGroup, out: MutableList<Path>) {
        for (node in group) {
            when (node) {
                is VectorPath -> {
                    val composePath = node.pathData.toPath()
                    out.add(composePath.asAndroidPath())
                }
                is VectorGroup -> {
                    extractFromGroup(node, out)
                }
                else -> { /* skip */ }
            }
        }
    }

    /**
     * Clear all cached bitmaps (call on theme/color change).
     */
    fun clearCache() {
        bitmapCache.clear()
    }
}
