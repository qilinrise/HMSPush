package one.yufz.hmspush.hook.systemui

import android.app.AndroidAppHelper
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.getOrNull
import one.yufz.xposed.hook

/**
 * SystemUI entry point for the notification icon fix.
 *
 * Hooks the notification row inflation, where the small icon of every
 * notification is about to be bound to its row, and lets
 * [NotificationIconFixer] convert non-grayscale icons. All lookups are
 * defensive: on ROMs where the expected classes/methods are absent (or
 * renamed), the hook is skipped with a log line instead of breaking
 * SystemUI.
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
        val rowBinder = ROW_BINDER_CLASSES.firstNotNullOfOrNull {
            XposedHelpers.findClassIfExists(it, classLoader)
        }
        if (rowBinder == null) {
            XLog.d(TAG, "NotificationRowBinderImpl not found, icon fix unavailable on this ROM")
            return
        }

        val entryClass = XposedHelpers.findClassIfExists(ENTRY_CLASS, classLoader)
        if (entryClass == null) {
            XLog.d(TAG, "NotificationEntry not found, icon fix unavailable on this ROM")
            return
        }

        val methods = rowBinder.declaredMethods.filter { method ->
            method.name == "inflateViews" && method.parameterTypes.any { it == entryClass }
        }
        if (methods.isEmpty()) {
            XLog.d(TAG, "inflateViews(NotificationEntry, ...) not found, icon fix unavailable on this ROM")
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
            val context = AndroidAppHelper.currentApplication() ?: return
            val entry = args.firstOrNull { it != null && entryClass.isInstance(it) } ?: return
            val sbn = extractSbn(entry) ?: return
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
