package one.yufz.hmspush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.common.noticefix.NoticeIconFunc
import one.yufz.hmspush.hook.XLog
import kotlin.math.abs

/**
 * SystemUI-side notification icon pipeline, applied to every notification
 * at row inflation time. It is a functional reimplementation of NoticeFix
 * (https://github.com/xudazhu1/NoticeFix, AGPL-3.0; reimplemented from
 * scratch, no code or assets copied):
 *
 * - Apps flagged "do not handle" are skipped, except for proxy (push)
 *   notifications when the always-handle-proxy preference is on: those
 *   are posted by a push framework (opPkg differs from the target
 *   package), which includes notifications delivered through HMS push.
 * - When "skip grayscale" is on, notifications whose icon already
 *   follows the grayscale specification are left completely alone.
 * - Otherwise the enabled strategies are tried in the user's order:
 *   the per-app custom icon, the icon library entry for the package,
 *   and the automatic conversion of a non-grayscale icon into a
 *   monochrome silhouette.
 *
 * Data comes from the module app through its content provider (see
 * [NoticeFixData]); when it is unavailable the notification is left
 * exactly as it was. Everything is best-effort: any failure is logged
 * and must never break SystemUI.
 */
object NotificationIconFixer {
    private const val TAG = "NotificationIconFixer"

    fun fix(context: Context, sbn: StatusBarNotification) {
        try {
            fixInternal(context, sbn)
        } catch (t: Throwable) {
            XLog.e(TAG, "fix failed for ${sbn.packageName}", t)
        }
    }

    private fun fixInternal(context: Context, sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        val snapshot = NoticeFixData.load(context) ?: return
        val config = snapshot.config
        val custom = snapshot.customs[packageName]

        // Do-not-handle apps are skipped, with the proxy exception that
        // defines the always-handle-proxy preference.
        if (custom?.noHandle == true) {
            val isProxy = sbn.opPkg != null && sbn.opPkg != packageName
            if (!isProxy) return
            if (!config.alwaysHandleProxyNotice) return
        }

        val smallIcon = notification.smallIcon ?: return

        // Icon resources belong to the posting app; load them with its context.
        val iconContext = runCatching {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: context

        var decoded = false
        var currentBitmap: Bitmap? = null
        fun current(): Bitmap? {
            if (!decoded) {
                currentBitmap = bitmapFromIcon(smallIcon, iconContext)
                decoded = true
            }
            return currentBitmap
        }

        if (config.skipGrayscale) {
            val bitmap = current() ?: return
            if (isGrayscale(bitmap)) return
        }

        for (status in snapshot.funcs) {
            if (!status.active) continue
            when (NoticeIconFunc.fromId(status.funcId)) {
                NoticeIconFunc.CUSTOM -> {
                    val bitmap = custom?.decodeBitmap()
                    if (bitmap != null) {
                        setSmallIcon(notification, Icon.createWithBitmap(bitmap))
                        XLog.d(TAG, "applied custom icon for $packageName")
                        return
                    }
                }

                NoticeIconFunc.LIB -> {
                    val libIcon = snapshot.library[packageName]
                    val bitmap = libIcon?.decodeBitmap()
                    if (libIcon != null && bitmap != null) {
                        setSmallIcon(notification, Icon.createWithBitmap(bitmap))
                        libIcon.iconColor?.let { hex ->
                            runCatching { notification.color = Color.parseColor(hex) }
                        }
                        XLog.d(TAG, "applied library icon for $packageName")
                        return
                    }
                }

                NoticeIconFunc.AUTO -> {
                    val bitmap = current()
                    if (bitmap != null && !isGrayscale(bitmap)) {
                        val fixed = toMonochrome(bitmap)
                        if (fixed != null) {
                            setSmallIcon(notification, Icon.createWithBitmap(fixed))
                            XLog.d(TAG, "converted non-grayscale small icon for $packageName")
                            return
                        }
                    }
                }

                null -> Unit
            }
        }
    }

    private fun setSmallIcon(notification: Notification, icon: Icon) {
        XposedHelpers.setObjectField(notification, "mSmallIcon", icon)
    }

    private fun bitmapFromIcon(icon: Icon, context: Context): Bitmap? {
        return when (icon.type) {
            Icon.TYPE_BITMAP, Icon.TYPE_ADAPTIVE_BITMAP ->
                XposedHelpers.getObjectField(icon, "mObj1") as? Bitmap

            Icon.TYPE_DATA -> {
                val data = XposedHelpers.getObjectField(icon, "mObj1") as? ByteArray ?: return null
                val length = XposedHelpers.getIntField(icon, "mInt1")
                val offset = XposedHelpers.getIntField(icon, "mInt2")
                BitmapFactory.decodeByteArray(data, offset, length)
            }

            else -> {
                // Resource and Uri icons: render through the framework loader.
                val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
                drawableToBitmap(drawable)
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * A grayscale icon only contains pixels whose RGB channels are (nearly)
     * equal; fully transparent pixels count as grayscale. Large icons are
     * sampled down to 64x64 first.
     */
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

    /**
     * Converts a colored icon into a monochrome silhouette: foreground
     * pixels become white (keeping their alpha) and the background becomes
     * transparent. The split between foreground and background uses the
     * mean luminance as threshold; if the icon has a solid background pad
     * (all four edge midpoints opaque and on the same side of the
     * threshold), that side is treated as the background.
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
        // Mean channel sum; equivalent to 3x the mean luminance.
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
                // Solid light pad: the darker glyph is the foreground.
                true -> !isLight
                // Solid dark pad: the lighter glyph is the foreground.
                false -> isLight
                // No solid pad: every opaque pixel belongs to the glyph.
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

    /**
     * Returns true if the icon sits on a solid light background pad, false
     * for a solid dark pad, and null when the edges are transparent or
     * mixed (no pad).
     */
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
}
