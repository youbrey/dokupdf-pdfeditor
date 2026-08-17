package com.example.core.filter

import android.graphics.*
import com.example.core.model.FilterType
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance image processing engine for document scanning filters
 */
object FilterProcessor {

    fun applyFilter(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 1.0f,
        contrast: Float = 1.0f
    ): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> {
                if (brightness == 1.0f && contrast == 1.0f) source
                else applyColorMatrix(source, brightness, contrast, 1.0f)
            }
            FilterType.NO_SHADOW -> applyNoShadow(source, brightness, contrast)
            FilterType.MAGIC_BW_HP -> applyHighContrastBW(source, brightness, contrast)
            FilterType.MAGIC_COLOR -> applyMagicColor(source, brightness, contrast)
            FilterType.GRAYSCALE -> applyGrayscale(source, brightness, contrast)
            FilterType.INVERT -> applyInvert(source)
            FilterType.SHARPEN -> applySharpen(source, brightness, contrast)
            FilterType.LIGHTEN -> applyLighten(source, brightness, contrast)
        }
    }

    /**
     * CamScanner Magic Color (Hemat): Vivid colors, white clean paper background
     */
    private fun applyMagicColor(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Color matrix with high contrast & saturation boost to clean paper grey to white
        val effectiveContrast = 1.6f * contrast
        val scale = effectiveContrast
        val translate = (-0.5f * scale + 0.5f + 0.15f * brightness) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                scale * 1.1f, 0f, 0f, 0f, translate + 15f,
                0f, scale * 1.1f, 0f, 0f, translate + 15f,
                0f, 0f, scale * 1.1f, 0f, translate + 15f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        // Boost saturation
        val sat = ColorMatrix()
        sat.setSaturation(1.35f)
        cm.postConcat(sat)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * High Contrast Black & White (H&P)
     */
    private fun applyHighContrastBW(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f) // Desaturate

        val scale = 3.5f * contrast
        val translate = (-0.5f * scale + 0.5f + 0.25f * brightness) * 255f

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Shadow Removal (Tanpa Bayangan): Evens out lighting gradient
     */
    private fun applyNoShadow(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = 1.35f * contrast
        val translate = (-0.5f * scale + 0.5f + 0.22f * brightness) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate + 25f,
                0f, scale, 0f, 0f, translate + 25f,
                0f, 0f, scale, 0f, translate + 25f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val sat = ColorMatrix()
        sat.setSaturation(1.1f)
        cm.postConcat(sat)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Standard Grayscale with gentle contrast
     */
    private fun applyGrayscale(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f)

        val scale = 1.2f * contrast
        val translate = (-0.5f * scale + 0.5f + 0.05f * brightness) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Invert / Balik (Negative)
     */
    private fun applyInvert(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    private fun applyLighten(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        return applyColorMatrix(source, brightness * 1.25f, contrast, 1.0f)
    }

    private fun applySharpen(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        return applyColorMatrix(source, brightness, contrast * 1.4f, 1.2f)
    }

    private fun applyColorMatrix(
        source: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f + (brightness - 1.0f)) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        if (saturation != 1.0f) {
            val sat = ColorMatrix()
            sat.setSaturation(saturation)
            cm.postConcat(sat)
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Perspective crop / Warp quadrilateral to rectangular bitmap
     */
    fun cropPerspective(
        source: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomRight: PointF,
        bottomLeft: PointF
    ): Bitmap {
        val targetWidth = max(
            hypot(topRight.x - topLeft.x, topRight.y - topLeft.y),
            hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y)
        ).toInt().coerceAtLeast(100)

        val targetHeight = max(
            hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y),
            hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y)
        ).toInt().coerceAtLeast(100)

        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val srcPoints = floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        return result
    }

    private fun hypot(dx: Float, dy: Float): Float {
        return Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }
}
