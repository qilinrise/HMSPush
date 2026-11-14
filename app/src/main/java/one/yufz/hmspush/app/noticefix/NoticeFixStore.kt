package one.yufz.hmspush.app.noticefix

import android.content.Context
import android.content.SharedPreferences
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixConfig
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import org.json.JSONObject

/**
 * Local storage of the ported NoticeFix feature inside the module app,
 * mirroring NoticeFix's layout of one SharedPreferences file per data set
 * holding JSON values. The SystemUI side reads the same data through
 * [NoticeFixProvider]; nothing of this touches the HMS push icon feature,
 * which keeps its own storage and service.
 */
object NoticeFixStore {

    private fun prefs(context: Context, file: String): SharedPreferences =
        context.getSharedPreferences(file, Context.MODE_PRIVATE)

    // ----- global config -------------------------------------------------

    fun loadConfig(context: Context): NoticeFixConfig {
        val json = prefs(context, NoticeFixContract.PREF_GLOBAL_CONFIG)
            .getString(NoticeFixContract.PREF_GLOBAL_CONFIG, null)
        return NoticeFixConfig.parse(json)
    }

    fun saveConfig(context: Context, config: NoticeFixConfig) {
        prefs(context, NoticeFixContract.PREF_GLOBAL_CONFIG)
            .edit()
            .putString(NoticeFixContract.PREF_GLOBAL_CONFIG, config.toJson().toString())
            .apply()
    }

    // ----- strategy order ------------------------------------------------

    fun loadFuncs(context: Context): List<NoticeFuncStatus> {
        val all = prefs(context, NoticeFixContract.PREF_FUNC_ORDER).all
        if (all.isEmpty()) return NoticeFuncStatus.defaults()
        val parsed = all.values.mapNotNull { value ->
            (value as? String)?.let { runCatching { NoticeFuncStatus.fromJson(JSONObject(it)) }.getOrNull() }
        }
        if (parsed.isEmpty()) return NoticeFuncStatus.defaults()
        val byId = parsed.associateBy { it.funcId }.toMutableMap()
        one.yufz.hmspush.common.noticefix.NoticeIconFunc.entries.forEach { func ->
            byId.putIfAbsent(func.id, NoticeFuncStatus(func.id, true, func.id))
        }
        return byId.values.sortedBy { it.order }
    }

    fun saveFuncs(context: Context, funcs: List<NoticeFuncStatus>) {
        val editor = prefs(context, NoticeFixContract.PREF_FUNC_ORDER).edit()
        editor.clear()
        funcs.forEach { editor.putString(it.funcId.toString(), it.toJson().toString()) }
        editor.apply()
    }

    // ----- custom icons ----------------------------------------------------

    fun loadCustoms(context: Context): Map<String, NoticeCustomIcon> {
        val all = prefs(context, NoticeFixContract.PREF_CUSTOM_ICONS).all
        val result = LinkedHashMap<String, NoticeCustomIcon>()
        for ((key, value) in all) {
            val json = value as? String ?: continue
            runCatching { NoticeCustomIcon.fromJson(JSONObject(json)) }
                .getOrNull()
                ?.takeIf { it.pkgName.isNotEmpty() }
                ?.let { result[key] = it }
        }
        return result
    }

    fun saveCustom(context: Context, custom: NoticeCustomIcon) {
        prefs(context, NoticeFixContract.PREF_CUSTOM_ICONS)
            .edit()
            .putString(custom.pkgName, custom.toJson().toString())
            .apply()
    }

    fun deleteCustom(context: Context, packageName: String) {
        prefs(context, NoticeFixContract.PREF_CUSTOM_ICONS)
            .edit()
            .remove(packageName)
            .apply()
    }
}
