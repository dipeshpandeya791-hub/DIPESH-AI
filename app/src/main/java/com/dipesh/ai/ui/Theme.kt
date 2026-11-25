package com.dipesh.ai.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Immutable
data class DipeshColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val glass: Color,
    val glassBorderHi: Color,
    val glassBorderLo: Color,
    val violet: Color,
    val blue: Color,
    val cyan: Color,
    val text: Color,
    val textMuted: Color,
    val danger: Color,
    val success: Color
)

val DarkColors = DipeshColors(
    isDark = true,
    bgTop = Color(0xFF03040A),
    bgBottom = Color(0xFF0B1238),
    glass = Color(0x16FFFFFF),
    glassBorderHi = Color(0x44FFFFFF),
    glassBorderLo = Color(0x0FFFFFFF),
    violet = Color(0xFF8B5CF6),
    blue = Color(0xFF4F7CFF),
    cyan = Color(0xFF22D3EE),
    text = Color(0xFFEAF0FF),
    textMuted = Color(0xFF8C97BD),
    danger = Color(0xFFFF6B7A),
    success = Color(0xFF34D399)
)

val LightColors = DipeshColors(
    isDark = false,
    bgTop = Color(0xFFF6F7FF),
    bgBottom = Color(0xFFDDE3FF),
    glass = Color(0xB3FFFFFF),
    glassBorderHi = Color(0xFFFFFFFF),
    glassBorderLo = Color(0x338B5CF6),
    violet = Color(0xFF7C3AED),
    blue = Color(0xFF3B63E8),
    cyan = Color(0xFF0891B2),
    text = Color(0xFF10163A),
    textMuted = Color(0xFF5B6590),
    danger = Color(0xFFD6263B),
    success = Color(0xFF059669)
)

val LocalDipeshColors = staticCompositionLocalOf { DarkColors }

private val Sans = FontFamily.SansSerif

private val DipeshTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)
)

@Composable
fun DipeshTheme(themeSetting: String, content: @Composable () -> Unit) {
    val dark = when (themeSetting) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.violet, onPrimary = Color.White, secondary = c.blue, tertiary = c.cyan,
            background = c.bgTop, onBackground = c.text, surface = c.bgBottom, onSurface = c.text,
            surfaceVariant = Color(0xFF151B3D), onSurfaceVariant = c.textMuted, error = c.danger,
            outline = c.glassBorderHi
        )
    } else {
        lightColorScheme(
            primary = c.violet, onPrimary = Color.White, secondary = c.blue, tertiary = c.cyan,
            background = c.bgTop, onBackground = c.text, surface = Color.White, onSurface = c.text,
            surfaceVariant = Color(0xFFE6EAFF), onSurfaceVariant = c.textMuted, error = c.danger,
            outline = c.glassBorderLo
        )
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDipeshColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = DipeshTypography, content = content)
    }
}

/** Full-screen deep gradient with soft violet and blue glows. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalDipeshColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.violet.copy(alpha = if (c.isDark) 0.30f else 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.06f),
                        radius = size.width * 0.85f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c.blue.copy(alpha = if (c.isDark) 0.22f else 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.85f),
                        radius = size.width * 0.9f
                    )
                )
            }
    ) { content() }
}

/** Frosted-glass surface: translucent fill, bright top-left edge, soft coloured shadow. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), c: DipeshColors, elevation: Int = 12): Modifier =
    this
        .shadow(
            elevation.dp, shape, clip = false,
            ambientColor = c.violet.copy(alpha = 0.25f), spotColor = c.blue.copy(alpha = 0.35f)
        )
        .clip(shape)
        .background(c.glass)
        .border(1.dp, Brush.linearGradient(listOf(c.glassBorderHi, c.glassBorderLo)), shape)
