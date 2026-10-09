package com.klikacz.clicklog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(primary = Color(0xFFB71C1C), secondary = Color(0xFF5D4037))
private val DarkColors = darkColorScheme(primary = Color(0xFFEF9A9A), secondary = Color(0xFFBCAAA4))

@Composable
fun ClickLogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // minSdk 31: dynamic color is always available; static schemes remain as a fallback.
    val scheme = runCatching { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
        .getOrElse { if (dark) DarkColors else LightColors }
    MaterialTheme(colorScheme = scheme, content = content)
}
