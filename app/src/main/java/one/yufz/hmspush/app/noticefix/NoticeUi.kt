package one.yufz.hmspush.app.noticefix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Shared building blocks for the NoticeFix screens, following the grouped
 * card style Sun picked: large-radius cards grouping related rows, thin
 * M3-style dividers between rows, and switches with a check/close thumb.
 */

@Composable
fun NoticeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun NoticeGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
fun NoticeGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** M3 switch with a check/close icon inside the thumb, as in the reference UI. */
@Composable
fun CheckSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

@Composable
fun NoticeSwitchRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(text = it) } },
        leadingContent = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingContent = {
            CheckSwitch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/** One strategy row of the icon rules: name, description, reorder buttons, switch. */
@Composable
fun NoticeFuncRow(
    status: one.yufz.hmspush.common.noticefix.NoticeFuncStatus,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val (titleRes, summaryRes) = when (
        one.yufz.hmspush.common.noticefix.NoticeIconFunc.fromId(status.funcId)
    ) {
        one.yufz.hmspush.common.noticefix.NoticeIconFunc.CUSTOM ->
            one.yufz.hmspush.R.string.notice_func_custom to
                one.yufz.hmspush.R.string.notice_func_custom_summary
        one.yufz.hmspush.common.noticefix.NoticeIconFunc.LIB ->
            one.yufz.hmspush.R.string.notice_func_lib to
                one.yufz.hmspush.R.string.notice_func_lib_summary
        one.yufz.hmspush.common.noticefix.NoticeIconFunc.AUTO ->
            one.yufz.hmspush.R.string.notice_func_auto to
                one.yufz.hmspush.R.string.notice_func_auto_summary
        null ->
            one.yufz.hmspush.R.string.notice_func_custom to
                one.yufz.hmspush.R.string.notice_func_custom_summary
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(text = androidx.compose.ui.res.stringResource(id = titleRes))
        },
        supportingContent = {
            Text(text = androidx.compose.ui.res.stringResource(id = summaryRes))
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(
                    onClick = { onMove(-1) },
                    enabled = canMoveUp,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { onMove(1) },
                    enabled = canMoveDown,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                CheckSwitch(checked = status.active, onCheckedChange = onToggle)
            }
        },
    )
}

@Composable
fun NoticeActionRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(text = it) } },
        leadingContent = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingContent = value?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
