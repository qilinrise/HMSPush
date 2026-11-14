package one.yufz.hmspush.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import one.yufz.hmspush.app.nav.AppNavDisplay
import one.yufz.hmspush.app.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: content draws behind the system bars, which the
        // Material3 DayNight theme keeps transparent.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavDisplay()
            }
        }
    }
}

val mainActivityAlias = "${MainActivity::class.java.name}Alias"
