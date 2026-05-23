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

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),        // Vibrant neon purple
    onPrimary = Color(0xFF381E72),      // Deep contrast purple
    secondary = Color(0xFF1C1B1F),    // Dark chassis background/surface
    onSecondary = Color(0xFFE6E1E5),  // Sleek foreground off-white
    tertiary = Color(0xFF49454F),     // Cool grey border colors
    background = Color(0xFF0F0D13),   // Obsidian/dark purple deep canvas
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930), // Overlay backdrops
    onSurfaceVariant = Color(0xFFD0BCFF)
  )

private val LightColorScheme = DarkColorScheme // Force dark elegant fantasy theme throughout the app

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false, // Disable to respect the handcrafted game console art theme!
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
