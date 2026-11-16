package one.yufz.hmspush.app.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A masquerade (domino) eye mask, drawn as a vector so the bottom
 * bar can tint it like any Material icon. The eye holes are cut
 * out with the even-odd fill rule.
 */
val MasqueradeMaskIcon: ImageVector by lazy { buildMasqueradeMaskIcon() }

private fun buildMasqueradeMaskIcon(): ImageVector =
    ImageVector.Builder(
        name = "MasqueradeMask",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            // Mask body: wide band, dipping at the nose bridge on
            // top and notched for the nose at the bottom centre.
            moveTo(1.8f, 10.2f)
            curveTo(2.2f, 7.4f, 5.2f, 5.6f, 8.6f, 5.9f)
            curveTo(10.4f, 6.1f, 11.2f, 6.9f, 12f, 6.9f)
            curveTo(12.8f, 6.9f, 13.6f, 6.1f, 15.4f, 5.9f)
            curveTo(18.8f, 5.6f, 21.8f, 7.4f, 22.2f, 10.2f)
            curveTo(22.4f, 13.2f, 20.4f, 15.9f, 17.2f, 16.9f)
            curveTo(15.2f, 17.5f, 13.4f, 17.1f, 12f, 15.9f)
            curveTo(10.6f, 17.1f, 8.8f, 17.5f, 6.8f, 16.9f)
            curveTo(3.6f, 15.9f, 1.6f, 13.2f, 1.8f, 10.2f)
            close()
            // Left eye hole.
            moveTo(5.0f, 11f)
            arcTo(2.3f, 1.45f, 0f, false, true, 9.6f, 11f)
            arcTo(2.3f, 1.45f, 0f, false, true, 5.0f, 11f)
            close()
            // Right eye hole.
            moveTo(14.4f, 11f)
            arcTo(2.3f, 1.45f, 0f, false, true, 19.0f, 11f)
            arcTo(2.3f, 1.45f, 0f, false, true, 14.4f, 11f)
            close()
        }
    }.build()
