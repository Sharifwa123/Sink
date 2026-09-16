package com.sharif.sink.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SinkSignalDark,
    secondary = SinkSlate,
    tertiary = SinkSignal,
    error = SinkError,
    background = SinkInkLight,
    surface = SinkInkLight,
)

private val DarkColors = darkColorScheme(
    primary = SinkSignal,
    secondary = SinkSlate,
    tertiary = SinkSignal,
    error = SinkError,
    background = SinkInk,
    surface = SinkInk,
)

@Composable
fun SinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // off by default: Sink has a deliberate brand palette
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SinkTypography,
        content = content,
    )
}
