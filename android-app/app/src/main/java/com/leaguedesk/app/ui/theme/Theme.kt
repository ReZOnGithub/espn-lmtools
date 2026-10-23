package com.leaguedesk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LD {
    val Bg = Color(0xFF070A0F)
    val BgSoft = Color(0xFF0A0F16)
    val Panel = Color(0xFF0F151E)
    val Panel2 = Color(0xFF121A26)
    val Glass = Color(0x0BFFFFFF)
    val GlassStrong = Color(0x14FFFFFF)
    val Line = Color(0x14FFFFFF)
    val LineStrong = Color(0x29FFFFFF)
    val Text = Color(0xFFEEF3FA)
    val Muted = Color(0xFF94A3B8)
    val Dim = Color(0xFF64748B)
    val Accent = Color(0xFF8B7CFF)
    val Accent2 = Color(0xFF22D3EE)
    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFFB7185)
    val White = Color(0xFFFFFFFF)
}

val LDTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.7).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.7.sp,
    ),
)

@Composable
fun LeagueDeskTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LD.Accent,
            onPrimary = LD.White,
            primaryContainer = LD.Accent.copy(alpha = 0.2f),
            onPrimaryContainer = LD.Text,
            secondary = LD.Accent2,
            onSecondary = LD.Bg,
            background = LD.Bg,
            onBackground = LD.Text,
            surface = LD.Panel,
            onSurface = LD.Text,
            surfaceVariant = LD.Panel2,
            onSurfaceVariant = LD.Muted,
            outline = LD.LineStrong,
            outlineVariant = LD.Line,
            error = LD.Bad,
            onError = LD.White,
        ),
        typography = LDTypography,
        content = content,
    )
}

fun hexColor(value: String?, fallback: Color = LD.Accent): Color {
    if (value.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
}
