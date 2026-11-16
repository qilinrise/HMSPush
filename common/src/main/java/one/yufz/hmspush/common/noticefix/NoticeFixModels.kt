package one.yufz.hmspush.common.noticefix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data model of the notification icon feature ported from NoticeFix
 * (https://github.com/xudazhu1/NoticeFix, AGPL-3.0; this is an independent
 * reimplementation of its data structures and behaviour, no code copied).
 *
 * The data lives in the module app (edited by its UI) and is served to the
 * SystemUI process through [NoticeFixContract]'s content provider.
 */
object NoticeFixContract {
    const val AUTHORITY = "one.yufz.hmspush.provider.noticefix"
    const val URI = "content://$AUTHORITY/data"

    const val COL_CONFIG = "config"
    const val COL_FUNCS = "funcs"
    const val COL_CUSTOMS = "customs"

    const val PREF_GLOBAL_CONFIG = "noticefix_global_config"
    const val PREF_CUSTOM_ICONS = "noticefix_custom_icons"
    const val PREF_FUNC_ORDER = "noticefix_func_order"
}

fun decodeBase64Bitmap(base64: String?): Bitmap? {
    if (base64.isNullOrEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

fun encodeBitmapToBase64(bitmap: Bitmap): String {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

/** A per-app custom icon assignment plus the do-not-handle flag. */
data class NoticeCustomIcon(
    val pkgName: String,
    val label: String = "",
    /** Base64 encoded PNG; null when only flags are set for this app. */
    val iconBase64: String? = null,
    val iconColor: String? = null,
    /** Whitelist: do not process this app's own notices (its push/proxy notices are always processed). */
    val noHandle: Boolean = false,
) {
    fun decodeBitmap(): Bitmap? = decodeBase64Bitmap(iconBase64)

    fun toJson(): JSONObject = JSONObject().apply {
        put("pkgName", pkgName)
        put("label", label)
        put("iconBase64", iconBase64 ?: "")
        put("iconColor", iconColor ?: "")
        put("noHandle", noHandle)
    }

    companion object {
        fun fromJson(obj: JSONObject): NoticeCustomIcon = NoticeCustomIcon(
            pkgName = obj.optString("pkgName"),
            label = obj.optString("label"),
            iconBase64 = obj.optString("iconBase64").ifEmpty { null },
            iconColor = obj.optString("iconColor").ifEmpty { null },
            noHandle = obj.optBoolean("noHandle", false),
        )

        fun toJsonArray(icons: Collection<NoticeCustomIcon>): String {
            val array = JSONArray()
            icons.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun parseArray(json: String): List<NoticeCustomIcon> {
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            val result = ArrayList<NoticeCustomIcon>(array.length())
            for (i in 0 until array.length()) {
                runCatching { fromJson(array.getJSONObject(i)) }
                    .getOrNull()
                    ?.takeIf { it.pkgName.isNotEmpty() }
                    ?.let(result::add)
            }
            return result
        }
    }
}

/** Global switches of the ported feature. Defaults mirror NoticeFix. */
data class NoticeFixConfig(
    /** Leave already-grayscale icons completely alone. */
    val skipGrayscale: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("skipGrayscale", skipGrayscale)
    }

    companion object {
        fun fromJson(obj: JSONObject): NoticeFixConfig = NoticeFixConfig(
            skipGrayscale = obj.optBoolean("skipGrayscale", false),
        )

        fun parse(json: String?): NoticeFixConfig {
            if (json.isNullOrBlank()) return NoticeFixConfig()
            return runCatching { fromJson(JSONObject(json)) }.getOrDefault(NoticeFixConfig())
        }
    }
}

/** The three icon replacement strategies, tried in user-defined order. */
enum class NoticeIconFunc(val id: Int) {
    CUSTOM(0),
    LIB(1),
    AUTO(2);

    companion object {
        fun fromId(id: Int): NoticeIconFunc? = entries.firstOrNull { it.id == id }
    }
}

data class NoticeFuncStatus(
    val funcId: Int,
    val active: Boolean = true,
    val order: Int = funcId,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("iconFuncId", funcId)
        put("active", active)
        put("order", order)
    }

    companion object {
        fun fromJson(obj: JSONObject): NoticeFuncStatus = NoticeFuncStatus(
            funcId = obj.optInt("iconFuncId"),
            active = obj.optBoolean("active", true),
            order = obj.optInt("order", obj.optInt("iconFuncId")),
        )

        /** NoticeFix defaults: custom, then library, then auto; all active. */
        fun defaults(): List<NoticeFuncStatus> =
            NoticeIconFunc.entries.map { NoticeFuncStatus(it.id, active = true, order = it.id) }

        fun parseArray(json: String?): List<NoticeFuncStatus> {
            if (json.isNullOrBlank()) return defaults()
            val parsed = runCatching {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull {
                    runCatching { fromJson(array.getJSONObject(it)) }.getOrNull()
                }
            }.getOrDefault(emptyList())
            if (parsed.isEmpty()) return defaults()
            // Fill in any missing strategy and normalize.
            val byId = parsed.associateBy { it.funcId }.toMutableMap()
            NoticeIconFunc.entries.forEach { func ->
                byId.putIfAbsent(func.id, NoticeFuncStatus(func.id, true, func.id))
            }
            return byId.values.sortedBy { it.order }
        }

        fun toJsonArray(statuses: Collection<NoticeFuncStatus>): String {
            val array = JSONArray()
            statuses.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
