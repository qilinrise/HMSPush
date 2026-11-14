package one.yufz.hmspush.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.compose.collectAsState
import one.yufz.hmspush.R
import one.yufz.hmspush.app.noticefix.CheckSwitch
import one.yufz.hmspush.app.noticefix.NoticeFuncRow
import one.yufz.hmspush.app.noticefix.NoticeGroupCard
import one.yufz.hmspush.app.noticefix.NoticeGroupDivider
import one.yufz.hmspush.app.noticefix.NoticeSectionTitle
import one.yufz.hmspush.app.noticefix.NoticeSwitchRow
import one.yufz.hmspush.app.noticefix.rememberNoticeFixViewModel
import one.yufz.hmspush.app.workaround.mavericksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = mavericksViewModel()) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.menu_settings))
                }
            )
        }) { paddingValues ->

        val state by viewModel.collectAsState()
        val preferences = state.preferences
        var showHideAppIconDialog by remember { mutableStateOf(false) }

        if (showHideAppIconDialog) {
            HideAppIconConfirmDialog(
                onConfirm = {
                    viewModel.toggleAppIcon(true)
                    showHideAppIconDialog = false
                },
                onDismiss = {
                    showHideAppIconDialog = false
                }
            )
        }

        Surface(modifier = Modifier.padding(paddingValues)) {
            // Grouped rounded cards with M3-style dividers, following the
            // layout style Sun picked (see the NoticeFix screens).
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoticeGroupCard {
                    SwitchPreference(
                        title = stringResource(id = R.string.disable_signature),
                        summary = stringResource(id = R.string.disable_signature_summary),
                        icon = Icons.Outlined.RemoveModerator,
                        checked = preferences.disableSignature,
                        onCheckedChange = {
                            viewModel.updatePreference { disableSignature = it }
                        }
                    )
                    NoticeGroupDivider()
                    SwitchPreference(
                        title = stringResource(id = R.string.keep_history_message),
                        summary = stringResource(id = R.string.keep_history_message_summary),
                        icon = Icons.Outlined.FormatListBulleted,
                        checked = preferences.groupMessageById,
                        onCheckedChange = {
                            viewModel.updatePreference { groupMessageById = it }
                        }
                    )
                    NoticeGroupDivider()
                    SwitchPreference(
                        title = stringResource(id = R.string.keep_alive),
                        summary = stringResource(id = R.string.keep_alive_summary),
                        icon = Icons.Outlined.Computer,
                        checked = preferences.keepAlive,
                        onCheckedChange = {
                            viewModel.updatePreference { keepAlive = it }
                            viewModel.setHmsCoreForeground(it)
                        }
                    )
                }
                NoticeGroupCard {
                    SwitchPreference(
                        title = stringResource(id = R.string.notification_icon),
                        icon = Icons.Outlined.Palette,
                        checked = preferences.useCustomIcon,
                        onCheckedChange = {
                            viewModel.updatePreference { useCustomIcon = it }
                        }
                    )
                    if (preferences.useCustomIcon) {
                        NoticeGroupDivider()
                        SwitchPreference(
                            title = stringResource(id = R.string.tint_notification_icon),
                            summary = stringResource(id = R.string.tint_notification_icon_summary),
                            icon = Icons.Outlined.FormatColorFill,
                            checked = preferences.tintIconColor,
                            onCheckedChange = {
                                viewModel.updatePreference { tintIconColor = it }
                            }
                        )
                    }
                    NoticeGroupDivider()
                    SwitchPreference(
                        title = stringResource(id = R.string.hide_app_icon),
                        summary = stringResource(id = R.string.hide_app_icon_desc),
                        icon = Icons.Outlined.VisibilityOff,
                        checked = preferences.hideAppIcon,
                        showDivider = false,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showHideAppIconDialog = true
                            } else {
                                viewModel.toggleAppIcon(false)
                            }
                        },
                    )
                }

                // Ported NoticeFix rules: strategy order and global toggles.
                val noticeViewModel = rememberNoticeFixViewModel()
                NoticeSectionTitle(text = stringResource(id = R.string.notice_strategy_title))
                NoticeGroupCard {
                    val sortedFuncs = noticeViewModel.funcs.sortedBy { it.order }
                    sortedFuncs.forEachIndexed { index, status ->
                        if (index > 0) NoticeGroupDivider()
                        NoticeFuncRow(
                            status = status,
                            canMoveUp = index > 0,
                            canMoveDown = index < sortedFuncs.lastIndex,
                            onToggle = { noticeViewModel.setFuncActive(status.funcId, it) },
                            onMove = { noticeViewModel.moveFunc(status.funcId, it) },
                        )
                    }
                }
                NoticeSectionTitle(text = stringResource(id = R.string.notice_global_title))
                NoticeGroupCard {
                    NoticeSwitchRow(
                        title = stringResource(id = R.string.notice_always_handle),
                        summary = stringResource(id = R.string.notice_always_handle_summary),
                        checked = noticeViewModel.config.alwaysHandleProxyNotice,
                        onCheckedChange = { enabled ->
                            noticeViewModel.updateConfig { it.copy(alwaysHandleProxyNotice = enabled) }
                        },
                    )
                    NoticeGroupDivider()
                    NoticeSwitchRow(
                        title = stringResource(id = R.string.notice_skip_grayscale),
                        summary = stringResource(id = R.string.notice_skip_grayscale_summary),
                        checked = noticeViewModel.config.skipGrayscale,
                        onCheckedChange = { enabled ->
                            noticeViewModel.updateConfig { it.copy(skipGrayscale = enabled) }
                        },
                    )
                }
                Text(
                    text = stringResource(id = R.string.notice_scope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

        }
    }
}

@Composable
fun SwitchPreference(
    title: String, summary: String? = null, icon: ImageVector?, checked: Boolean,
    showDivider: Boolean = false,
    onCheckedChange: (checked: Boolean) -> Unit,
    onClick: () -> Unit = { onCheckedChange(!checked) }
) {
    Preference(
        title = title,
        summary = summary,
        icon = icon,
        showDivider = showDivider,
        action = {
            CheckSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
        onClick = onClick
    )
}

@Composable
fun Preference(title: String, summary: String? = null, icon: ImageVector?, showDivider: Boolean = false, action: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    // Material Design 3 list item: headline/supporting text follow the M3 type
    // scale, leading icon and trailing action use the standard slots.
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(text = title)
        },
        supportingContent = summary?.let {
            { Text(text = it) }
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                )
            }
        },
        trailingContent = action?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showDivider) {
                        VerticalDivider(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .height(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    it()
                }
            }
        }
    )
}

@Composable
fun HideAppIconConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.hide_app_icon)) },
        text = { Text(text = stringResource(id = R.string.hide_app_icon_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dialog_cancel))
            }
        }
    )
}

@Preview
@Composable
fun PreviewSettings() {
    SwitchPreference(
        "title", "summary",
        Icons.Filled.Android,
        checked = false,
        showDivider = true,
        onCheckedChange = {}
    )
}