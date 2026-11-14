package one.yufz.hmspush.app.noticefix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import one.yufz.hmspush.R
import one.yufz.hmspush.common.IconData
import one.yufz.hmspush.common.noticefix.NoticeCustomIcon

private enum class CustomFilter { ALL, USER, SYSTEM, LIB, CUSTOM, NO_HANDLE }

private class EditingState(val app: InstalledApp, val source: Bitmap)

/**
 * Custom icons page (NoticeFix's AppListActivity): every installed app
 * can get a custom notification icon picked from the gallery (with the
 * grayscale / invert / round-corner processing NoticeFix offers), reuse
 * its library icon, or be flagged do-not-handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeCustomScreen(viewModel: NoticeFixViewModel = rememberNoticeFixViewModel()) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CustomFilter.ALL) }

    LaunchedEffect(Unit) { viewModel.loadSubscriptionIcons() }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    var pickingApp by remember { mutableStateOf<InstalledApp?>(null) }
    var editing by remember { mutableStateOf<EditingState?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val target = pickingApp
        pickingApp = null
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()?.let { bitmap ->
                editing = EditingState(target, bitmap)
            }
        }
    }

    val filteredApps = remember(viewModel.apps, viewModel.subscriptionIcons, viewModel.customs, query, filter) {
        viewModel.apps.filter { app ->
            val matchesFilter = when (filter) {
                CustomFilter.ALL -> true
                CustomFilter.USER -> !app.isSystem
                CustomFilter.SYSTEM -> app.isSystem
                CustomFilter.LIB -> viewModel.subscriptionIcons.containsKey(app.packageName)
                CustomFilter.CUSTOM -> viewModel.customs[app.packageName]?.iconBase64 != null
                CustomFilter.NO_HANDLE -> viewModel.customs[app.packageName]?.noHandle == true
            }
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.nav_custom)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = stringResource(id = R.string.notice_search_hint)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(percent = 50),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == CustomFilter.ALL,
                    onClick = { filter = CustomFilter.ALL },
                    label = { Text(text = stringResource(id = R.string.notice_filter_all)) },
                )
                FilterChip(
                    selected = filter == CustomFilter.USER,
                    onClick = { filter = CustomFilter.USER },
                    label = { Text(text = stringResource(id = R.string.notice_filter_user)) },
                )
                FilterChip(
                    selected = filter == CustomFilter.SYSTEM,
                    onClick = { filter = CustomFilter.SYSTEM },
                    label = { Text(text = stringResource(id = R.string.notice_filter_system)) },
                )
                FilterChip(
                    selected = filter == CustomFilter.LIB,
                    onClick = { filter = CustomFilter.LIB },
                    label = { Text(text = stringResource(id = R.string.notice_filter_lib)) },
                )
                FilterChip(
                    selected = filter == CustomFilter.CUSTOM,
                    onClick = { filter = CustomFilter.CUSTOM },
                    label = { Text(text = stringResource(id = R.string.notice_filter_custom)) },
                )
                FilterChip(
                    selected = filter == CustomFilter.NO_HANDLE,
                    onClick = { filter = CustomFilter.NO_HANDLE },
                    label = { Text(text = stringResource(id = R.string.notice_filter_no_handle)) },
                )
            }
            if (!viewModel.appsLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.notice_loading_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        NoticeGroupCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AppRow(
                            app = app,
                            libIcon = viewModel.subscriptionIcons[app.packageName],
                            custom = viewModel.customs[app.packageName],
                            expanded = expandedPkg == app.packageName,
                            onToggleExpand = {
                                expandedPkg = if (expandedPkg == app.packageName) null else app.packageName
                            },
                            onPickImage = {
                                pickingApp = app
                                pickLauncher.launch("image/*")
                            },
                            onUseLibrary = { viewModel.setCustomFromLibrary(app.packageName, app.label) },
                            onDeleteCustom = { viewModel.deleteCustom(app.packageName) },
                            onNoHandleChange = { viewModel.setNoHandle(app.packageName, app.label, it) },
                        )
                        }
                    }
                }
            }
        }
    }

    editing?.let { state ->
        CustomIconEditDialog(
            state = state,
            onDismiss = { editing = null },
            onConfirm = { bitmap ->
                viewModel.setCustomFromBitmap(state.app.packageName, state.app.label, bitmap)
                editing = null
            },
        )
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    libIcon: IconData?,
    custom: NoticeCustomIcon?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPickImage: () -> Unit,
    onUseLibrary: () -> Unit,
    onDeleteCustom: () -> Unit,
    onNoHandleChange: (Boolean) -> Unit,
) {
    val statusParts = buildList {
        if (custom?.iconBase64 != null) add(stringResource(id = R.string.notice_status_custom))
        if (libIcon != null) add(stringResource(id = R.string.notice_status_lib))
        if (custom?.noHandle == true) add(stringResource(id = R.string.notice_status_no_handle))
    }
    Column {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(text = app.label) },
            supportingContent = {
                Column {
                    Text(text = app.packageName)
                    if (statusParts.isNotEmpty()) {
                        Text(
                            text = statusParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            leadingContent = {
                Image(
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            },
            trailingContent = {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            },
        )
        if (expanded) {
            val libBitmap = libIcon?.iconBitmap
            val customBitmap = remember(custom?.iconBase64) { custom?.decodeBitmap() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PreviewItem(label = stringResource(id = R.string.notice_preview_original)) {
                        Image(
                            painter = rememberDrawablePainter(drawable = app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    PreviewItem(label = stringResource(id = R.string.notice_preview_lib)) {
                        if (libBitmap != null) {
                            Image(
                                bitmap = libBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    PreviewItem(label = stringResource(id = R.string.notice_preview_custom)) {
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = onPickImage) {
                        Text(text = stringResource(id = R.string.notice_pick_image))
                    }
                    TextButton(onClick = onUseLibrary, enabled = libIcon != null) {
                        Text(text = stringResource(id = R.string.notice_use_lib))
                    }
                    TextButton(onClick = onDeleteCustom, enabled = custom?.iconBase64 != null) {
                        Text(text = stringResource(id = R.string.notice_delete_custom))
                    }
                }
                NoticeSwitchRow(
                    title = stringResource(id = R.string.notice_no_handle),
                    summary = null,
                    checked = custom?.noHandle == true,
                    onCheckedChange = onNoHandleChange,
                )
            }
        }
    }
}

@Composable
private fun PreviewItem(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CustomIconEditDialog(
    state: EditingState,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    var monochrome by remember { mutableStateOf(false) }
    var invert by remember { mutableStateOf(false) }
    var rounded by remember { mutableStateOf(false) }
    val processed = remember(state.source, monochrome, invert, rounded) {
        var bitmap = NoticeImageTools.cropToSquare(state.source)
        if (monochrome) bitmap = NoticeImageTools.toMonochrome(bitmap) ?: bitmap
        if (invert) bitmap = NoticeImageTools.reverseColor(bitmap)
        if (rounded) bitmap = NoticeImageTools.toRoundCorner(bitmap, bitmap.width / 2f)
        bitmap
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.notice_custom_icon)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = processed.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                EditSwitchRow(
                    label = stringResource(id = R.string.notice_edit_monochrome),
                    checked = monochrome,
                    onCheckedChange = { monochrome = it },
                )
                EditSwitchRow(
                    label = stringResource(id = R.string.notice_edit_invert),
                    checked = invert,
                    onCheckedChange = { invert = it },
                )
                EditSwitchRow(
                    label = stringResource(id = R.string.notice_edit_rounded),
                    checked = rounded,
                    onCheckedChange = { rounded = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(processed) }) {
                Text(text = stringResource(id = R.string.notice_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.notice_cancel))
            }
        },
    )
}

@Composable
private fun EditSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        CheckSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
