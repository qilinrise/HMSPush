package one.yufz.hmspush.hook.systemui

import android.content.Context
import one.yufz.hmspush.common.BinderCursor
import one.yufz.hmspush.common.BridgeUri
import one.yufz.hmspush.common.BridgeWrap
import one.yufz.hmspush.common.HmsPushInterface
import one.yufz.hmspush.hook.XLog

/**
 * Minimal client for the HMS Core bridge service, used from the SystemUI
 * process. It resolves the service binder through the same bridge provider
 * the module app uses (see HmsPushClient), but keeps no state beyond a
 * cached binder and never throws: when HMS Core is absent or not running,
 * every lookup simply yields null and the caller keeps features off.
 */
internal object HmsServiceClient {
    private const val TAG = "HmsServiceClient"

    @Volatile
    private var service: HmsPushInterface? = null

    fun get(context: Context): HmsPushInterface? {
        service?.let {
            if (it.asBinder().isBinderAlive) {
                return it
            }
        }
        val resolved = runCatching {
            val binder = BridgeWrap.query(context, BridgeUri.HMS_PUSH_SERVICE.toUri())?.use {
                BinderCursor.getBinder(it)
            } ?: return@runCatching null
            HmsPushInterface.Stub.asInterface(binder)
        }.onFailure {
            XLog.d(TAG, "resolve HmsPush service failed: $it")
        }.getOrNull()
        service = resolved
        return resolved
    }
}
