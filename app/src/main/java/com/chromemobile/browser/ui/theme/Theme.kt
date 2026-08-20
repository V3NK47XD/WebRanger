package com.chromemobile.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme = darkColorScheme(
    primary = BlueishGreen,
    secondary = HotPink,
    tertiary = BlueishGreenLight,
    background = AmoledBlack,
    surface = AmoledSurface,
    onPrimary = AmoledBlack,
    onSecondary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

@Composable
fun ChromeMobileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        content = content
    )
}
