package one.yufz.hmspush.app.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Brush

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation3.ui.NavDisplayTransitionEffects
import one.yufz.hmspush.R
import one.yufz.hmspush.app.fake.FakeDeviceScreen
import one.yufz.hmspush.app.home.HomeScreen
import one.yufz.hmspush.app.icon.IconScreen
import one.yufz.hmspush.app.noticefix.NoticeCustomScreen
import one.yufz.hmspush.app.settings.SettingsScreen

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No LocalNavigator provided") }

val appEntryProvider = entryProvider<NavKey> {
    entry<Router.Home> { HomeScreen() }
    entry<Router.Icon> { IconScreen() }
    entry<Router.NoticeCustom> { NoticeCustomScreen() }
    entry<Router.Settings> { SettingsScreen() }
    entry<Router.FakeDevice> { FakeDeviceScreen() }
}

private data class BottomTab(
    val route: Router,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomTabs = listOf(
    BottomTab(Router.Home, Icons.Filled.AppRegistration, R.string.nav_register),
    BottomTab(Router.FakeDevice, MasqueradeMaskIcon, R.string.nav_spoof),
    BottomTab(Router.NoticeCustom, Icons.Filled.Brush, R.string.nav_custom),
    BottomTab(Router.Settings, Icons.Filled.Settings, R.string.menu_settings),
)

@Composable
fun AppNavDisplay(
    modifier: Modifier = Modifier,
    startDestination: NavKey = Router.Home,
) {
    val navigationState = rememberNavigationState(
        startRoute = startDestination,
        topLevelRoutes = setOf(
            Router.Home,
            Router.FakeDevice,
            Router.NoticeCustom,
            Router.Settings,
        ),
    )
    val navigator = remember { Navigator(navigationState) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        // A plain Column instead of a Scaffold: the inner screens bring
        // their own Scaffold/top bar, and a wrapping Scaffold would add
        // the status-bar inset a second time (visible as a gap above
        // every top bar).
        Column(modifier = modifier.fillMaxSize()) {
            NavDisplay(
                modifier = Modifier.weight(1f),
                entries = navigationState.toEntries(appEntryProvider),
                onBack = { navigator.goBack() },
                // One plain crossfade for every switch. The library
                // defaults slide scenes in/out and clip the moving
                // scene with rounded corners (plus a dim on the scene
                // below); worse, swapping one top-level tab for
                // another can be read as a pop instead of a push, so
                // some tab switches animated in the opposite
                // direction. A single fade for forward, pop and
                // predictive pop keeps all tab switches identical,
                // and the effects below turn the corner clip and dim
                // off entirely.
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                popTransitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                predictivePopTransitionSpec = { _ ->
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                transitionEffects = NavDisplayTransitionEffects(
                    enableCornerClip = false,
                    dimAmount = 0f,
                    blockInputDuringTransition = true,
                    popDirectionFollowsSwipeEdge = false,
                ),
            )

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
        }
    }
}
