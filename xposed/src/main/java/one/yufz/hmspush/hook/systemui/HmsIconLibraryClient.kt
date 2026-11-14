package one.yufz.hmspush.hook.systemui

import android.content.Context
import android.os.SystemClock
import one.yufz.hmspush.common.BinderCursor
import one.yufz.hmspush.common.BridgeUri
import one.yufz.hmspush.common.BridgeWrap
import one.yufz.hmspush.common.HmsPushInterface
import one.yufz.hmspush.common.IconData
import one.yufz.hmspush.hook.XLog

/**
 * Reads the HMS icon subscription library (the one managed on the icon
 * page and used for HMS push notifications) from the bridge service
 * hosted in the HMS Core process, so the SystemUI pipeline's library
 * strategy uses the same single library. Results are cached for a few
 * minutes; any failure leaves the strategy without icons instead of
 * breaking SystemUI.
 */
internal object HmsIconLibraryClient {
    private const val TAG = "HmsIconLibraryClient"
    private const val CACHE_MS = 10 * 60_000L

    @Volatile
    private var cachedIcons: Map<String, IconData>? = null

    @Volatile
    private var cachedAt = 0L

    fun loadIcons(context: Context): Map<String, IconData> {
        val now = SystemClock.elapsedRealtime()
        val hit = cachedIcons
        if (hit != null && now - cachedAt < CACHE_MS) {
            return hit
        }
        val loaded = runCatching {
            val service = BridgeWrap.query(context, BridgeUri.HMS_PUSH_SERVICE.toUri())?.use { cursor ->
                HmsPushInterface.Stub.asInterface(BinderCursor.getBinder(cursor))
            }
            service?.getAllIcon()
                ?.mapNotNull { model -> runCatching { model.toIconData() }.getOrNull() }
                ?.associateBy { it.packageName }
        }.onFailure {
            XLog.d(TAG, "load subscription icons failed: $it")
        }.getOrNull()
        if (loaded != null) {
            cachedIcons = loaded
            cachedAt = now
            return loaded
        }
        return cachedIcons ?: emptyMap()
    }
}
