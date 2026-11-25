package one.yufz.hmspush.app.noticefix

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus

/**
 * Serves the ported NoticeFix data (global config, strategy order,
 * custom icons) to the SystemUI process, which queries this
 * provider and caches the snapshot briefly. This is the same role
 * NoticeFix's IconDataContentProvider was written for; read-only.
 */
class NoticeFixProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = arrayOf(
            NoticeFixContract.COL_CONFIG,
            NoticeFixContract.COL_FUNCS,
            NoticeFixContract.COL_CUSTOMS,
            NoticeFixContract.COL_MASTER,
        )
        val cursor = MatrixCursor(columns)
        val context = context ?: return cursor
        // The Custom Notification Icon switch is the master switch of
        // BOTH icon pipelines: the HMS-side handler already honours it,
        // and the SystemUI pipeline reads it from this column. The
        // switch's canonical value lives in HMS Core's device-protected
        // preferences (that is where the hooked Prefs object stores
        // it), which this process cannot read as a file — the only way
        // to it is the HMS push service bridge, the same channel the
        // Settings page itself uses, so this column always matches
        // what the switch shows. When the service is unreachable the
        // column reports ON: a bridge hiccup must not disable the
        // SystemUI pipeline, and the real value applies as soon as
        // the service answers again.
        // Isolated on purpose: this one flag must never fail the whole
        // query — a throw here would starve the SystemUI pipeline of
        // ALL its data (this is exactly what killed the pipeline after
        // a reboot, when an early query hit the not-yet-initialized
        // HmsPushClient). Any failure reads as ON.
        val masterEnabled = runCatching {
            !HmsPushClient.isHmsPushServiceAlive() ||
                HmsPushClient.preference.useCustomIcon
        }.getOrDefault(true)
        cursor.addRow(
            arrayOf(
                NoticeFixStore.loadConfig(context).toJson().toString(),
                NoticeFuncStatus.toJsonArray(NoticeFixStore.loadFuncs(context)),
                NoticeCustomIcon.toJsonArray(NoticeFixStore.loadCustoms(context).values),
                if (masterEnabled) "1" else "0",
            ),
        )
        return cursor
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.item/vnd.one.yufz.hmspush.noticefix"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
