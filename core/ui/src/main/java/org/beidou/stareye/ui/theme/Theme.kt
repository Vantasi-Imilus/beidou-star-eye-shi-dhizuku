package org.beidou.stareye.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StarColorScheme = darkColorScheme(
    primary = StarBlue,
    onPrimary = DeepSpace,
    secondary = StarGold,
    onSecondary = DeepSpace,
    tertiary = NebulaPurple,
    background = DeepSpace,
    onBackground = StarWhite,
    surface = DeepSpaceLight,
    onSurface = StarWhite,
)

/**
 * 北斗·群星之眼 深空主题。
 */
@Composable
fun BeidouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StarColorScheme,
        content = content,
    )
}
