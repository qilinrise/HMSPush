package one.yufz.hmspush.app.noticefix

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.common.IconData
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixConfig
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.common.noticefix.encodeBitmapToBase64

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
)

/**
 * Shared state for the NoticeFix settings section and the custom page
 * (activity-scoped). The icon library is the HMS icon subscription: it
 * is loaded through [HmsPushClient] like the icon page does, so the
 * custom page recognizes the same library.
 */
class NoticeFixViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Application get() = getApplication()

    var config by mutableStateOf(NoticeFixStore.loadConfig(context))
        private set
    var funcs by mutableStateOf(NoticeFixStore.loadFuncs(context))
        private set
    var customs by mutableStateOf<Map<String, NoticeCustomIcon>>(emptyMap())
        private set
    /** The HMS subscription library, by package name. */
    var subscriptionIcons by mutableStateOf<Map<String, IconData>>(emptyMap())
        private set
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var appsLoaded by mutableStateOf(false)
        private set

    init {
        reloadCustoms()
        loadApps()
        loadSubscriptionIcons()
    }

    fun reloadCustoms() {
        customs = NoticeFixStore.loadCustoms(context)
    }

    private fun loadApps() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                runCatching {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .map { info ->
                            InstalledApp(
                                // Some apps ship labels with trailing
                                // whitespace/newlines; trim so the label
                                // can never reserve a phantom text line.
                                label = info.loadLabel(pm).toString().trim(),
                                packageName = info.packageName,
                                icon = info.loadIcon(pm),
                                isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            apps = loaded
            appsLoaded = true
        }
    }

    /** Reloads the subscription library; call when the custom page shows. */
    fun loadSubscriptionIcons() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    HmsPushClient.getAllIcon()
                        .mapNotNull { model -> runCatching { model.toIconData() }.getOrNull() }
                        .associateBy { it.packageName }
                }.getOrDefault(emptyMap())
            }
            if (loaded.isNotEmpty() || subscriptionIcons.isEmpty()) {
                subscriptionIcons = loaded
            }
        }
    }

    // ----- config ---------------------------------------------------------

    fun updateConfig(transform: (NoticeFixConfig) -> NoticeFixConfig) {
        val newConfig = transform(config)
        NoticeFixStore.saveConfig(context, newConfig)
        config = newConfig
    }

    // ----- strategy order ---------------------------------------------------

    fun setFuncActive(funcId: Int, active: Boolean) {
        val updated = funcs.map { if (it.funcId == funcId) it.copy(active = active) else it }
        NoticeFixStore.saveFuncs(context, updated)
        funcs = updated
    }

    fun moveFunc(funcId: Int, direction: Int) {
        val sorted = funcs.sortedBy { it.order }.toMutableList()
        val index = sorted.indexOfFirst { it.funcId == funcId }
        val target = index + direction
        if (index < 0 || target !in sorted.indices) return
        val item = sorted.removeAt(index)
        sorted.add(target, item)
        val renumbered = sorted.mapIndexed { i, status -> status.copy(order = i) }
        NoticeFixStore.saveFuncs(context, renumbered)
        funcs = renumbered
    }

    // ----- custom icons -------------------------------------------------------

    fun setCustomFromBitmap(packageName: String, label: String, bitmap: Bitmap) {
        val existing = customs[packageName]
        val bean = NoticeCustomIcon(
            pkgName = packageName,
            label = label,
            iconBase64 = encodeBitmapToBase64(bitmap),
            iconColor = existing?.iconColor,
            noHandle = existing?.noHandle ?: false,
        )
        NoticeFixStore.saveCustom(context, bean)
        reloadCustoms()
    }

    fun setCustomFromLibrary(packageName: String, label: String) {
        val libIcon = subscriptionIcons[packageName] ?: return
        setCustomFromBitmap(packageName, label, libIcon.iconBitmap)
    }

    fun deleteCustom(packageName: String) {
        NoticeFixStore.deleteCustom(context, packageName)
        reloadCustoms()
    }

    fun setNoHandle(packageName: String, label: String, noHandle: Boolean) {
        val existing = customs[packageName]
        if (existing == null && !noHandle) return
        if (existing != null && existing.iconBase64 == null && !noHandle) {
            // Nothing left worth storing once the flag is cleared.
            NoticeFixStore.deleteCustom(context, packageName)
        } else {
            NoticeFixStore.saveCustom(
                context,
                (existing ?: NoticeCustomIcon(pkgName = packageName, label = label))
                    .copy(label = label, noHandle = noHandle),
            )
        }
        reloadCustoms()
    }
}

/** The ViewModel is activity-scoped so the settings section and tabs share one state. */
@Composable
fun rememberNoticeFixViewModel(): NoticeFixViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(activity)
}
