package one.yufz.hmspush.hook.systemui

import android.app.AndroidAppHelper
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.getOrNull
import one.yufz.xposed.hook
import one.yufz.xposed.hookAllMethods
import java.lang.reflect.Method

/**
 * SystemUI entry point for the notification icon fix.
 *
 * Two entry points, both letting [NotificationIconFixer] rewrite the
 * small icon on the shared [StatusBarNotification] instance:
 *
 * - `NotificationListener.onNotificationPosted`: the earliest point,
 *   before the notification pipeline creates any icon from the SBN.
 * - `NotificationRowBinderImpl.inflateViews`: the point the original
 *   NoticeFix used; row and status-bar icons are (re)created inside
 *   this call, from the same SBN instance.
 *
 * Running the fix twice for one notification is harmless: custom and
 * library application are idempotent, and the automatic conversion
 * leaves an already-grayscale (e.g. just replaced) icon alone.
 *
 * Replacing the small icon is only half of the job: Android renders
 * small icons as tinted silhouettes (an SRC_ATOP filter in the shade
 * header, a colour matrix in the status bar), which reduces a
 * coloured or opaque-background replacement bitmap to a solid blob.
 * For packages the fixer flags as coloured (see
 * [NotificationIconFixer.isColoredDisplay]) two display hooks skip
 * that tinting, mirroring NoticeFix's coloured-icon display:
 *
 * - `Notification.Builder.processSmallIconColor`: marks the header
 *   icon as "no original colour" (COLOR_INVALID, so CachingIconView
 *   applies no filter) and skips the framework's own tinting for the
 *   package; afterwards it also strips the template's circle
 *   background and the padding around the icon (and the `left_icon`
 *   in expanded templates), which otherwise shrink a full-bleed
 *   replacement bitmap to the circle's inner area.
 * - `IconManager.setIcon`: the icon views it creates (status bar,
 *   shelf and AOD share this funnel) are tagged `icon_is_pre_L` for
 *   the package, which is SystemUI's own switch for "coloured app
 *   icon, do not tint" — the mechanism NoticeFix uses. The status-bar
 *   binder and the shelf then leave multicolour icons untinted, but
 *   still tint a grayscale icon of the same package with the colour
 *   contrasted against the background behind it, so replaced icons
 *   keep adapting to light and dark backgrounds like stock ones.
 *   (An earlier version skipped `StatusBarIconView.updateIconColor`
 *   outright for these packages; that also blocked the background
 *   adaptation for their grayscale icons, so the tag replaces it.)
 *
 * All lookups are defensive: on ROMs where the expected classes or
 * methods are absent (or renamed), that hook is skipped with a log
 * line instead of breaking SystemUI.
 */
class HookSystemUI {
    companion object {
        private const val TAG = "HookSystemUI"

        // NotificationRowBinderImpl moved packages in Android 11.
        private val ROW_BINDER_CLASSES = arrayOf(
            "com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl",
            "com.android.systemui.statusbar.notification.collection.NotificationRowBinderImpl",
        )

        private const val ENTRY_CLASS =
            "com.android.systemui.statusbar.notification.collection.NotificationEntry"
    }

    fun hook(classLoader: ClassLoader) {
        hookNotificationListener(classLoader)
        hookRowBinder(classLoader)
        hookColoredDisplay(classLoader)
    }

    private fun hookColoredDisplay(classLoader: ClassLoader) {
        hookSmallIconColor()
        hookIconManagerTag(classLoader)
    }

    private fun hookSmallIconColor() {
        try {
            Notification.Builder::class.java.hookAllMethods("processSmallIconColor") {
                doBefore {
                    try {
                        val contentView = args.getOrNull(1) as? RemoteViews
                        if (contentView != null) {
                            val packageName = realPackageName(thisObject, contentView)
                            if (NotificationIconFixer.isColoredDisplay(packageName)) {
                                // COLOR_INVALID tells CachingIconView to
                                // leave the icon's colours alone (and drop
                                // its tinted background); skipping the
                                // original method stops the framework from
                                // overwriting that with the contrast colour.
                                contentView.setInt(
                                    android.R.id.icon,
                                    "setOriginalIconColor",
                                    // Notification.COLOR_INVALID
                                    0,
                                )
                                result = if ((method as? Method)?.returnType ==
                                    java.lang.Boolean.TYPE
                                ) {
                                    true
                                } else {
                                    null
                                }
                                XLog.d(TAG, "colored display: skipped small icon tinting for $packageName")
                            }
                        }
                    } catch (t: Throwable) {
                        XLog.e(TAG, "processSmallIconColor handling failed", t)
                    }
                }
                doAfter {
                    try {
                        val contentView = args.getOrNull(1) as? RemoteViews
                        if (contentView != null) {
                            val packageName = realPackageName(thisObject, contentView)
                            if (NotificationIconFixer.isColoredDisplay(packageName)) {
                                // The standard template wraps the small
                                // icon in a tinted circle background with
                                // padding, so a full-bleed replacement
                                // bitmap only occupies the inner area and
                                // renders visibly smaller than the same
                                // icon on the push path. NoticeFix's
                                // coloured display removes both (its own
                                // comment: drop the padding to cancel the
                                // surrounding circle), letting the bitmap
                                // fill the whole icon area.
                                contentView.setInt(
                                    android.R.id.icon,
                                    "setBackgroundResource",
                                    // android.R.color.transparent
                                    0,
                                )
                                contentView.setViewPadding(android.R.id.icon, 0, 0, 0, 0)
                                val leftIconId = leftIconId()
                                if (leftIconId != 0) {
                                    contentView.setViewPadding(leftIconId, 0, 0, 0, 0)
                                }
                                XLog.d(TAG, "colored display: removed icon background and padding for $packageName")
                            }
                        }
                    } catch (t: Throwable) {
                        XLog.e(TAG, "processSmallIconColor after handling failed", t)
                    }
                }
            }
            XLog.d(TAG, "hooked Notification.Builder.processSmallIconColor")
        } catch (t: Throwable) {
            XLog.e(TAG, "hooking processSmallIconColor failed", t)
        }
    }

    /**
     * NoticeFix's status-bar coloured display: after IconManager has
     * created an icon view (status bar, shelf and AOD icons all pass
     * through this one method), overwrite the `icon_is_pre_L` tag the
     * framework just set from the app's targetSdk. SystemUI's colour
     * binding reads the tag on every bind: a pre-L icon whose drawable
     * is not grayscale gets NO_COLOR (original colours kept), while a
     * grayscale drawable is still tinted with the background-contrasted
     * colour — the black/white adaptation stock icons have.
     */
    private fun hookIconManagerTag(classLoader: ClassLoader) {
        try {
            val iconManager = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.notification.icon.IconManager",
                classLoader,
            )
            val entryClass = XposedHelpers.findClassIfExists(ENTRY_CLASS, classLoader)
            val statusBarIcon = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.StatusBarIcon",
                classLoader,
            )
            val iconViewClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.StatusBarIconView",
                classLoader,
            )
            if (iconManager == null || entryClass == null ||
                statusBarIcon == null || iconViewClass == null
            ) {
                XLog.d(TAG, "IconManager classes not found, status bar coloured display unavailable on this ROM")
                return
            }
            val methods = XposedHelpers.findMethodsByExactParameters(
                iconManager, Void.TYPE, entryClass, statusBarIcon, iconViewClass,
            ).filter { it.name == "setIcon" }
            if (methods.isEmpty()) {
                XLog.d(TAG, "IconManager.setIcon not found, status bar coloured display unavailable on this ROM")
                return
            }
            methods.forEach { method ->
                method.hook {
                    doAfter {
                        try {
                            val entry = args.getOrNull(0) ?: return@doAfter
                            val iconView = args.getOrNull(2) as? android.view.View
                                ?: return@doAfter
                            val sbn = runCatching {
                                XposedHelpers.getObjectField(entry, "sbn")
                            }.getOrNull() as? StatusBarNotification
                            if (NotificationIconFixer.isColoredDisplay(sbn?.packageName)) {
                                val tagId = preLTagId(iconView)
                                if (tagId != 0) {
                                    iconView.setTag(tagId, true)
                                    XLog.d(TAG, "colored display: tagged icon_is_pre_L for ${sbn?.packageName}")
                                }
                            }
                        } catch (t: Throwable) {
                            XLog.e(TAG, "IconManager.setIcon handling failed", t)
                        }
                    }
                }
            }
            XLog.d(TAG, "hooked IconManager.setIcon")
        } catch (t: Throwable) {
            XLog.e(TAG, "hooking IconManager.setIcon failed", t)
        }
    }

    @Volatile
    private var cachedPreLTagId: Int? = null

    /** SystemUI's `icon_is_pre_L` view-tag id, resolved once. */
    private fun preLTagId(view: android.view.View): Int {
        cachedPreLTagId?.let { return it }
        val id = runCatching {
            view.resources.getIdentifier("icon_is_pre_L", "id", "com.android.systemui")
        }.getOrDefault(0)
        cachedPreLTagId = id
        return id
    }

    @Volatile
    private var cachedLeftIconId: Int? = null

    /** The framework's `left_icon` view id in SystemUI templates, resolved once. */
    private fun leftIconId(): Int {
        cachedLeftIconId?.let { return it }
        val id = runCatching {
            AndroidAppHelper.currentApplication()?.resources
                ?.getIdentifier("left_icon", "id", "com.android.systemui") ?: 0
        }.getOrDefault(0)
        cachedLeftIconId = id
        return id
    }

    /**
     * The package a [Notification.Builder] is building for. The builder
     * does not carry it directly, so read it from the notification's
     * app-info extra (written by the posting side), falling back to
     * the substitution name and then to the RemoteViews' own package.
     */
    private fun realPackageName(builder: Any?, contentView: RemoteViews?): String? {
        if (builder != null) {
            val notification = runCatching {
                XposedHelpers.getObjectField(builder, "mN")
            }.getOrNull() ?: runCatching {
                XposedHelpers.getObjectField(builder, "mNotification")
            }.getOrNull()
            if (notification is Notification) {
                val extras = notification.extras
                if (extras != null) {
                    @Suppress("DEPRECATION")
                    val appInfo = runCatching {
                        extras.getParcelable("android.appInfo") as? ApplicationInfo
                    }.getOrNull()
                    if (!appInfo?.packageName.isNullOrEmpty()) return appInfo?.packageName
                    val substName = runCatching { extras.getString("android.substName") }.getOrNull()
                    if (!substName.isNullOrEmpty()) return substName
                }
            }
        }
        if (contentView != null) {
            val rvPackage = runCatching {
                XposedHelpers.callMethod(contentView, "getPackage") as? String
            }.getOrNull()
            if (!rvPackage.isNullOrEmpty() && rvPackage != "com.android.systemui") {
                return rvPackage
            }
        }
        return null
    }

    private fun hookNotificationListener(classLoader: ClassLoader) {
        try {
            val listener = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.NotificationListener",
                classLoader,
            )
            if (listener == null) {
                XLog.d(TAG, "NotificationListener not found, posted-hook unavailable on this ROM")
                return
            }
            listener.hookAllMethods("onNotificationPosted") {
                doBefore {
                    try {
                        val context = AndroidAppHelper.currentApplication()
                        val sbn = args.firstOrNull { it is StatusBarNotification }
                            as? StatusBarNotification
                        if (context != null && sbn != null) {
                            NotificationIconFixer.fix(context, sbn)
                        }
                    } catch (t: Throwable) {
                        XLog.e(TAG, "onNotificationPosted handling failed", t)
                    }
                }
            }
            XLog.d(TAG, "hooked NotificationListener.onNotificationPosted")
        } catch (t: Throwable) {
            XLog.e(TAG, "hooking NotificationListener failed", t)
        }
    }

    private fun hookRowBinder(classLoader: ClassLoader) {
        val rowBinder = ROW_BINDER_CLASSES.firstNotNullOfOrNull {
            XposedHelpers.findClassIfExists(it, classLoader)
        }
        if (rowBinder == null) {
            XLog.d(TAG, "NotificationRowBinderImpl not found, inflateViews hook unavailable on this ROM")
            return
        }

        val entryClass = XposedHelpers.findClassIfExists(ENTRY_CLASS, classLoader)
        if (entryClass == null) {
            XLog.d(TAG, "NotificationEntry not found, inflateViews hook unavailable on this ROM")
            return
        }

        val methods = rowBinder.declaredMethods.filter { method ->
            method.name == "inflateViews" && method.parameterTypes.any { it == entryClass }
        }
        if (methods.isEmpty()) {
            XLog.d(TAG, "inflateViews(NotificationEntry, ...) not found, inflateViews hook unavailable on this ROM")
            return
        }

        methods.forEach { method ->
            method.isAccessible = true
            method.hook {
                doBefore {
                    handleInflateViews(args, entryClass)
                }
            }
        }
        XLog.d(TAG, "hooked ${methods.size} inflateViews method(s)")
    }

    private fun handleInflateViews(args: Array<Any?>, entryClass: Class<*>) {
        try {
            val context = AndroidAppHelper.currentApplication()
            if (context == null) {
                XLog.d(TAG, "inflateViews: no application context yet, skipping")
                return
            }
            val entry = args.firstOrNull { it != null && entryClass.isInstance(it) }
            if (entry == null) {
                XLog.d(TAG, "inflateViews: no NotificationEntry argument, skipping")
                return
            }
            val sbn = extractSbn(entry)
            if (sbn == null) {
                XLog.d(TAG, "inflateViews: could not extract Sbn from entry, skipping")
                return
            }
            NotificationIconFixer.fix(context, sbn)
        } catch (t: Throwable) {
            XLog.e(TAG, "inflateViews handling failed", t)
        }
    }

    private fun extractSbn(entry: Any): StatusBarNotification? {
        // Newer SystemUI exposes getSbn(); older versions keep the field.
        runCatching {
            val sbn = XposedHelpers.callMethod(entry, "getSbn")
            if (sbn is StatusBarNotification) return sbn
        }
        return runCatching { entry.getOrNull<StatusBarNotification>("mSbn") }.getOrNull()
    }
}
