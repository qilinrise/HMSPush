@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalComposeUiApi::class,
)

package one.yufz.hmspush.app.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.compose.collectAsState
import one.yufz.hmspush.R
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.app.nav.LocalNavigator
import one.yufz.hmspush.app.nav.Router
import one.yufz.hmspush.app.widget.LifecycleAware
import one.yufz.hmspush.app.widget.PillSearchField
import one.yufz.hmspush.app.workaround.mavericksViewModel
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.common.HmsCoreUtil

@Composable
fun HomeScreen(homeViewModel: HomeViewModel = mavericksViewModel()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state by homeViewModel.collectAsState()

    Scaffold(
        topBar = {
            AppBar(scrollBehavior)
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
            LifecycleAware(onResume = { homeViewModel.checkHmsCore() }) {
                if (state.usable) {
                    // Drop-down style search: a permanent centered pill
                    // below the top bar.
                    PillSearchField(
                        value = state.searchText,
                        onValueChange = { homeViewModel.setSearchText(it) },
                        placeholderText = stringResource(id = R.string.menu_search),
                    )
                    AppListScreen(state.searchText)
                } else if (state.reason == HomeViewModel.Reason.Checking) {
                    Loading()
                } else {
                    HmsCoreTips(state.tips, state.reason)
                }
            }
        }
    }
}

@Composable
private fun AppBar(
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(text = stringResource(id = R.string.app_name))
        },
        scrollBehavior = scrollBehavior,
        actions = {
            // Mirror the title's inset: the title text starts 16 dp
            // from the left edge, so this button gets 4 dp of end
            // padding, which puts the icon's right edge 16 dp from
            // the right edge (48 dp button, icon centered).
            Box(modifier = Modifier.padding(end = 4.dp)) {
                AppBarMoreMenu()
            }
        }
    )
}

@Composable
private fun AppBarMoreMenu() {
    //More
    var openMoreMenu by remember { mutableStateOf(false) }
    var showRestartSystemUiDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showRestartSystemUiDialog) {
        AlertDialog(
            onDismissRequest = { showRestartSystemUiDialog = false },
            title = { Text(text = stringResource(id = R.string.notice_restart_systemui)) },
            text = { Text(text = stringResource(id = R.string.notice_restart_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartSystemUiDialog = false
                        scope.launch {
                            val sent = withContext(Dispatchers.IO) {
                                one.yufz.hmspush.app.noticefix.RootActions.restartSystemUI()
                            }
                            Toast.makeText(
                                context,
                                if (sent) R.string.notice_restart_sent else R.string.notice_restart_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartSystemUiDialog = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    IconButton(onClick = { openMoreMenu = true }) {
        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More")
        if (openMoreMenu) {
            DropdownMenu(
                expanded = openMoreMenu,
                onDismissRequest = { openMoreMenu = false },
                modifier = Modifier.requiredWidth(160.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                val navigator = LocalNavigator.current
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.fake_device))
                    },
                    onClick = {
                        navigator.navigate(Router.FakeDevice)
                        openMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.open_hms_core_app_info))
                    },
                    onClick = {
                        Util.launchAppInfo(context, HMS_PACKAGE_NAME)
                        openMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.reboot_hms_core))
                    },
                    onClick = {
                        rebootHmsCore(context)
                        openMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(id = R.string.notice_restart_systemui))
                    },
                    onClick = {
                        openMoreMenu = false
                        showRestartSystemUiDialog = true
                    }
                )
            }
        }
    }
}


private fun rebootHmsCore(context: Context) {
    if (HmsPushClient.killHmsCore()) {
        Toast.makeText(context, R.string.rebooting_hms_core, Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, R.string.manually_reboot_hms_core, Toast.LENGTH_SHORT).show()
        Util.launchAppInfo(context, HMS_PACKAGE_NAME)
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun HmsCoreTips(tips: String, reason: HomeViewModel.Reason) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(all = 16.dp)
                .fillMaxWidth()
                .requiredHeight(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            onClick = {
                onTipsClick(context, reason)
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(text = tips, Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun onTipsClick(context: Context, reason: HomeViewModel.Reason) {
    when (reason) {
        HomeViewModel.Reason.HmsCoreNotActivated -> HmsCoreUtil.startHmsCoreDummyActivity(context)
        HomeViewModel.Reason.HmsPushVersionNotMatch -> rebootHmsCore(context)
        else -> {}
    }
}