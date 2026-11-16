@file:OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package one.yufz.hmspush.app.fake

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.compose.collectAsState
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.yufz.hmspush.R
import one.yufz.hmspush.app.widget.PillSearchField
import one.yufz.hmspush.app.widget.TipsDialog
import one.yufz.hmspush.app.workaround.mavericksViewModel

@Composable
fun FakeDeviceScreen(viewModel: FakeDeviceViewModel = mavericksViewModel()) {
    val state by viewModel.collectAsState()

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onErrorShown() },
            title = { Text(text = "Error") },
            text = {
                Text(
                    text = state.error?.message ?: "Unknown error",
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onErrorShown() }) {
                    Text(text = stringResource(id = R.string.dialog_confirm))
                }
            }
        )
    }
    if (state.showZygiskTips) {
        TipsDialog(state.zygiskEnabled, onDismissRequest = { viewModel.dismissZygiskTips() })
    }

    Scaffold(
        topBar = {
            // No scroll behaviour: like the Custom page, the top bar
            // keeps its colour while the list scrolls under it.
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = R.string.fake_device))
                        Spacer(modifier = Modifier.width(8.dp))
                        Tips(state.zygiskEnabled) {
                            viewModel.showZygiskTips()
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            // Same permanent pill search as Home / Icons / Custom:
            // always visible under the top bar, no separate search
            // mode and no search back arrow.
            PillSearchField(
                value = state.filterKeywords,
                onValueChange = { viewModel.filter(it) },
                placeholderText = stringResource(id = R.string.menu_search),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.filteredConfigList, key = { it.packageName }) { config ->
                    AppCard(config) {
                        viewModel.update(config.copy(enabled = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tips(zygiskEnabled: Boolean, requestShowTips: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (zygiskEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (zygiskEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.clickable {
            requestShowTips()
        }
    ) {
        Text(
            text = if (zygiskEnabled) stringResource(R.string.hmspush_zygisk_installed) else stringResource(R.string.hmspush_zygisk_not_install),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AppCard(config: AppConfig, onCheckedChange: (checked: Boolean) -> Unit) {
    val drawable by loadAppIcon(LocalContext.current, config.packageName)
    val drawablePainter = rememberDrawablePainter(drawable)
    // Same per-app rounded card as Home's app list.
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    painter = drawablePainter,
                    contentDescription = "icon",
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(all = 8.dp)
                )
            },
            headlineContent = {
                Text(text = config.name)
            },
            supportingContent = {
                Text(text = config.packageName)
            },
            trailingContent = {
                // Optimistic switch state: persisting a toggle goes
                // through a root shell write and a flow round trip,
                // and a fully controlled Switch visibly bounces back
                // while that is in flight. The card flips immediately
                // and the persisted value catches up; the state is
                // keyed by package so a recycled card never shows
                // another app's position.
                var checked by remember(config.packageName) {
                    mutableStateOf(config.enabled)
                }
                // The stock Switch is itself draggable: a scroll
                // gesture that starts on a switch drags its thumb
                // sideways before the list's scroll takes over, and
                // the thumb then animates back — the "jumping
                // switch" seen while scrolling. Render the Switch
                // display-only and take the tap on this wrapper
                // instead: taps toggle, drags just scroll the list.
                Box(
                    modifier = Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = {
                            checked = it
                            onCheckedChange(it)
                        },
                    ),
                ) {
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        )
    }
}

private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()

@Composable
private fun loadAppIcon(context: Context, packageName: String): MutableState<Drawable?> {
    // Keyed by package: a recycled card starts from this app's cached
    // icon (or nothing), never from the previous app's icon, which
    // used to linger until the async load finished and made icons
    // and names disagree mid-scroll. The cache keeps icons of
    // already-seen apps instant on the way back.
    val drawable = remember(packageName) { mutableStateOf(appIconCache[packageName]) }

    LaunchedEffect(packageName) {
        if (drawable.value == null) {
            launch(Dispatchers.IO) {
                val icon = runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                }.getOrNull()
                if (icon != null) {
                    appIconCache[packageName] = icon
                    drawable.value = icon
                }
            }
        }
    }

    return drawable
}