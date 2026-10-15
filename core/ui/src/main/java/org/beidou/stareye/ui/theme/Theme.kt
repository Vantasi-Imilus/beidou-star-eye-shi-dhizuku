package org.beidou.stareye.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun schemeOf(p: BeidouPalette): ColorScheme = darkColorScheme(
    primary = p.starBlue,
    onPrimary = p.deepSpace,
    secondary = p.starGold,
    onSecondary = p.deepSpace,
    tertiary = p.nebulaPurple,
    background = p.deepSpace,
    onBackground = p.starWhite,
    surface = p.deepSpaceLight,
    onSurface = p.starWhite,
)

private val StarColorScheme = schemeOf(NormalPalette)
private val ReversedColorScheme = schemeOf(ReversedPalette)

/**
 * 北斗·群星之眼 深空主题。
 *
 * @param reversed 反向星界模式：全局调色板反色 + 边框圆角收为直角。
 */
@Composable
fun BeidouTheme(reversed: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBeidouPalette provides if (reversed) ReversedPalette else NormalPalette,
        LocalSharpCorners provides reversed,
    ) {
        MaterialTheme(
            colorScheme = if (reversed) ReversedColorScheme else StarColorScheme,
            content = content,
        )
    }
}
