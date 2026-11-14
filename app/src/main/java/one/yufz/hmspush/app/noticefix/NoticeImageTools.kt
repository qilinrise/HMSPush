package one.yufz.hmspush.app.noticefix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.min

/**
 * Bitmap transforms offered by NoticeFix's custom icon editor: monochrome
 * conversion, color inversion and rounded corners, plus small helpers.
 * (Independent reimplementation of the same well-known operations.)
 */
object NoticeImageTools {

    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    fun reverseColor(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun toRoundCorner(bitmap: Bitmap, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                radius,
                radius,
                Path.Direction.CW,
            )
        }
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return output
    }

    /**
     * Same monochrome conversion the SystemUI pipeline applies: mean
     * luminance threshold, solid light/dark background pads folded away,
     * foreground kept white with its original alpha.
     */
    fun toMonochrome(bitmap: Bitmap): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumTotal = 0L
        var opaqueCount = 0
        for (pixel in pixels) {
            if (Color.alpha(pixel) > 0) {
                sumTotal += Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                opaqueCount++
            }
        }
        if (opaqueCount == 0) return null
        val threshold = sumTotal / opaqueCount

        val backgroundIsLight = detectSolidBackground(pixels, width, height, threshold)

        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = Color.alpha(pixel)
            if (alpha == 0) continue
            val channelSum = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
            val isLight = channelSum > threshold
            val foreground = when (backgroundIsLight) {
                true -> !isLight
                false -> isLight
                null -> true
            }
            if (foreground) {
                result[i] = Color.argb(alpha, 255, 255, 255)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, width, 0, 0, width, height)
        }
    }

    fun isGrayscale(bitmap: Bitmap): Boolean {
        val sample = if (bitmap.width > 64 || bitmap.height > 64) {
            Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        } else {
            bitmap
        }
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        return pixels.all { pixel ->
            if (Color.alpha(pixel) < 50) {
                true
            } else {
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                abs(r - g) < 20 && abs(r - b) < 20 && abs(g - b) < 20
            }
        }
    }

    private fun detectSolidBackground(pixels: IntArray, width: Int, height: Int, threshold: Long): Boolean? {
        if (width < 3 || height < 3) return null
        val midRow = height / 2 * width
        val edges = intArrayOf(
            pixels[(1.5 * width).toInt().coerceIn(0, pixels.size - 1)],
            pixels[(width * height - 1.5 * width).toInt().coerceIn(0, pixels.size - 1)],
            pixels[(midRow + 1).coerceIn(0, pixels.size - 1)],
            pixels[(midRow + width - 2).coerceIn(0, pixels.size - 1)],
        )
        if (edges.any { Color.alpha(it) == 0 }) return null
        val light = edges.map { Color.red(it) + Color.green(it) + Color.blue(it) > threshold }
        return when {
            light.all { it } -> true
            light.none { it } -> false
            else -> null
        }
    }

    fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
