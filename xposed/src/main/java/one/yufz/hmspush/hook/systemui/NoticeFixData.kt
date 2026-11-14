package one.yufz.hmspush.hook.systemui

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixConfig
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.common.noticefix.NoticeLibIcon
import one.yufz.hmspush.hook.XLog

/** Immutable snapshot of everything the SystemUI icon pipeline needs. */
internal data class NoticeFixSnapshot(
    val config: NoticeFixConfig,
    /** Strategies in the order they should be tried. */
    val funcs: List<NoticeFuncStatus>,
    val library: Map<String, NoticeLibIcon>,
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
    private const val CACHE_MS = 60_000L

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
                    null
                } else {
                    NoticeFixSnapshot(
                        config = NoticeFixConfig.parse(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_CONFIG)),
                        ),
                        funcs = NoticeFuncStatus.parseArray(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_FUNCS)),
                        ),
                        library = NoticeLibIcon.parseLibrary(
                            cursor.getString(cursor.getColumnIndexOrThrow(NoticeFixContract.COL_LIBRARY)),
                        ).associateBy { it.packageName },
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
        }
        return snapshot ?: cached
    }
}
