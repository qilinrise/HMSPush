package one.yufz.hmspush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.common.IconData
import one.yufz.hmspush.common.IconData.Companion.scaleForNotification
import one.yufz.hmspush.common.model.PrefsModel
import one.yufz.hmspush.hook.XLog
import kotlin.math.abs

/**
 * SystemUI-side notification icon pipeline, applied to every notification
 * at row inflation time:
 *
 * 1. Custom notification icons, applied globally. The HMS custom icon
 *    feature (see the IconHandler in the HMS Core process) only sees
 *    notifications posted through HMS push. Here the same per-app icons
 *    from the same icon store are applied to all other notifications too,
 *    as long as the existing "custom notification icon" preference is on.
 *    A notification whose icon already is the stored icon (the normal
 *    case for HMS push notifications, which were already handled in the
 *    HMS Core process) is detected and left untouched, so no notification
 *    is ever processed twice for the same result.
 *
 * 2. Grayscale repair, for notifications without a stored icon. Small
 *    icons that do not follow the Android grayscale icon specification
 *    are rendered by SystemUI as white/grey blocks, so they are converted
 *    into compliant monochrome (white + alpha) icons. This step is a
 *    functional reimplementation of the core idea of NoticeFix
 *    (https://github.com/xudazhu1/NoticeFix, AGPL-3.0); no NoticeFix code,
 *    resources or assets are used.
 *
 * Every processed notification is marked in its extras, and both steps
 * are best-effort: any failure is logged and the notification is left
 * exactly as it was.
 */
object NotificationIconFixer {
    private const val TAG = "NotificationIconFixer"

    /** Extras marker set on notifications this pipeline already processed. */
    const val EXTRA_ICON_FIXED = "one.yufz.hmspush.extra.NOTICE_ICON_PROCESSED"

    private const val CONFIG_CACHE_MS = 60_000L
    private const val ICON_CACHE_MS = 10 * 60_000L

    @Volatile
    private var cachedPrefs: PrefsModel? = null

    @Volatile
    private var cachedPrefsAt = 0L

    @Volatile
    private var cachedIcons: Map<String, IconData>? = null

    @Volatile
    private var cachedIconsAt = 0L

    fun fix(context: Context, sbn: StatusBarNotification) {
        try {
            fixInternal(context, sbn)
        } catch (t: Throwable) {
            // Icon processing must never break SystemUI.
            XLog.e(TAG, "fix failed for ${sbn.packageName}", t)
        }
    }

    private fun fixInternal(context: Context, sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        // System-posted notifications already use compliant icons.
        if (packageName == "android") return

        val notification = sbn.notification ?: return
        if (notification.extras.getBoolean(EXTRA_ICON_FIXED, false)) return

        val prefs = loadPrefs(context) ?: return
        if (!prefs.useCustomIcon && !prefs.fixNotificationIcon) return

        val smallIcon = notification.smallIcon ?: return

        // Icon resources belong to the posting app; load them with its context.
        val iconContext = runCatching {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: context

        val currentBitmap = bitmapFromIcon(smallIcon, iconContext)

        // Step 1: apply the stored custom icon globally, like IconHandler
        // does for HMS push notifications in the HMS Core process.
        if (prefs.useCustomIcon) {
            val iconData = iconFor(context, packageName)
            if (iconData != null) {
                if (currentBitmap != null && currentBitmap.sameAs(iconData.iconBitmap)) {
                    // Already handled by the HMS-side icon feature.
                    notification.extras.putBoolean(EXTRA_ICON_FIXED, true)
                    return
                }
                setSmallIcon(notification, Icon.createWithBitmap(iconData.iconBitmap))
                if (prefs.tintIconColor) {
                    iconData.iconColor?.let { notification.color = it }
                }
                notification.extras.putBoolean(EXTRA_ICON_FIXED, true)
                XLog.d(TAG, "applied stored notification icon for $packageName")
                return
            }
        }

        // Step 2: no stored icon; repair a non-grayscale icon if enabled.
        if (!prefs.fixNotificationIcon) return
        val bitmap = currentBitmap ?: return

        if (isGrayscale(bitmap)) {
            notification.extras.putBoolean(EXTRA_ICON_FIXED, true)
            return
        }

        val fixed = toMonochrome(bitmap) ?: return
        setSmallIcon(notification, Icon.createWithBitmap(fixed))
        notification.extras.putBoolean(EXTRA_ICON_FIXED, true)
        XLog.d(TAG, "converted non-grayscale small icon for $packageName")
    }

    private fun setSmallIcon(notification: Notification, icon: Icon) {
        XposedHelpers.setObjectField(notification, "mSmallIcon", icon)
    }

    private fun loadPrefs(context: Context): PrefsModel? {
        val now = SystemClock.elapsedRealtime()
        if (now - cachedPrefsAt < CONFIG_CACHE_MS) {
            return cachedPrefs
        }
        val prefs = runCatching { HmsServiceClient.get(context)?.preference }.getOrNull()
        cachedPrefs = prefs
        cachedPrefsAt = now
        return prefs
    }

    private fun iconFor(context: Context, packageName: String): IconData? {
        val now = SystemClock.elapsedRealtime()
        if (cachedIcons == null || now - cachedIconsAt >= ICON_CACHE_MS) {
            val loaded = runCatching {
                HmsServiceClient.get(context)?.allIcon
                    ?.mapNotNull { model ->
                        runCatching {
                            model.toIconData()?.let { it.scaleForNotification(context) }
                        }.getOrNull()
                    }
                    ?.associateBy { it.packageName }
            }.getOrNull()
            if (loaded != null) {
                cachedIcons = loaded
                cachedIconsAt = now
            } else if (cachedIcons == null) {
                // HMS Core unreachable: retry on the next notification
                // instead of caching the failure.
                return null
            }
        }
        return cachedIcons?.get(packageName)
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
