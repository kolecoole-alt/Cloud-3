package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SophisticatedDarkPrimary,
    onPrimary = SophisticatedDarkOnPrimary,
    primaryContainer = SophisticatedDarkPrimaryContainer,
    onPrimaryContainer = SophisticatedDarkOnPrimaryContainer,
    background = SophisticatedDarkBg,
    onBackground = SophisticatedDarkOnSurface,
    surface = SophisticatedDarkSurface,
    onSurface = SophisticatedDarkOnSurface,
    surfaceVariant = SophisticatedDarkSurfaceVariant,
    onSurfaceVariant = SophisticatedDarkOnSurfaceVariant,
    outline = SophisticatedDarkOutline,
    error = SophisticatedDarkError,
    onError = SophisticatedDarkOnError,
    errorContainer = SophisticatedDarkErrorContainer,
    onErrorContainer = SophisticatedDarkOnErrorContainer
  )

private val LightColorScheme = DarkColorScheme // Keep it consistently dark for the Sophisticated Dark look


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Consistently choose dark theme
  dynamicColor: Boolean = false, // Disable system dynamics to preserve design
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme


  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
