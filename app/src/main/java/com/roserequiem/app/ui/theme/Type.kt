package com.roserequiem.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.roserequiem.app.R

/**
 * Rose Requiem's type system, built on three bundled variable fonts (see licenses/fonts/):
 *
 *  - Display (Fraunces): reserved for the rare, big moments — onboarding, empty states,
 *    the About screen. A serif carries a little literary weight, which fits lyrics
 *    without demanding attention on every single screen.
 *  - UI (Manrope): everything else — titles, body copy, labels. Clean and fast to scan,
 *    since most of this app is dense lists and settings, not display copy.
 *  - Mono (JetBrains Mono): reserved for [RoseRequiemMonoTextStyle] — timestamps and other
 *    technical figures only. A nod to the raw `[mm:ss.xx]` syntax this app processes.
 *
 * Variable fonts keep this to three files total instead of one per weight.
 */

@OptIn(ExperimentalTextApi::class)
private fun frauncesAt(weight: Int, opticalSize: Float) = FontFamily(
    Font(
        resId = R.font.fraunces,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.opticalSizing(opticalSize.sp),
        )
    )
)

@OptIn(ExperimentalTextApi::class)
private fun manropeAt(weight: Int) = FontFamily(
    Font(
        resId = R.font.manrope,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )
)

val DisplayFontFamily = frauncesAt(weight = 500, opticalSize = 72f)
val TitleFontFamily = manropeAt(weight = 700)
val BodyFontFamily = manropeAt(weight = 400)
val LabelFontFamily = manropeAt(weight = 600)

@OptIn(ExperimentalTextApi::class)
val RoseRequiemMonoFontFamily = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    )
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
)

/** For timestamps and technical figures — `[mm:ss.xx]` LRC offsets, version numbers, counts. */
val RoseRequiemMonoTextStyle = TextStyle(
    fontFamily = RoseRequiemMonoFontFamily,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp,
)
