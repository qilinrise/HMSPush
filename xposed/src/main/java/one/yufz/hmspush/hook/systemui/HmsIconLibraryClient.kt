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
    // Short cache, and empty results are never cached: this client
    // depends on the HMS Core process being up and hooked, so an early
    // empty result (HMS not ready yet) must not wedge the library
    // strategy for minutes afterwards.
    private const val CACHE_MS = 60_000L

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
        if (!loaded.isNullOrEmpty()) {
            cachedIcons = loaded
            cachedAt = now
            XLog.d(TAG, "loaded ${loaded.size} subscription icons via HMS bridge")
            return loaded
        }
        if (loaded != null) {
            XLog.d(TAG, "HMS bridge returned 0 icons (HMS Core not ready or library empty), not caching")
        }
        return cachedIcons ?: emptyMap()
    }
}
