package one.yufz.hmspush.app.fake

import android.content.pm.PackageManager
import android.util.Log
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import one.yufz.hmspush.app.App
import one.yufz.hmspush.app.hms.SupportHmsAppList

data class AppConfig(val name: String, val packageName: String, val enabled: Boolean)

data class FakeDeviceState(
    val configList: List<AppConfig> = emptyList(),
    val filterKeywords: String = "",
    val error: Throwable? = null,
    val zygiskEnabled: Boolean = false,
    val showZygiskTips: Boolean = false
) : MavericksState {
    val filteredConfigList: List<AppConfig>
        get() = if (filterKeywords.isEmpty()) configList else
            configList.filter { it.name.contains(filterKeywords, true) || it.packageName.contains(filterKeywords, true) }
}

class FakeDeviceViewModel(initialState: FakeDeviceState) : MavericksViewModel<FakeDeviceState>(initialState) {
    companion object {
        private const val TAG = "FakeDeviceViewModel"
    }

    private val app = App.instance

    private val supportedAppList = SupportHmsAppList(app)

    private val fakeDeviceConfig = FakeDeviceConfig

    init {
        setState {
            copy(
                zygiskEnabled = fakeDeviceConfig.zygiskEnabled,
                showZygiskTips = !fakeDeviceConfig.zygiskEnabled
            )
        }

        combine(supportedAppList.appListFlow, fakeDeviceConfig.configMapFlow, ::mergeSource)
            .flowOn(Dispatchers.IO)
            .onEach { list ->
                setState {
                    // Enabled apps first, disabled ones sink to the
                    // bottom — on every update, not just the first
                    // load, so toggling a switch moves its row into
                    // the other group right away instead of only
                    // after a restart. The sort is stable: within a
                    // group, rows keep their previous relative order.
                    copy(
                        configList = mergeConfigList(configList, list)
                            .sortedByDescending { it.enabled }
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            fakeDeviceConfig.loadConfig()
            supportedAppList.init()
        }
    }

    /**
     * Merge the current config list and the new config list with a stable order.
     */
    private fun mergeConfigList(current: List<AppConfig>, newList: List<AppConfig>): List<AppConfig> {
        val newConfigMap = newList.associateBy { it.packageName }.toMutableMap()
        val merged = current.map { old ->
            newConfigMap.remove(old.packageName) ?: old
        }
        return merged + newConfigMap.values
    }

    private fun mergeSource(supportList: List<String>, configs: ConfigMap): List<AppConfig> {
        Log.d(TAG, "mergeSource() called with: supportList = $supportList, configs = $configs")

        return supportList.map { pkg ->
            AppConfig(loadName(pkg), pkg, configs.contains(pkg))
        }
    }

    private fun loadName(packageName: String): String {
        return try {
            app.packageManager.getApplicationInfo(packageName, 0).loadLabel(app.packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun update(appConfig: AppConfig) {
        // Optimistic reorder: move the row into its new group in
        // the local state right away, for both directions. Turning
        // a row off sank it via the config-flow round trip, but a
        // row turned back on stayed in the bottom group until a
        // restart reloaded everything — the returning emission
        // does not reliably re-drive the sort. The flow merge
        // still runs afterwards and confirms the same order.
        setState {
            copy(
                configList = configList
                    .map { if (it.packageName == appConfig.packageName) appConfig else it }
                    .sortedByDescending { it.enabled }
            )
        }
        viewModelScope.launch {
            val result = if (appConfig.enabled) {
                fakeDeviceConfig.update(appConfig.packageName, emptyList())
            } else {
                fakeDeviceConfig.deleteConfig(appConfig.packageName)
            }
            result.onFailure { cause ->
                // The write did not land: put the row back into
                // its previous group instead of leaving the list
                // showing a state the config file does not have.
                setState {
                    copy(
                        configList = configList
                            .map {
                                if (it.packageName == appConfig.packageName) {
                                    it.copy(enabled = !appConfig.enabled)
                                } else {
                                    it
                                }
                            }
                            .sortedByDescending { it.enabled },
                        error = cause,
                    )
                }
            }
        }
    }

    fun filter(filter: String) {
        setState { copy(filterKeywords = filter) }
    }

    fun onErrorShown() {
        setState { copy(error = null) }
    }

    fun showZygiskTips() {
        setState { copy(showZygiskTips = true) }
    }

    fun dismissZygiskTips() {
        setState { copy(showZygiskTips = false) }
    }


}