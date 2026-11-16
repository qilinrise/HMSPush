package one.yufz.hmspush.hook.systemui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixConfig
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.hook.XLog

/** Immutable snapshot of the NoticeFix data the SystemUI pipeline needs. */
internal data class NoticeFixSnapshot(
    /** The Custom Notification Icon master switch; off stops the whole pipeline. */
    val masterEnabled: Boolean,
    val config: NoticeFixConfig,
    /** Strategies in the order they should be tried. */
    val funcs: List<NoticeFuncStatus>,
    val customs: Map<String, NoticeCustomIcon>,
)

/**
 * Loads the NoticeFix data (config, strategy order, icon library, custom
 * icons) from the module app through its content provider and caches it
 * briefly, so row inflation stays cheap. Any failure yields null and the
 * pipeline leaves notifications untouched.
 */
internal object NoticeFixData {
    private const val TAG = "NoticeFixData"
    // Short cache: a long one makes a just-changed custom icon look
    // broken, because notifications arriving inside the window are
    // processed with the snapshot taken before the change.
    private const val CACHE_MS = 10_000L

    @Volatile
    private var cached: NoticeFixSnapshot? = null

    @Volatile
    private var cachedAt = 0L

    fun load(context: Context): NoticeFixSnapshot? {
        val now = SystemClock.elapsedRealtime()
        if (now - cachedAt < CACHE_MS) {
            return cached
        }
        val snapshot = runCatching {
            context.contentResolver.query(
                Uri.parse(NoticeFixContract.URI),
                null,
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    XLog.d(TAG, "provider returned an empty cursor")
                    null
                } else {
                    val masterIndex = cursor.getColumnIndex(NoticeFixContract.COL_MASTER)
                    NoticeFixSnapshot(
                        masterEnabled = masterIndex < 0 ||
                            cursor.getString(masterIndex) == "1",
                        config = NoticeFixConfig.parse(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_CONFIG)),
                        ),
                        funcs = NoticeFuncStatus.parseArray(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_FUNCS)),
                        ),
                        customs = NoticeCustomIcon.parseArray(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_CUSTOMS)),
                        ).associateBy { it.pkgName },
                    )
                }
            }
        }.onFailure {
            XLog.d(TAG, "load NoticeFix data failed: $it")
        }.getOrNull()
        // Only cache successful loads; failures are retried on the next
        // notification so a not-yet-running module app cannot wedge the
        // pipeline for the whole cache window.
        if (snapshot != null) {
            cached = snapshot
            cachedAt = now
            XLog.d(
                TAG,
                "loaded snapshot: config=${snapshot.config} funcs=${snapshot.funcs} " +
                    "customs=${snapshot.customs.keys}",
            )
        }
        return snapshot ?: cached
    }
}
