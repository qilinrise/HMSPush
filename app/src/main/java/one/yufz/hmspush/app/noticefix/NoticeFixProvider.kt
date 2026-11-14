package one.yufz.hmspush.app.noticefix

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.common.noticefix.NoticeLibIcon

/**
 * Serves the ported NoticeFix data (global config, strategy order, icon
 * library, custom icons) to the SystemUI process, which queries this
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
            NoticeFixContract.COL_LIBRARY,
            NoticeFixContract.COL_CUSTOMS,
        )
        val cursor = MatrixCursor(columns)
        val context = context ?: return cursor
        cursor.addRow(
            arrayOf(
                NoticeFixStore.loadConfig(context).toJson().toString(),
                NoticeFuncStatus.toJsonArray(NoticeFixStore.loadFuncs(context)),
                NoticeLibIcon.toJsonArray(NoticeFixStore.loadLibrary(context).values),
                NoticeCustomIcon.toJsonArray(NoticeFixStore.loadCustoms(context).values),
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
