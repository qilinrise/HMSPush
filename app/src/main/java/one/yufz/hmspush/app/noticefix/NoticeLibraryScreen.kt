package one.yufz.hmspush.app.noticefix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.yufz.hmspush.R
import one.yufz.hmspush.common.noticefix.NoticeLibIcon

/**
 * Icon library page: browse and search every library icon (NoticeFix's
 * IconLibActivity). Library entries apply automatically by package name
 * when the library strategy runs; refresh and import live on the
 * strategy page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeLibraryScreen(viewModel: NoticeFixViewModel = rememberNoticeFixViewModel()) {
    var query by remember { mutableStateOf("") }
    val entries = remember(viewModel.library, query) {
        viewModel.library.values
            .filter {
                query.isBlank() ||
                    it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.appName.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.nav_library) +
                            " (" + viewModel.library.size + ")",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = stringResource(id = R.string.notice_search_hint)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.notice_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.packageName }) { entry ->
                        LibraryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: NoticeLibIcon) {
    val bitmap = remember(entry.iconBitmap) { entry.decodeBitmap() }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = entry.appName.ifEmpty { entry.packageName }) },
        supportingContent = {
            Column {
                Text(text = entry.packageName)
                entry.contributorName?.takeIf { it.isNotEmpty() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        },
    )
}
