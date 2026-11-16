@file:OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package one.yufz.hmspush.app.fake

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.compose.collectAsState
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import one.yufz.hmspush.R
import one.yufz.hmspush.app.widget.PillSearchField
import one.yufz.hmspush.app.widget.TipsDialog
import one.yufz.hmspush.app.workaround.mavericksViewModel

@Composable
fun FakeDeviceScreen(viewModel: FakeDeviceViewModel = mavericksViewModel()) {
    val state by viewModel.collectAsState()

    val listState = rememberLazyListState()
    val toggleScope = rememberCoroutineScope()

    // Toggling the first visible row used to drag the whole
    // viewport to the row's new group: LazyColumn anchors the
    // scroll position on the first visible item's key and
    // follows it when it moves. Re-anchor on the next row at the
    // exact same visual position before applying the toggle, so
    // the anchor itself never moves and the view stays put.
    fun onToggle(config: AppConfig, enabled: Boolean) {
        toggleScope.launch {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()
            if (first != null && first.key == config.packageName) {
                val next = visible.getOrNull(1)
                if (next != null) {
                    listState.scrollToItem(
                        next.index,
                        listState.firstVisibleItemScrollOffset - first.size,
                    )
                }
            }
            viewModel.update(config.copy(enabled = enabled))
        }
    }

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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(
                    state.filteredConfigList,
                    key = { it.packageName },
                    contentType = { "appCard" },
                ) { config ->
                    AppCard(config) {
                        onToggle(config, it)
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
                SpoofSwitch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        onCheckedChange(it)
                    },
                )
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
/**
 * A switch drawn from scratch for this list. Material3's Switch
 * keeps its thumb offset in an Animatable owned by a layout node
 * that is only re-measured when `checked` flips; when LazyColumn
 * recycles a composition for another app, that node (and its stale
 * offset) is reused, so the thumb renders displaced — frozen at
 * the previous app's position with this app's colours — and no
 * later state change repairs it. This switch derives everything
 * from the current [checked] value instead: colours come from
 * [SwitchDefaults] so it matches the stock switches used on the
 * Settings page exactly, and all drawing happens in the draw
 * phase, so toggling never recomposes or re-measures the row and
 * scrolling rows stay cheap.
 */
@Composable
private fun SpoofSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "spoofSwitch",
    )
    val switchColors = SwitchDefaults.colors()
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .drawBehind {
                val f = fraction
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(
                    color = lerp(
                        switchColors.uncheckedTrackColor,
                        switchColors.checkedTrackColor,
                        f,
                    ),
                    cornerRadius = corner,
                )
                val borderColor = lerp(
                    switchColors.uncheckedBorderColor,
                    switchColors.checkedBorderColor,
                    f,
                )
                if (borderColor.alpha > 0f) {
                    val inset = 1.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(corner.x - inset),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                // The thumb is full-size in both states and carries
                // an icon, exactly like the CheckSwitch used on the
                // Settings page: a check when on, a cross when off,
                // cross-faded by the same fraction.
                val thumbCenter = Offset((16f + 20f * f).dp.toPx(), size.height / 2f)
                drawCircle(
                    color = lerp(
                        switchColors.uncheckedThumbColor,
                        switchColors.checkedThumbColor,
                        f,
                    ),
                    radius = 12.dp.toPx(),
                    center = thumbCenter,
                )
                if (f < 1f) {
                    val crossColor = switchColors.uncheckedIconColor.copy(
                        alpha = switchColors.uncheckedIconColor.alpha * (1f - f),
                    )
                    val arm = 3.7.dp.toPx()
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        color = crossColor,
                        start = Offset(thumbCenter.x - arm, thumbCenter.y - arm),
                        end = Offset(thumbCenter.x + arm, thumbCenter.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = crossColor,
                        start = Offset(thumbCenter.x + arm, thumbCenter.y - arm),
                        end = Offset(thumbCenter.x - arm, thumbCenter.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                if (f > 0f) {
                    val checkColor = switchColors.checkedIconColor.copy(
                        alpha = switchColors.checkedIconColor.alpha * f,
                    )
                    val s = 5.dp.toPx()
                    val checkPath = Path().apply {
                        moveTo(thumbCenter.x - 0.85f * s, thumbCenter.y + 0.05f * s)
                        lineTo(thumbCenter.x - 0.3f * s, thumbCenter.y + 0.6f * s)
                        lineTo(thumbCenter.x + 0.9f * s, thumbCenter.y - 0.55f * s)
                    }
                    drawPath(
                        path = checkPath,
                        color = checkColor,
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            },
    )
}
