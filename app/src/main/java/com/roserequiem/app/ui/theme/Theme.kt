package com.roserequiem.app.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

private val darkColorScheme = darkColorScheme(
    primary = InkDark80,
    onPrimary = InkDark20,
    primaryContainer = InkContainerDark,
    onPrimaryContainer = InkContainerLight,
    secondary = AmberDark80,
    onSecondary = AmberDark20,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainerLight,
    tertiary = PlumDark80,
    onTertiary = PlumDark20,
    tertiaryContainer = PlumContainerDark,
    onTertiaryContainer = PlumContainerLight,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

private val lightColorScheme = lightColorScheme(
    primary = InkLight30,
    onPrimary = Color.White,
    primaryContainer = InkContainerLight,
    onPrimaryContainer = InkLight10,
    secondary = AmberLight40,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberLight10,
    tertiary = PlumLight40,
    onTertiary = Color.White,
    tertiaryContainer = PlumContainerLight,
    onTertiaryContainer = PlumLight10,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

/** A softer, slightly more generous rounding scale than Material's default — reads warm, not sharp. */
private val RoseRequiemShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Custom Rose Requiem theme that applies the desired color scheme and system UI adjustments.
 *
 * @param darkTheme Whether to use dark theme based on system settings.
 * @param dynamicColor Whether to use dynamic color scheme (available on Android 12+).
 * @param content The content of the theme.
 */
@Composable
fun RoseRequiemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme)
                if (pureBlack)
                    dynamicDarkColorScheme(context).copy(
                        surface = Color.Black,
                        background = Color.Black,
                    )
                else
                    dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }

        pureBlack -> darkColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
        )

        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }
    // Replaces the old accompanist SystemUiController call (deprecated since Aug 2023,
    // Google's own recommended migration target is exactly this). Re-invoked on every
    // darkTheme change since enableEdgeToEdge() only reads it once per call -- MainActivity's
    // own bare enableEdgeToEdge() call in onCreate can't react to pureBlack/dynamicColor
    // settings changing at runtime, this one does.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme) {
            val activity = view.context as ComponentActivity
            val scrim = android.graphics.Color.TRANSPARENT
            val style = if (darkTheme) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
        shapes = RoseRequiemShapes,
    )
}
