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
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon
import one.yufz.hmspush.common.noticefix.NoticeFixConfig
import one.yufz.hmspush.common.noticefix.NoticeFixContract
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.common.noticefix.NoticeLibIcon
import one.yufz.hmspush.common.noticefix.encodeBitmapToBase64
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
)

/** Shared state for the three NoticeFix screens (activity-scoped). */
class NoticeFixViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Application get() = getApplication()

    var config by mutableStateOf(NoticeFixStore.loadConfig(context))
        private set
    var funcs by mutableStateOf(NoticeFixStore.loadFuncs(context))
        private set
    var library by mutableStateOf<Map<String, NoticeLibIcon>>(emptyMap())
        private set
    var customs by mutableStateOf<Map<String, NoticeCustomIcon>>(emptyMap())
        private set
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var appsLoaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    /** One-shot message for the UI to toast; cleared by [consumeMessage]. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        reloadLibraryAndCustoms()
        loadApps()
    }

    fun consumeMessage() {
        message = null
    }

    fun reloadLibraryAndCustoms() {
        library = NoticeFixStore.loadLibrary(context)
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
                                label = info.loadLabel(pm).toString(),
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

    // ----- icon library -----------------------------------------------------

    fun refreshLibraryOnline() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(NoticeFixContract.DEFAULT_LIBRARY_URL).openConnection()
                        as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    NoticeLibIcon.parseLibrary(text)
                }
            }
            busy = false
            result.onSuccess { entries ->
                if (entries.isEmpty()) {
                    message = "library_refresh_empty"
                } else {
                    NoticeFixStore.importLibrary(context, entries)
                    reloadLibraryAndCustoms()
                    message = "library_refreshed:${library.size}"
                }
            }.onFailure {
                message = "library_refresh_failed"
            }
        }
    }

    fun importLibrary(input: InputStream) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = input.bufferedReader().use { it.readText() }
                    NoticeLibIcon.parseLibrary(text)
                }
            }
            busy = false
            result.onSuccess { entries ->
                if (entries.isEmpty()) {
                    message = "library_import_invalid"
                } else {
                    val added = NoticeFixStore.importLibrary(context, entries)
                    reloadLibraryAndCustoms()
                    message = "library_imported:$added:${library.size}"
                }
            }.onFailure {
                message = "library_import_invalid"
            }
        }
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
        reloadLibraryAndCustoms()
    }

    fun setCustomFromLibrary(packageName: String, label: String) {
        val libIcon = library[packageName] ?: return
        val existing = customs[packageName]
        val bean = NoticeCustomIcon(
            pkgName = packageName,
            label = label,
            iconBase64 = libIcon.iconBitmap,
            iconColor = libIcon.iconColor,
            noHandle = existing?.noHandle ?: false,
        )
        NoticeFixStore.saveCustom(context, bean)
        reloadLibraryAndCustoms()
    }

    fun deleteCustom(packageName: String) {
        NoticeFixStore.deleteCustom(context, packageName)
        reloadLibraryAndCustoms()
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
        reloadLibraryAndCustoms()
    }

    // ----- system -------------------------------------------------------------

    fun restartSystemUI() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RootActions.restartSystemUI() }
            busy = false
            message = if (ok) "restart_systemui_sent" else "restart_systemui_failed"
        }
    }
}

/** The ViewModel is activity-scoped so all bottom-bar tabs share one state. */
@Composable
fun rememberNoticeFixViewModel(): NoticeFixViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(activity)
}
