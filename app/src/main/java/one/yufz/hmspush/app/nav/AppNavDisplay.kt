package one.yufz.hmspush.app.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import one.yufz.hmspush.R
import one.yufz.hmspush.app.fake.FakeDeviceScreen
import one.yufz.hmspush.app.home.HomeScreen
import one.yufz.hmspush.app.icon.IconScreen
import one.yufz.hmspush.app.noticefix.NoticeCustomScreen
import one.yufz.hmspush.app.noticefix.NoticeHomeScreen
import one.yufz.hmspush.app.noticefix.NoticeLibraryScreen
import one.yufz.hmspush.app.settings.SettingsScreen

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No LocalNavigator provided") }

val appEntryProvider = entryProvider<NavKey> {
    entry<Router.Home> { HomeScreen() }
    entry<Router.Settings> { SettingsScreen() }
    entry<Router.Icon> { IconScreen() }
    entry<Router.FakeDevice> { FakeDeviceScreen() }
    entry<Router.NoticeHome> { NoticeHomeScreen() }
    entry<Router.NoticeLibrary> { NoticeLibraryScreen() }
    entry<Router.NoticeCustom> { NoticeCustomScreen() }
}

private data class BottomTab(
    val route: Router,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomTabs = listOf(
    BottomTab(Router.Home, Icons.Filled.Home, R.string.nav_home),
    BottomTab(Router.NoticeHome, Icons.Filled.Tune, R.string.nav_notice),
    BottomTab(Router.NoticeLibrary, Icons.Filled.PhotoLibrary, R.string.nav_library),
    BottomTab(Router.NoticeCustom, Icons.Filled.Brush, R.string.nav_custom),
    BottomTab(Router.Settings, Icons.Filled.Settings, R.string.menu_settings),
)

@Composable
fun AppNavDisplay(
    modifier: Modifier = Modifier,
    startDestination: NavKey = Router.Home
) {
    val navigationState = rememberNavigationState(
        startRoute = startDestination,
        topLevelRoutes = setOf(
            Router.Home,
            Router.NoticeHome,
            Router.NoticeLibrary,
            Router.NoticeCustom,
            Router.Settings,
        )
    )
    val navigator = remember { Navigator(navigationState) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = navigationState.topLevelRoute == tab.route,
                            onClick = { navigator.navigate(tab.route) },
                            icon = {
                                Icon(imageVector = tab.icon, contentDescription = null)
                            },
                            label = {
                                Text(text = stringResource(id = tab.labelRes))
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavDisplay(
                modifier = Modifier.padding(innerPadding),
                entries = navigationState.toEntries(appEntryProvider),
                onBack = { navigator.goBack() },
            )
        }
    }
}
