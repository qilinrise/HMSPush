package one.yufz.hmspush.app.noticefix

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.yufz.hmspush.R
import one.yufz.hmspush.common.noticefix.NoticeFuncStatus
import one.yufz.hmspush.common.noticefix.NoticeIconFunc

/**
 * Strategy page of the ported NoticeFix feature: the order in which the
 * custom / library / auto strategies are tried (NoticeFix exposes this as
 * a drag-to-reorder list on its home screen; here rows move with the
 * up/down buttons), the global switches, icon library management and
 * the restart-SystemUI action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeHomeScreen(viewModel: NoticeFixViewModel = rememberNoticeFixViewModel()) {
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.let { viewModel.importLibrary(it) }
            }
        }
    }

    // One-shot status messages from the view model, shown as toasts.
    val refreshedText = stringResource(id = R.string.notice_library_refreshed)
    val importedText = stringResource(id = R.string.notice_library_imported)
    val refreshFailedText = stringResource(id = R.string.notice_library_refresh_failed)
    val importInvalidText = stringResource(id = R.string.notice_library_import_invalid)
    val restartSentText = stringResource(id = R.string.notice_restart_sent)
    val restartFailedText = stringResource(id = R.string.notice_restart_failed)
    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        val text = when {
            message.startsWith("library_refreshed:") ->
                refreshedText.format(message.substringAfterLast(':'))
            message.startsWith("library_imported:") -> {
                val parts = message.split(':')
                importedText.format(parts.getOrNull(1) ?: "0", parts.getOrNull(2) ?: "0")
            }
            message == "library_refresh_failed" || message == "library_refresh_empty" ->
                refreshFailedText
            message == "library_import_invalid" -> importInvalidText
            message == "restart_systemui_sent" -> restartSentText
            message == "restart_systemui_failed" -> restartFailedText
            else -> null
        }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
        viewModel.consumeMessage()
    }

    var showRestartDialog by remember { mutableStateOf(false) }
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(text = stringResource(id = R.string.notice_restart_systemui)) },
            text = { Text(text = stringResource(id = R.string.notice_restart_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        viewModel.restartSystemUI()
                    },
                ) { Text(text = stringResource(id = R.string.notice_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(text = stringResource(id = R.string.notice_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.nav_notice)) })
        },
    ) { padding ->
        val sortedFuncs = viewModel.funcs.sortedBy { it.order }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { NoticeSectionTitle(stringResource(id = R.string.notice_strategy_title)) }
            item {
                NoticeGroupCard {
                    sortedFuncs.forEachIndexed { index, status ->
                        if (index > 0) NoticeGroupDivider()
                        FuncRow(
                            status = status,
                            canMoveUp = index > 0,
                            canMoveDown = index < sortedFuncs.lastIndex,
                            onToggle = { viewModel.setFuncActive(status.funcId, it) },
                            onMove = { viewModel.moveFunc(status.funcId, it) },
                        )
                    }
                }
            }

            item { NoticeSectionTitle(stringResource(id = R.string.notice_global_title)) }
            item {
                NoticeGroupCard {
                    NoticeSwitchRow(
                        title = stringResource(id = R.string.notice_always_handle),
                        summary = stringResource(id = R.string.notice_always_handle_summary),
                        checked = viewModel.config.alwaysHandleProxyNotice,
                        onCheckedChange = { checked ->
                            viewModel.updateConfig { it.copy(alwaysHandleProxyNotice = checked) }
                        },
                    )
                    NoticeGroupDivider()
                    NoticeSwitchRow(
                        title = stringResource(id = R.string.notice_skip_grayscale),
                        summary = stringResource(id = R.string.notice_skip_grayscale_summary),
                        checked = viewModel.config.skipGrayscale,
                        onCheckedChange = { checked ->
                            viewModel.updateConfig { it.copy(skipGrayscale = checked) }
                        },
                    )
                }
            }

            item { NoticeSectionTitle(stringResource(id = R.string.notice_library_title)) }
            item {
                NoticeGroupCard {
                    NoticeActionRow(
                        title = stringResource(id = R.string.notice_library_count),
                        icon = Icons.Outlined.PhotoLibrary,
                        value = viewModel.library.size.toString(),
                        onClick = { },
                    )
                    NoticeGroupDivider()
                    NoticeActionRow(
                        title = stringResource(id = R.string.notice_library_refresh),
                        summary = stringResource(id = R.string.notice_library_refresh_summary),
                        icon = Icons.Outlined.CloudDownload,
                        onClick = { viewModel.refreshLibraryOnline() },
                    )
                    NoticeGroupDivider()
                    NoticeActionRow(
                        title = stringResource(id = R.string.notice_library_import),
                        summary = stringResource(id = R.string.notice_library_import_summary),
                        icon = Icons.Outlined.FolderOpen,
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }

            item { NoticeSectionTitle(stringResource(id = R.string.notice_system_title)) }
            item {
                NoticeGroupCard {
                    NoticeActionRow(
                        title = stringResource(id = R.string.notice_restart_systemui),
                        summary = stringResource(id = R.string.notice_restart_systemui_summary),
                        icon = Icons.Outlined.RestartAlt,
                        onClick = { showRestartDialog = true },
                    )
                }
            }
            item {
                Text(
                    text = stringResource(id = R.string.notice_scope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FuncRow(
    status: NoticeFuncStatus,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val (titleRes, summaryRes) = when (NoticeIconFunc.fromId(status.funcId)) {
        NoticeIconFunc.CUSTOM -> R.string.notice_func_custom to R.string.notice_func_custom_summary
        NoticeIconFunc.LIB -> R.string.notice_func_lib to R.string.notice_func_lib_summary
        NoticeIconFunc.AUTO -> R.string.notice_func_auto to R.string.notice_func_auto_summary
        null -> R.string.notice_func_custom to R.string.notice_func_custom_summary
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = stringResource(id = titleRes)) },
        supportingContent = { Text(text = stringResource(id = summaryRes)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                CheckSwitch(checked = status.active, onCheckedChange = onToggle)
            }
        },
    )
}
