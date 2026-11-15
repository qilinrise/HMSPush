package one.yufz.hmspush.hook.systemui

import android.app.AndroidAppHelper
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.getOrNull
import one.yufz.xposed.hook
import one.yufz.xposed.hookAllMethods

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
